package com.example.videodownloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Locale

private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

/** Mengambil tautan pertama dari teks (berguna untuk teks hasil "Bagikan"). */
fun extractUrl(text: String): String? =
    URL_REGEX.find(text)?.value?.trimEnd(')', ']', '.', ',', ';', '"', '\'')

fun formatDuration(totalSec: Long): String {
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, if (i == 0) "%.0f %s" else "%.1f %s", v, units[i])
}

fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .trim()
        .trim('.')
        .take(100)
        .ifBlank { "video" }

fun mimeFor(ext: String, isAudio: Boolean): String = when (ext.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "3gp" -> "video/3gpp"
    "flv" -> "video/x-flv"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "opus", "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    "wav" -> "audio/x-wav"
    else -> if (isAudio) "audio/mpeg" else "video/mp4"
}

/** Mengubah pesan error teknis yt-dlp menjadi pesan yang mudah dipahami. */
fun friendlyError(e: Throwable): String {
    val raw = e.message ?: e.toString()
    val line = raw.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
        ?: raw.lines().lastOrNull { it.isNotBlank() }
        ?: raw
    val clean = line.substringAfter("ERROR:", line).trim()
    return when {
        clean.contains("Unsupported URL", true) -> "Situs atau tautan ini tidak didukung."
        clean.contains("DRM", true) -> "Video dilindungi DRM dan tidak dapat diunduh."
        clean.contains("Unable to resolve host", true) ||
            clean.contains("Network is unreachable", true) ||
            clean.contains("timed out", true) -> "Koneksi internet bermasalah. Coba lagi."
        clean.contains("403") -> "Akses ditolak (403). Coba perbarui yt-dlp di Pengaturan."
        clean.contains("Sign in", true) || clean.contains("login", true) ->
            "Video ini memerlukan login/akun, sehingga tidak bisa diunduh."
        clean.contains("Private video", true) -> "Video ini bersifat privat."
        clean.contains("not available", true) -> "Video tidak tersedia (dihapus atau dibatasi wilayah)."
        else -> clean.take(300).ifBlank { "Terjadi kesalahan yang tidak diketahui." }
    }
}

fun isUnmeteredNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
