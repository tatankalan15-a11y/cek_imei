# Cek IMEI (Android, offline)

Aplikasi Android native (Kotlin) untuk teknisi service HP. Cek IMEI dan database bekerja offline; internet hanya dipakai saat menekan tombol "Cari online".

Fitur:
- Scan IMEI langsung dengan kamera (angka atau barcode) atau dari foto galeri, tetap jalan offline
- Validasi IMEI (algoritma Luhn) dan ambil TAC
- Cari tipe HP dari database TAC lokal
- Impor database TAC dari file CSV (format `TAC,Merek,Model`)
- Simpan tipe baru untuk TAC yang belum dikenal
- Tombol "Cari online" (imei.info / Google) di dalam aplikasi untuk TAC yang belum dikenal
- Info merek/model HP yang sedang dipakai

## Cara dapat APK tanpa Android Studio

1. Upload semua isi folder ini ke repository GitHub (branch `main`), termasuk folder `.github`.
2. Buka tab **Actions**, tunggu workflow **Build APK** selesai (hijau).
3. Buka run tersebut, unduh artifact **cek-imei-apk**, ekstrak, lalu pasang `app-debug.apk` di HP
   (izinkan "Install dari sumber tidak dikenal").

## Cara lain: Android Studio

Buka folder ini di Android Studio, tunggu Gradle sync, lalu Run.

## Database TAC

Isi `app/src/main/assets/tac.csv` sebelum build agar data ikut tertanam, atau impor CSV langsung dari aplikasi.
