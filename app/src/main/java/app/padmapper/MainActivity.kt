package app.padmapper

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var status: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick(); refresh() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Engine.ensureLoaded(this)
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListener(binderListener)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }
        col.addView(TextView(this).apply { text = "PadMapper"; textSize = 26f })
        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(12), 0, dp(12)) }
        col.addView(status)

        col.addView(button("1. Izinkan Shizuku") {
            when {
                !Shizuku.pingBinder() -> toast("Shizuku belum berjalan. Buka aplikasi Shizuku dan start dulu.")
                shizukuReady() -> toast("Sudah diizinkan")
                else -> Shizuku.requestPermission(1)
            }
        })
        col.addView(button("2. Aktifkan layanan Aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("3. Mulai pembaca gamepad/mouse") {
            if (shizukuReady()) EvdevClient.start(this) else toast("Izin Shizuku belum ada")
        })
        col.addView(button("Hentikan pembaca gamepad/mouse") { EvdevClient.stop(this) })
        col.addView(button("Edit tata letak (overlay)") {
            val s = MapperService.instance
            if (s == null) toast("Aktifkan layanan Aksesibilitas dulu")
            else { s.showEditor(); toast("Overlay tampil. Tekan Home, buka game, atur posisi, lalu Simpan.") }
        })
        col.addView(button("Aktif/matikan pemetaan") {
            when {
                MapperService.instance == null -> toast("Aktifkan layanan Aksesibilitas dulu")
                !shizukuReady() -> toast("Izin Shizuku belum ada")
                else -> Engine.setEnabled(!Engine.enabled)
            }
        })

        col.addView(TextView(this).apply { text = "Sensitivitas kamera"; setPadding(0, dp(16), 0, 0) })
        col.addView(SeekBar(this).apply {
            max = 100
            progress = ((Engine.profile.sens - 0.2f) / 0.028f).toInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    Engine.profile.sens = 0.2f + v * 0.028f
                    ProfileStore.save(this@MainActivity, Engine.profile)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        col.addView(CheckBox(this).apply {
            text = "Stik kanan gaya PlayStation (Z/RZ)"
            isChecked = Engine.profile.psLayout
            setOnCheckedChangeListener { _, c ->
                Engine.profile.psLayout = c
                ProfileStore.save(this@MainActivity, Engine.profile)
            }
        })
        col.addView(TextView(this).apply {
            textSize = 12f
            setPadding(0, dp(16), 0, 0)
            text = "Pintasan keyboard: F8 = aktif/mati, F9 = editor tata letak.\n" +
                "Keyboard: WASD = joystick, Spasi = Lompat, R = Reload, Shift = Lari, Ctrl = Jongkok, Q = Ganti, klik kiri = Tembak, klik kanan = Bidik.\n" +
                "Gamepad: stik kiri = joystick, stik kanan = kamera, A/B/X/Y/R1/L1/L3 = tombol."
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        super.onDestroy()
    }

    private fun refresh() {
        val ping = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        fun mark(ok: Boolean) = if (ok) "OK" else "belum"
        status.text = "Shizuku berjalan: ${mark(ping)}\n" +
            "Izin Shizuku: ${mark(shizukuReady())}\n" +
            "Aksesibilitas aktif: ${mark(MapperService.instance != null)}\n" +
            "Pembaca gamepad/mouse: ${mark(EvdevClient.service != null)}\n" +
            "Pemetaan: ${if (Engine.enabled) "AKTIF" else "mati"}"
    }
}
