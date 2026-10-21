# Owo Hunt App (nama sementara)

Makro OwO native Android (Kotlin + Jetpack Compose + Miuix) — hasil migrasi dari
dashboard web `owo-macro-dashboard` (repo makro, jangan dilupar keduanya).

## Fase kerja
Lihat `docs/` (akan mengisi) — rencana lengkap di repo makro:
`migration-plan.md` & `task-breakdown.md`.

## Status
Scaffold F0 — modul `:app` (UI Compose+Miuix), `:core` (logika & jadwal),
`:automation` (adapter Shizuku/mock).

## Build (CI)
- push/PR → debug APK (artifact Actions).
- tag `v*` → GitHub Release dengan APK.

## Struktur
```
app        → layar UI + service + notifikasi
core       → scheduler, captcha, store, session (pure Kotlin)
automation → AutomationAdapter: ShizukuAdapter / MockAdapter
```

## Lisensi
- Kode aplikasi: milik manusia-makro (pengguna) — belum diset tantangan lisensi rotesis.
- Miuix: Apache-2.0 (NOTICE).
