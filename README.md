# SannyDroid (GTA SA Mobile)

Editor + compiler script CLEO Android (`.csa`) untuk GTA San Andreas Mobile, terinspirasi Sanny Builder.
Database opcode diambil dari `app/src/main/assets` (file asli Sanny Builder 4.2.0: `SASCM.INI`, `sa_mobile.json`, dst).

## Build lewat GitHub
1. Buat repo kosong di GitHub, lalu push isi folder ini (branch `main`).
2. Buka tab **Actions** -> workflow **Build APK** (jalan otomatis saat push).
3. Setelah hijau, unduh artifact **SannyDroid-debug-apk** -> `app-debug.apk` -> install.

Workflow menjalankan `:core:test` (unit test compiler) lebih dulu, lalu `:app:assembleDebug`.
Kalau gagal, lihat log langkah "Test compiler core and build debug APK" dan kirim ke saya.

## Push dari Termux
```
pkg install git
cd SannyDroid
git init && git add . && git commit -m "init"
git branch -M main
git remote add origin https://github.com/USER/REPO.git
git push -u origin main
```

## Status fitur
- [x] Editor dengan syntax highlight, buka/simpan sumber
- [x] Compile sintaks opcode klasik -> `.csa` (label, `0000:`, `not`, konstanta, string 'x' / "x")
- [ ] Decompile `.csa` -> teks
- [ ] Sintaks tingkat tinggi (`if ... then ... end`, `while`, `for`, `Player.Defined(...)`)
- [ ] Nama variabel (`$PLAYER_CHAR`), array, nama model (.ide) dan kunci GXT
- [ ] Autocomplete opcode, template, tema warna (`assets/themes`)
