package com.example.videodownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videodownloader.data.QualityPreset
import com.example.videodownloader.data.SaveLocation
import com.example.videodownloader.data.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val version by vm.ytVersion.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Penyimpanan") {
            Text("Simpan hasil unduhan ke", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(
                options = SaveLocation.entries,
                selected = st.saveLocation,
                label = {
                    when (it) {
                        SaveLocation.DOWNLOADS -> "Folder Download"
                        SaveLocation.MEDIA -> "Movies & Music"
                    }
                },
                onSelect = { v -> vm.updateSettings { s -> s.copy(saveLocation = v) } },
            )
            Text(
                "Semua berkas ada di subfolder \"VideoDownloader\" dan muncul di aplikasi Galeri/File.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Unduhan") {
            Text("Kualitas bawaan", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(
                options = QualityPreset.entries,
                selected = st.defaultPreset,
                label = { it.label },
                onSelect = { v -> vm.updateSettings { s -> s.copy(defaultPreset = v) } },
            )
            Text("Unduhan bersamaan", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(
                options = listOf(1, 2, 3),
                selected = st.maxConcurrent,
                label = { "$it" },
                onSelect = { v -> vm.updateSettings { s -> s.copy(maxConcurrent = v) } },
            )
            Text("Koneksi paralel per unduhan (lebih tinggi = lebih cepat)", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(
                options = listOf(1, 2, 4, 8),
                selected = st.fragments,
                label = { "$it" },
                onSelect = { v -> vm.updateSettings { s -> s.copy(fragments = v) } },
            )
            SwitchRow(
                title = "Hanya lewat Wi-Fi",
                subtitle = "Unduhan gagal jika memakai data seluler.",
                checked = st.wifiOnly,
                onChange = { v -> vm.updateSettings { s -> s.copy(wifiOnly = v) } },
            )
            SwitchRow(
                title = "Sematkan subtitle",
                subtitle = "Menyertakan subtitle Indonesia/Inggris jika tersedia (video saja).",
                checked = st.embedSubs,
                onChange = { v -> vm.updateSettings { s -> s.copy(embedSubs = v) } },
            )
            SwitchRow(
                title = "Langsung unduh dari menu Bagikan",
                subtitle = "Saat membagikan tautan ke aplikasi ini, unduhan dimulai tanpa konfirmasi.",
                checked = st.quickShare,
                onChange = { v -> vm.updateSettings { s -> s.copy(quickShare = v) } },
            )
        }

        SectionCard("Tampilan") {
            ChoiceChips(
                options = ThemeMode.entries,
                selected = st.theme,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "Ikuti sistem"
                        ThemeMode.LIGHT -> "Terang"
                        ThemeMode.DARK -> "Gelap"
                    }
                },
                onSelect = { v -> vm.updateSettings { s -> s.copy(theme = v) } },
            )
        }

        SectionCard("Mesin unduhan (yt-dlp)") {
            Text(
                "Versi: ${version ?: "memuat…"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Situs sering mengubah sistemnya. Jika suatu situs tiba-tiba gagal, perbarui yt-dlp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm::updateYtDlp, enabled = !updating, modifier = Modifier.fillMaxWidth()) {
                if (updating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memperbarui…")
                } else {
                    Text("Periksa pembaruan")
                }
            }
            SwitchRow(
                title = "Perbarui otomatis",
                subtitle = "Memeriksa pembaruan di latar belakang maksimal 2 kali sehari.",
                checked = st.autoUpdate,
                onChange = { v -> vm.updateSettings { s -> s.copy(autoUpdate = v) } },
            )
            SwitchRow(
                title = "Gunakan channel nightly",
                subtitle = "Perbaikan situs lebih cepat, tetapi kurang stabil.",
                checked = st.nightlyChannel,
                onChange = { v -> vm.updateSettings { s -> s.copy(nightlyChannel = v) } },
            )
        }

        SectionCard("Tentang") {
            Text(
                "Ditenagai yt-dlp dan FFmpeg melalui library youtubedl-android (open source).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Gunakan hanya untuk konten yang Anda miliki hak atau izinnya. Mengunduh konten berhak cipta " +
                    "dapat melanggar ketentuan situs dan hukum yang berlaku. Video berDRM (mis. Netflix) tidak didukung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
