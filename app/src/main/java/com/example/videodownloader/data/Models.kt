package com.example.videodownloader.data

enum class Status { QUEUED, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELED }

/** Pilihan kualitas. [formatSelector] adalah ekspresi format yt-dlp (-f). */
enum class QualityPreset(
    val label: String,
    val isAudio: Boolean,
    val ext: String,
    private val maxHeight: Int? = null,
) {
    BEST("Terbaik", false, "mp4"),
    P1080("1080p", false, "mp4", 1080),
    P720("720p", false, "mp4", 720),
    P480("480p", false, "mp4", 480),
    P360("360p", false, "mp4", 360),
    MP3("Audio MP3", true, "mp3"),
    M4A("Audio M4A", true, "m4a");

    fun formatSelector(): String {
        if (isAudio) return "ba/b"
        val h = maxHeight?.let { "[height<=$it]" }.orEmpty()
        // Utamakan mp4+m4a (paling kompatibel), lalu apa pun yang tersedia.
        return "bv*[ext=mp4]$h+ba[ext=m4a]/bv*$h+ba/b$h/b"
    }
}

/** Info video hasil pencarian (sebelum diunduh). */
data class VideoMeta(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Long,
)

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String = "",
    val thumbnail: String? = null,
    val uploader: String? = null,
    val durationSec: Long = 0,
    val preset: QualityPreset = QualityPreset.BEST,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = -1,
    val error: String? = null,
    val outputUri: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val fileMissing: Boolean = false,
) {
    val isActive: Boolean
        get() = status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.PROCESSING

    val displayTitle: String
        get() = title.ifBlank { url }
}
