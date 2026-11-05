package com.example.videodownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videodownloader.data.QualityPreset
import com.example.videodownloader.download.RuntimeState
import com.example.videodownloader.download.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val s by vm.home.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RuntimeBanner(runtime)

        OutlinedTextField(
            value = s.url,
            onValueChange = vm::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tautan video") },
            placeholder = { Text("https://…") },
            maxLines = 3,
            trailingIcon = {
                if (s.url.isNotEmpty()) {
                    IconButton(onClick = { vm.onUrlChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.fetchInfo()
            }),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { readClipboard(context)?.let(vm::paste) },
                modifier = Modifier.weight(1f),
            ) { Text("Tempel") }
            OutlinedButton(
                onClick = {
                    focus.clearFocus()
                    vm.fetchInfo()
                },
                enabled = s.url.isNotBlank() && !s.loading,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cari info")
            }
        }

        when {
            s.loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Mengambil info video…", style = MaterialTheme.typography.bodySmall)
            }
            s.error != null -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    s.error.orEmpty(),
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            s.meta != null -> {
                val meta = s.meta!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column {
                        Thumb(meta.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                meta.title.ifBlank { meta.url },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(
                                meta.uploader?.takeIf { it.isNotBlank() },
                                formatDuration(meta.durationSec).takeIf { it.isNotEmpty() },
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Kualitas") {
            Text("Video", style = MaterialTheme.typography.labelMedium)
            PresetChips(QualityPreset.entries.filter { !it.isAudio }, s.preset, vm::selectPreset)
            Text("Hanya audio", style = MaterialTheme.typography.labelMedium)
            PresetChips(QualityPreset.entries.filter { it.isAudio }, s.preset, vm::selectPreset)
        }

        Button(
            onClick = {
                focus.clearFocus()
                vm.download()
            },
            enabled = s.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(DownloadIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download")
        }

        Text(
            "Gunakan hanya untuk konten yang Anda miliki atau yang memang boleh diunduh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    presets: List<QualityPreset>,
    selected: QualityPreset,
    onSelect: (QualityPreset) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { p ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(p.label) },
            )
        }
    }
}

@Composable
private fun RuntimeBanner(state: RuntimeState) {
    when (state) {
        RuntimeState.Ready -> Unit
        RuntimeState.Initializing -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Menyiapkan mesin unduhan…", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pertama kali dibuka butuh beberapa detik. Pencarian dan unduhan akan jalan otomatis setelah siap.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is RuntimeState.Failed -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mesin unduhan gagal dimulai", style = MaterialTheme.typography.titleSmall)
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
