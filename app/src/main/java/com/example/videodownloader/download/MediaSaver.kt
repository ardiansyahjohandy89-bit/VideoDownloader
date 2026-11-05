package com.example.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.videodownloader.data.SaveLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

private const val SUBFOLDER = "VideoDownloader"

/** Memindahkan hasil unduhan dari folder sementara ke galeri/Download publik. */
object MediaSaver {

    suspend fun save(
        context: Context,
        src: File,
        displayName: String,
        mime: String,
        location: SaveLocation,
    ): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, src, displayName, mime, location)
        } else {
            saveLegacy(context, src, displayName, mime, location)
        }
    }

    private fun saveScoped(
        context: Context,
        src: File,
        displayName: String,
        mime: String,
        location: SaveLocation,
    ): Uri {
        val top = mime.substringBefore('/')
        val (collection, folder) = when {
            location == SaveLocation.DOWNLOADS || (top != "video" && top != "audio") ->
                MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
            top == "audio" ->
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else ->
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$SUBFOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Gagal membuat berkas di penyimpanan.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Gagal menulis berkas.")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(
        context: Context,
        src: File,
        displayName: String,
        mime: String,
        location: SaveLocation,
    ): Uri {
        val top = mime.substringBefore('/')
        val type = when {
            location == SaveLocation.DOWNLOADS || (top != "video" && top != "audio") -> Environment.DIRECTORY_DOWNLOADS
            top == "audio" -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_MOVIES
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(type), SUBFOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Tidak bisa membuat folder $SUBFOLDER.")
        val base = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        var dest = File(dir, displayName)
        var n = 1
        while (dest.exists()) {
            dest = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        src.copyTo(dest)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf(mime)) { _, uri ->
                cont.resume(uri ?: Uri.fromFile(dest))
            }
        }
    }

    fun exists(context: Context, uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            File(parsed.path.orEmpty()).exists()
        } else {
            context.contentResolver.openFileDescriptor(parsed, "r")?.use { true } ?: false
        }
    }.getOrDefault(false)

    fun delete(context: Context, uri: String) {
        runCatching {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file") {
                File(parsed.path.orEmpty()).delete()
            } else {
                context.contentResolver.delete(parsed, null, null)
            }
        }
    }
}
