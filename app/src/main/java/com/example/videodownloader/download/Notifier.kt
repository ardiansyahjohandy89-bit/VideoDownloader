package com.example.videodownloader.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.videodownloader.MainActivity
import com.example.videodownloader.R
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.Status

object Notifier {
    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"
    const val ID_FOREGROUND = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Progres unduhan", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Unduhan selesai", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_TAB, 1)
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Notifikasi ongoing untuk foreground service. */
    fun progress(context: Context, items: List<DownloadItem>): Notification {
        val active = items.filter { it.isActive }
        val downloading = items.filter { it.status == Status.DOWNLOADING }
        val avg = if (downloading.isEmpty()) 0 else downloading.map { it.progress }.average().toInt()
        val title = if (active.size == 1) active[0].displayTitle else "Mengunduh ${active.size} berkas"
        val text = when {
            downloading.isNotEmpty() -> "$avg%" + if (active.size > downloading.size) " · ${active.size - downloading.size} menunggu" else ""
            active.any { it.status == Status.PROCESSING } -> "Memproses…"
            else -> "Menunggu antrean…"
        }
        val cancelAll = PendingIntent.getService(
            context, 1,
            Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, avg, downloading.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Batalkan semua", cancelAll)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun completed(context: Context, item: DownloadItem) {
        val uri = item.outputUri?.let { Uri.parse(it) }
        val contentIntent = if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PendingIntent.getActivity(
                context, item.id.hashCode(), view,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            openAppIntent(context)
        }
        notify(
            context, item.id.hashCode(),
            NotificationCompat.Builder(context, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Unduhan selesai")
                .setContentText(item.displayTitle)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        )
    }

    fun failed(context: Context, item: DownloadItem) {
        notify(
            context, item.id.hashCode(),
            NotificationCompat.Builder(context, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Unduhan gagal")
                .setContentText(item.displayTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.error ?: item.displayTitle))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, notification)
    }
}
