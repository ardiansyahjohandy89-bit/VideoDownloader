# Video Downloader (Android)

Aplikasi Android untuk mengunduh video/audio dari ribuan situs. Dibuat dengan Kotlin + Jetpack Compose, mesin unduhan memakai **yt-dlp** dan **FFmpeg** lewat library `youtubedl-android`.

## Fitur

- Tempel tautan, lihat judul, thumbnail, durasi, lalu pilih kualitas: Terbaik, 1080p, 720p, 480p, 360p, atau audio saja (MP3 / M4A)
- Unduh di latar belakang dengan notifikasi progres dan tombol "Batalkan semua"
- Antrean unduhan, bisa 1–3 unduhan bersamaan
- Menerima tautan dari menu **Bagikan** aplikasi lain (opsi: langsung unduh tanpa konfirmasi)
- Daftar unduhan: progres, batal, coba lagi, putar, bagikan, hapus (dengan atau tanpa berkas)
- Hasil disimpan ke folder **Download/VideoDownloader** atau **Movies / Music** (bisa dipilih di Pengaturan)
- Pengaturan: kualitas bawaan, koneksi paralel (lebih cepat), hanya Wi-Fi, sematkan subtitle, tema terang/gelap
- Tombol pembaruan dan pembaruan otomatis **yt-dlp** (channel stable atau nightly)

## Cara memasang Android Studio (sekali saja)

1. Buka https://developer.android.com/studio lalu unduh Android Studio untuk komputer Anda (Windows / macOS / Linux). Komputer sebaiknya punya RAM 8 GB atau lebih dan ruang kosong sekitar 15 GB.
2. Pasang seperti aplikasi biasa dan pilih pengaturan **Standard** saat wizard pertama muncul. Android Studio akan mengunduh Android SDK sendiri (butuh internet dan beberapa menit).
3. Ekstrak berkas zip proyek ini ke sebuah folder, misalnya `Documents/VideoDownloader`.

## Membuka dan menjalankan proyek

1. Di Android Studio pilih **File > Open**, lalu pilih folder proyek (yang berisi `settings.gradle.kts`).
2. Jika muncul pertanyaan "Trust project", pilih **Trust Project**.
3. Tunggu **Gradle sync** selesai (lihat bilah progres di bawah). Pertama kali bisa 5–20 menit karena mengunduh banyak komponen. Jika muncul tawaran memperbarui Android Gradle Plugin, boleh diterima.
4. Jika diminta, pasang versi Android SDK yang disebutkan (klik tautan **Install** di pesan error/peringatan).

### Cara A: langsung ke HP lewat kabel USB

1. Di HP: **Pengaturan > Tentang ponsel**, ketuk **Nomor build** 7 kali untuk mengaktifkan Opsi pengembang.
2. Masuk **Opsi pengembang**, aktifkan **Debugging USB**.
3. Sambungkan HP ke komputer, izinkan debugging saat HP bertanya.
4. Di Android Studio pilih nama HP Anda di bagian atas, lalu klik tombol **Run** (segitiga hijau).

### Cara B: membuat berkas APK untuk dipasang manual

1. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Setelah selesai, klik **locate** pada pemberitahuan di pojok kanan bawah.
3. Kirim `app-debug.apk` ke HP, buka berkasnya, dan izinkan "Pasang dari sumber tidak dikenal" jika diminta.

Catatan: APK berukuran sekitar 80–100 MB karena membawa Python dan FFmpeg.

## Hal yang perlu diketahui

- **Hanya untuk HP ARM** (hampir semua HP). Untuk emulator PC, tambahkan `"x86_64"` pada `abiFilters` di `app/build.gradle.kts`.
- **Tidak bisa dipasang lewat Google Play.** Pasang sebagai APK langsung.
- **Video DRM** (Netflix, Disney+, dan sejenisnya) tidak bisa diunduh. Situs yang butuh login juga tidak didukung.
- Jika suatu situs tiba-tiba gagal, buka **Pengaturan > Periksa pembaruan** untuk memperbarui yt-dlp.
- Aplikasi ini mengunduh satu video per tautan (playlist tidak diunduh sekaligus).
- Gunakan hanya untuk konten yang Anda miliki hak atau izinnya. Mengunduh konten berhak cipta dapat melanggar ketentuan situs dan hukum yang berlaku.

## Jika ada error saat Gradle sync atau build

Salin pesan error lengkapnya (tab **Build** di bagian bawah Android Studio) dan kirim ke Claude untuk diperbaiki.

## Struktur kode

```
app/src/main/java/com/example/videodownloader/
  MainActivity.kt, VideoDownloaderApp.kt
  data/       model data, pengaturan, riwayat (JSON)
  download/   DownloadEngine (antrean & yt-dlp), DownloadService, Notifier, MediaSaver
  ui/         layar Beranda, Unduhan, Pengaturan (Compose)
```
