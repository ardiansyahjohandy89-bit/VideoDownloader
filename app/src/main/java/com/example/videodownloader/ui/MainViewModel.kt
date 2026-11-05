package com.example.videodownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.data.AppSettings
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.QualityPreset
import com.example.videodownloader.data.SettingsStore
import com.example.videodownloader.data.VideoMeta
import com.example.videodownloader.download.DownloadEngine
import com.example.videodownloader.download.YtDlpRuntime
import com.example.videodownloader.download.extractUrl
import com.example.videodownloader.download.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val url: String = "",
    val loading: Boolean = false,
    val meta: VideoMeta? = null,
    val error: String? = null,
    val preset: QualityPreset = QualityPreset.BEST,
)

data class UiEvent(val message: String, val actionLabel: String? = null, val goToDownloads: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _home = MutableStateFlow(HomeState(preset = SettingsStore.current.defaultPreset))
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val downloads = DownloadEngine.items
    val settings = SettingsStore.state
    val runtime = YtDlpRuntime.state
    val ytVersion = YtDlpRuntime.version
    val updating = YtDlpRuntime.updating

    private var fetchJob: Job? = null

    fun setTab(index: Int) {
        _tab.value = index
    }

    // ------------------------------------------------------------- Beranda

    fun onUrlChange(text: String) {
        _home.update { it.copy(url = text, meta = null, error = null) }
    }

    fun paste(text: String) = onUrlChange(extractUrl(text) ?: text.trim())

    fun selectPreset(preset: QualityPreset) {
        _home.update { it.copy(preset = preset) }
    }

    fun fetchInfo() {
        val url = extractUrl(_home.value.url)
        if (url == null) {
            _home.update { it.copy(error = "Masukkan tautan yang valid (diawali http:// atau https://).") }
            return
        }
        fetchJob?.cancel()
        _home.update { it.copy(url = url, loading = true, meta = null, error = null) }
        fetchJob = viewModelScope.launch {
            try {
                YtDlpRuntime.awaitReady()
                val meta = YtDlpRuntime.fetchMeta(url)
                _home.update { it.copy(loading = false, meta = meta) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _home.update { it.copy(loading = false, error = friendlyError(e)) }
            }
        }
    }

    fun download() {
        val state = _home.value
        val url = extractUrl(state.url)
        if (url == null) {
            _home.update { it.copy(error = "Masukkan tautan yang valid (diawali http:// atau https://).") }
            return
        }
        DownloadEngine.enqueue(url, state.meta?.takeIf { it.url == url }, state.preset)
        _home.update { HomeState(preset = it.preset) }
        _events.tryEmit(UiEvent("Ditambahkan ke antrean unduhan", "Lihat", goToDownloads = true))
    }

    /** Dipanggil saat pengguna membagikan tautan ke aplikasi ini dari aplikasi lain. */
    fun onShared(text: String) {
        val url = extractUrl(text) ?: return
        val s = SettingsStore.current
        if (s.quickShare) {
            DownloadEngine.enqueue(url, null, s.defaultPreset)
            _tab.value = 1
            _events.tryEmit(UiEvent("Unduhan dimulai"))
        } else {
            _tab.value = 0
            _home.value = HomeState(url = url, preset = s.defaultPreset)
            fetchInfo()
        }
    }

    // ------------------------------------------------------------- Unduhan

    fun cancel(id: String) = DownloadEngine.cancel(id)
    fun retry(id: String) = DownloadEngine.retry(id)
    fun remove(item: DownloadItem, deleteFile: Boolean) = DownloadEngine.remove(item.id, deleteFile)
    fun clearFinished() = DownloadEngine.clearFinished()
    fun verifyFiles() = DownloadEngine.verifyFiles()

    // ------------------------------------------------------------- Pengaturan

    fun updateSettings(transform: (AppSettings) -> AppSettings) = SettingsStore.update(transform)

    fun updateYtDlp() {
        viewModelScope.launch {
            val message = YtDlpRuntime.update(getApplication<Application>())
            _events.tryEmit(UiEvent(message))
        }
    }
}
