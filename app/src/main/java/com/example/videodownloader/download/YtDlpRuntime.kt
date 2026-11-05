package com.example.videodownloader.download

import android.app.Application
import android.content.Context
import com.example.videodownloader.data.SettingsStore
import com.example.videodownloader.data.VideoMeta
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RuntimeState {
    data object Initializing : RuntimeState
    data object Ready : RuntimeState
    data class Failed(val message: String) : RuntimeState
}

/** Membungkus yt-dlp + FFmpeg: inisialisasi, ambil info video, dan pembaruan. */
object YtDlpRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Initializing)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Dipanggil sekali dari Application.onCreate. Berjalan di latar belakang. */
    fun start(app: Application) {
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _version.value = YoutubeDL.getInstance().version(app)
                _state.value = RuntimeState.Ready
            } catch (e: Exception) {
                _state.value = RuntimeState.Failed(e.message ?: e.toString())
                return@launch
            }
            maybeAutoUpdate(app)
        }
    }

    suspend fun awaitReady() {
        state.first { it !is RuntimeState.Initializing }
        val s = state.value
        if (s is RuntimeState.Failed) {
            throw IllegalStateException("Mesin unduhan gagal dimulai: ${s.message}")
        }
    }

    /** Mengambil judul, thumbnail, dll. tanpa mengunduh. */
    suspend fun fetchMeta(url: String): VideoMeta = withContext(Dispatchers.IO) {
        val info = YoutubeDL.getInstance().getInfo(url)
        VideoMeta(
            url = url,
            title = info.title.orEmpty(),
            thumbnail = info.thumbnail,
            uploader = info.uploader,
            durationSec = info.duration?.toLong() ?: 0L,
        )
    }

    /** Memperbarui yt-dlp. Mengembalikan pesan untuk ditampilkan ke pengguna. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        if (_updating.value) return@withContext "Pembaruan sedang berjalan."
        _updating.value = true
        try {
            awaitReady()
            val channel = if (SettingsStore.current.nightlyChannel) {
                YoutubeDL.UpdateChannel.NIGHTLY
            } else {
                YoutubeDL.UpdateChannel.STABLE
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
            _version.value = YoutubeDL.getInstance().version(context)
            SettingsStore.lastUpdateCheck = System.currentTimeMillis()
            when (status?.toString()) {
                "DONE" -> "yt-dlp berhasil diperbarui."
                "ALREADY_UP_TO_DATE" -> "yt-dlp sudah versi terbaru."
                else -> "Pemeriksaan pembaruan selesai."
            }
        } catch (e: Exception) {
            "Gagal memperbarui: ${friendlyError(e)}"
        } finally {
            _updating.value = false
        }
    }

    private suspend fun maybeAutoUpdate(app: Application) {
        val twelveHours = 12L * 60 * 60 * 1000
        if (SettingsStore.current.autoUpdate &&
            System.currentTimeMillis() - SettingsStore.lastUpdateCheck > twelveHours
        ) {
            update(app)
        }
    }
}
