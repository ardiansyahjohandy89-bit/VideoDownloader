package com.example.videodownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videodownloader.data.DownloadItem
import com.example.videodownloader.data.Status
import com.example.videodownloader.download.formatDuration
import com.example.videodownloader.download.formatSize

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val items by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) { vm.verifyFiles() }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    DownloadIcon, contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text("Belum ada unduhan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tempel tautan di Beranda untuk mulai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (items.any { !it.isActive }) {
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${items.size} item", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = vm::clearFinished) { Text("Bersihkan riwayat") }
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onCancel = { vm.cancel(item.id) },
                    onRetry = { vm.retry(item.id) },
                    onPlay = { openItem(context, item) },
                    onShare = { shareItem(context, item) },
                    onDelete = { toDelete = item },
                )
            }
        }
    }

    toDelete?.let { item ->
        val hasFile = item.status == Status.COMPLETED && !item.fileMissing
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus dari daftar?") },
            text = {
                Text(
                    if (hasFile) "Anda juga bisa menghapus berkas videonya dari perangkat."
                    else "Item ini akan dihapus dari riwayat."
                )
            },
            confirmButton = {
                if (hasFile) {
                    TextButton(onClick = { vm.remove(item, deleteFile = true); toDelete = null }) {
                        Text("Hapus beserta berkas", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { vm.remove(item, deleteFile = false); toDelete = null }) { Text("Hapus") }
                }
            },
            dismissButton = {
                Row {
                    if (hasFile) {
                        TextButton(onClick = { vm.remove(item, deleteFile = false); toDelete = null }) {
                            Text("Hanya riwayat")
                        }
                    }
                    TextButton(onClick = { toDelete = null }) { Text("Batal") }
                }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(item.thumbnail, Modifier.size(width = 96.dp, height = 60.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (item.status) {
                Status.QUEUED, Status.PROCESSING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (item.status == Status.QUEUED) {
                        TextButton(onClick = onCancel) { Text("Batal") }
                    }
                }
                Status.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${item.progress.toInt()}%" +
                                if (item.etaSec > 0) " · sisa ${formatDuration(item.etaSec)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Batal")
                        }
                    }
                }
                Status.COMPLETED -> {
                    if (item.fileMissing) {
                        Text(
                            "Berkas tidak ditemukan di perangkat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row {
                            TextButton(onClick = onRetry) { ActionLabel(Icons.Default.Refresh, "Unduh ulang") }
                            TextButton(onClick = onDelete) { ActionLabel(Icons.Default.Delete, "Hapus") }
                        }
                    } else {
                        Row {
                            TextButton(onClick = onPlay) { ActionLabel(Icons.Default.PlayArrow, "Putar") }
                            TextButton(onClick = onShare) { ActionLabel(Icons.Default.Share, "Bagikan") }
                            TextButton(onClick = onDelete) { ActionLabel(Icons.Default.Delete, "Hapus") }
                        }
                    }
                }
                Status.FAILED, Status.CANCELED -> {
                    if (item.status == Status.FAILED && !item.error.isNullOrBlank()) {
                        Text(
                            item.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row {
                        TextButton(onClick = onRetry) {
                            ActionLabel(Icons.Default.Refresh, if (item.status == Status.FAILED) "Coba lagi" else "Ulangi")
                        }
                        TextButton(onClick = onDelete) { ActionLabel(Icons.Default.Delete, "Hapus") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text(text)
}

private fun subtitle(item: DownloadItem): String {
    val status = when (item.status) {
        Status.QUEUED -> "Menunggu antrean"
        Status.DOWNLOADING -> "Mengunduh"
        Status.PROCESSING -> "Memproses & menyimpan…"
        Status.COMPLETED -> listOf("Selesai", formatSize(item.fileSize)).filter { it.isNotEmpty() }.joinToString(" · ")
        Status.FAILED -> "Gagal"
        Status.CANCELED -> "Dibatalkan"
    }
    return "${item.preset.label} · $status"
}
