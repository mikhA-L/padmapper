# PadMapper

Keymapper pribadi: keyboard / gamepad / mouse -> sentuhan layar Android (butuh Shizuku, tanpa root).

## Build lewat GitHub Actions
1. Buat repositori baru di GitHub, unggah seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** -> workflow **Build APK** (jalan otomatis saat push, atau klik *Run workflow*).
3. Setelah hijau, buka run-nya -> bagian **Artifacts** -> unduh `padmapper-debug-apk` (zip berisi `app-debug.apk`).
4. Pasang APK di HP.

## Pemakaian
1. Pasang & start **Shizuku** (Debugging nirkabel), lalu di PadMapper tekan "1. Izinkan Shizuku".
2. "2. Aktifkan layanan Aksesibilitas" -> nyalakan PadMapper.
3. "3. Mulai pembaca gamepad/mouse" (untuk stik analog dan mouse).
4. "Edit tata letak": overlay muncul, tekan Home, buka game, geser joystick/kamera/tombol ke posisi kontrol game, tekan Simpan.
5. Nyalakan pemetaan ("Aktif/matikan pemetaan" atau F8 di keyboard), lalu main.

## Batasan v1
- Satu profil untuk semua game.
- Perangkat input tidak di-grab: kursor mouse Android tetap bergerak dan klik tetap sampai ke layar.
- Pemetaan tombol di kode (Profile.kt), belum bisa diubah lewat UI.
- Belum diuji di perangkat nyata; `injectInputEvent` bisa beda antar versi Android.
