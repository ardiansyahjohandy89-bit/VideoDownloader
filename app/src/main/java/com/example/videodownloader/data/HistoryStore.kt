package com.example.videodownloader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Menyimpan riwayat unduhan sebagai file JSON sederhana (tanpa database). */
object HistoryStore {
    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "history.json")
    }

    @Synchronized
    fun load(): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        runCatching {
            val arr = JSONArray()
            items.forEach { arr.put(toJson(it)) }
            val tmp = File(file.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key) || !has(key)) null else getString(key)

    private fun toJson(i: DownloadItem) = JSONObject().apply {
        put("id", i.id)
        put("url", i.url)
        put("title", i.title)
        put("thumbnail", i.thumbnail ?: JSONObject.NULL)
        put("uploader", i.uploader ?: JSONObject.NULL)
        put("duration", i.durationSec)
        put("preset", i.preset.name)
        put("status", i.status.name)
        put("progress", i.progress.toDouble())
        put("error", i.error ?: JSONObject.NULL)
        put("outputUri", i.outputUri ?: JSONObject.NULL)
        put("mime", i.mimeType ?: JSONObject.NULL)
        put("size", i.fileSize)
        put("createdAt", i.createdAt)
    }

    private fun fromJson(o: JSONObject): DownloadItem? = runCatching {
        DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title", ""),
            thumbnail = o.str("thumbnail"),
            uploader = o.str("uploader"),
            durationSec = o.optLong("duration", 0),
            preset = QualityPreset.entries.firstOrNull { it.name == o.optString("preset") } ?: QualityPreset.BEST,
            status = Status.entries.firstOrNull { it.name == o.optString("status") } ?: Status.FAILED,
            progress = o.optDouble("progress", 0.0).toFloat(),
            error = o.str("error"),
            outputUri = o.str("outputUri"),
            mimeType = o.str("mime"),
            fileSize = o.optLong("size", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }.getOrNull()
}
