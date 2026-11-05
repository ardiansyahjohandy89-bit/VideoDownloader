package com.example.videodownloader.download

import android.app.Application
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.HistoryStore
import com.example.videodownloader.data.QualityPreset
import com.example.videodownloader.data.SettingsStore
import com.example.videodownloader.data.Status
import com.example.videodownloader.data.VideoMeta
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Pusat semua unduhan: antrean, progres, pembatalan, dan riwayat.
 * Hidup selama proses aplikasi hidup; DownloadService menjaga proses tetap berjalan di latar belakang.
 */
object DownloadEngine {
    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val canceled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile private var semaphore = Semaphore(2)
    @Volatile private var permits = 2

    private val skipExt = setOf(
        "part", "ytdl", "vtt", "srt", "ass", "json", "jpg", "jpeg", "png", "webp", "temp", "tmp"
    )

    fun init(application: Application) {
        app = application
        _items.value = HistoryStore.load().map {
            if (it.isActive) {
                it.copy(status = Status.FAILED, error = "Terhenti karena aplikasi ditutup.", etaSec = -1)
            } else it
        }
        persist()
    }

    // ---------------------------------------------------------------- API publik

    fun enqueue(url: String, meta: VideoMeta?, preset: QualityPreset): String {
        val id = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = id,
            url = url,
            title = meta?.title.orEmpty(),
            thumbnail = meta?.thumbnail,
            uploader = meta?.uploader,
            durationSec = meta?.durationSec ?: 0L,
            preset = preset,
        )
        _items.update { listOf(item) + it }
        persist()
        start(id)
        return id
    }

    fun retry(id: String) {
        val item = find(id) ?: return
        if (item.isActive) return
        canceled.remove(id)
        mutate(id) {
            copy(status = Status.QUEUED, progress = 0f, etaSec = -1, error = null, fileMissing = false)
        }
        persist()
        start(id)
    }

    fun cancel(id: String) {
        val item = find(id) ?: return
        if (item.status != Status.QUEUED && item.status != Status.DOWNLOADING) return
        canceled.add(id)
        if (item.status == Status.DOWNLOADING) {
            scope.launch { runCatching { YoutubeDL.getInstance().destroyProcessById(id) } }
        } else {
            jobs[id]?.cancel()
        }
    }

    fun cancelAll() {
        _items.value.filter { it.isActive }.forEach { cancel(it.id) }
    }

    fun remove(id: String, deleteFile: Boolean) {
        val item = find(id) ?: return
        if (item.isActive) cancel(id)
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
        if (deleteFile) {
            item.outputUri?.let { uri -> scope.launch { MediaSaver.delete(app, uri) } }
        }
    }

    /** Menghapus semua item selesai/gagal/dibatalkan dari riwayat (berkas video tidak dihapus). */
    fun clearFinished() {
        _items.update { list -> list.filter { it.isActive } }
        persist()
    }

    /** Menandai item yang berkasnya sudah tidak ada (dihapus lewat galeri, dsb). */
    fun verifyFiles() {
        scope.launch {
            val missing = _items.value
                .filter { it.status == Status.COMPLETED && it.outputUri != null }
                .associate { it.id to !MediaSaver.exists(app, it.outputUri!!) }
            _items.update { list ->
                list.map { item -> missing[item.id]?.let { item.copy(fileMissing = it) } ?: item }
            }
        }
    }

    // ---------------------------------------------------------------- internal

    private fun find(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    private fun persist() = HistoryStore.save(_items.value)

    /** Mengubah satu item. Riwayat disimpan ke disk hanya jika status berubah (bukan tiap progres). */
    private fun mutate(id: String, transform: DownloadItem.() -> DownloadItem) {
        val before = find(id)?.status
        _items.update { list -> list.map { if (it.id == id) it.transform() else it } }
        if (before != find(id)?.status) persist()
    }

    private fun start(id: String) {
        if (jobs.isEmpty()) {
            val n = SettingsStore.current.maxConcurrent
            if (n != permits) {
                semaphore = Semaphore(n)
                permits = n
            }
        }
        DownloadService.start(app)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                runDownload(id)
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
        job.start()
    }

    private suspend fun runDownload(id: String) {
        val tmp = File(app.getExternalFilesDir(null) ?: app.cacheDir, "tmp/$id")
        try {
            semaphore.withPermit { download(id, tmp) }
        } catch (e: CancellationException) {
            markCanceled(id)
            throw e
        } catch (e: Exception) {
            if (canceled.contains(id)) markCanceled(id) else fail(id, e)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun throwIfCanceled(id: String) {
        if (canceled.contains(id)) throw CancellationException("Dibatalkan")
    }

    private suspend fun download(id: String, tmp: File) {
        val item = find(id) ?: return
        throwIfCanceled(id)
        mutate(id) { copy(status = Status.DOWNLOADING, progress = 0f, etaSec = -1, error = null) }

        val settings = SettingsStore.current
        YtDlpRuntime.awaitReady()
        if (settings.wifiOnly && !isUnmeteredNetwork(app)) {
            throw IllegalStateException("Mode \"Hanya Wi-Fi\" aktif, tetapi perangkat tidak tersambung ke Wi-Fi.")
        }

        // Lengkapi info (judul/thumbnail) bila belum ada, misalnya saat unduh langsung dari menu Bagikan.
        if (item.title.isBlank()) {
            runCatching { YtDlpRuntime.fetchMeta(item.url) }.getOrNull()?.let { meta ->
                mutate(id) {
                    copy(title = meta.title, thumbnail = meta.thumbnail, uploader = meta.uploader, durationSec = meta.durationSec)
                }
                persist()
            }
        }
        throwIfCanceled(id)

        tmp.mkdirs()
        val preset = item.preset
        val request = YoutubeDLRequest(item.url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--socket-timeout", "30")
            addOption("--concurrent-fragments", settings.fragments.toString())
            addOption("-o", File(tmp, "$id.%(ext)s").absolutePath)
            if (preset.isAudio) {
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", preset.ext)
                addOption("--audio-quality", "0")
            } else {
                addOption("-f", preset.formatSelector())
                addOption("--merge-output-format", "mp4")
                if (settings.embedSubs) {
                    addOption("--embed-subs")
                    addOption("--sub-langs", "id,en")
                }
            }
            addOption("--embed-metadata")
        }

        var lastProgress = -1f
        YoutubeDL.getInstance().execute(request, id) { progress, eta, _ ->
            val p = progress.coerceIn(0f, 100f)
            if (abs(p - lastProgress) >= 0.5f) {
                lastProgress = p
                mutate(id) { copy(progress = p, etaSec = eta) }
            }
        }
        throwIfCanceled(id)

        // Menyimpan ke Download / Movies / Music
        mutate(id) { copy(status = Status.PROCESSING, progress = 100f, etaSec = 0) }
        val file = pickOutput(tmp) ?: throw IllegalStateException("Berkas hasil unduhan tidak ditemukan.")
        val title = find(id)?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val name = sanitizeFileName(title) + "." + file.extension
        val mime = mimeFor(file.extension, preset.isAudio)
        val size = file.length()
        val uri = MediaSaver.save(app, file, name, mime, settings.saveLocation)

        mutate(id) {
            copy(
                status = Status.COMPLETED, progress = 100f, etaSec = 0, error = null,
                outputUri = uri.toString(), mimeType = mime, fileSize = size,
            )
        }
        find(id)?.let { Notifier.completed(app, it) }
    }

    private fun pickOutput(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() !in skipExt && !it.name.contains(".part") }
            ?.maxByOrNull { it.length() }

    private fun markCanceled(id: String) {
        canceled.remove(id)
        mutate(id) { copy(status = Status.CANCELED, etaSec = -1) }
        persist()
    }

    private fun fail(id: String, e: Exception) {
        mutate(id) { copy(status = Status.FAILED, error = friendlyError(e), etaSec = -1) }
        persist()
        find(id)?.let { Notifier.failed(app, it) }
    }
}
