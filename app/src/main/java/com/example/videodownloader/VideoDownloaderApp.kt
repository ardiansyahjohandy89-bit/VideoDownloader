package com.example.videodownloader

import android.app.Application
import com.example.videodownloader.data.HistoryStore
import com.example.videodownloader.data.SettingsStore
import com.example.videodownloader.download.DownloadEngine
import com.example.videodownloader.download.Notifier
import com.example.videodownloader.download.YtDlpRuntime

class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        HistoryStore.init(this)
        Notifier.createChannels(this)
        DownloadEngine.init(this)
        YtDlpRuntime.start(this) // berjalan di latar belakang
    }
}
