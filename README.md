# OwO Hunt Macro (APK Android)

Dashboard makro OwO Bot (Discord) untuk Android, dibangun native dengan Kotlin + Jetpack Compose + Miuix. Autentikasi rotasi command OwO berjalan di background service, dengan anti-detect (jeda acak, grace period, micro-break) dan deteksi captcha.

## Fitur
- **Dashboard**: status sesi, progres statistik, kontrol Mulai/Berhenti/Darurat
- **Rotasi**: editor urutan command OwO (hunt/gamble dll), acak urutan
- **Pengaturan**: jeda antar command, durasi sesi, break, grace period, preset
- **Log**: ring log dengan filter
- **Notifikasi**: status sesi + peringatan captcha
- **Wizard Shizuku**: status koneksi, permintaan izin, cek biner `rish`, probe shell

## Persyaratan
- Android 8.0+ (API 26). Blur UI aktif di Android 13+.
- **Shizuku** (disarankan) atau akses shell untuk menjalankan command Discord.

## Cara Install & Setup
1. Install APK dari [Releases](https://github.com/ItsCookie177/owo-hunt-app/releases) (pakai `debug` — bisa langsung di-install).
2. Install [Shizuku](https://shizuku.rikka.app/download), aktifkan via Wireless debugging (Android 11+) atau ADB PC.
3. Buka OwO Hunt → tab **Pengaturan** → **Diagnostik → Wizard Shizuku**:
   - Tekan **Minta izin Shizuku** saat tombol muncul.
   - Ekspor biner `rish` dari aplikasi Shizuku (menu "Export rish"), lalu salin ke:
     `/sdcard/Android/data/dev.owohunt.app/files/rish` (via file manager; folder bisa dibuat manual).
     Alternatif via PC/adb: `adb push rish /sdcard/Android/data/dev.owohunt.app/files/rish`
   - Tekan **Probe shell** — jika muncul `PROBE OK`, adapter siap.
4. Atur **composerId** di Selector jika berbeda dari default (probe Diagnostik membantu).
5. Tab **Dashboard** → **Mulai**.

Perhatian: app masih menggunakan adapter mock untuk sinkronisasi penuh; Shizuku/rish path di atas adalah kebutuhan runtime shell.

## Disclaimer
Makro melanggar ToS Discord/OwO dan berisiko ban. Gunakan di akun kedua saja. Segala risiko ditanggung pengguna.

## Build
CI (GitHub Actions) sudah lengkap: push → test + assembleDebug; tag `v*` → APK di-attach ke GitHub Releases.
