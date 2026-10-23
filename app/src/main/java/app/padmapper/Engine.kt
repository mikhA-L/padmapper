package app.padmapper

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Mengubah input (tombol/stik/mouse) menjadi sentuhan. Semua state dimiliki thread engine. */
object Engine {
    private const val JOY = 0
    private const val CAM = 1
    private const val BTN0 = 2
    private const val DEADZONE = 0.15f

    @Volatile var enabled = false
        private set
    @Volatile var profile = Profile()
    @Volatile var screen: () -> Rect = { Rect(0, 0, 1080, 2400) }

    private var loaded = false
    private val ht = HandlerThread("padmapper-engine").apply { start() }
    private val h = Handler(ht.looper)

    private val held = HashSet<Int>()
    private var kx = 0f; private var ky = 0f   // WASD
    private var sx = 0f; private var sy = 0f   // stik kiri
    private var rx = 0f; private var ry = 0f   // stik kanan
    private var joyDown = false
    private var camDown = false
    private var camX = 0f; private var camY = 0f
    private var tickOn = false

    fun ensureLoaded(ctx: Context) {
        if (!loaded) {
            profile = ProfileStore.load(ctx.applicationContext)
            loaded = true
        }
    }

    fun reload(ctx: Context) {
        profile = ProfileStore.load(ctx.applicationContext)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) h.post { reset() }
    }

    private fun reset() {
        h.removeCallbacksAndMessages(null)
        TouchInjector.releaseAll()
        held.clear()
        kx = 0f; ky = 0f; sx = 0f; sy = 0f; rx = 0f; ry = 0f
        joyDown = false; camDown = false; tickOn = false
    }

    /** Dipanggil dari AccessibilityService (thread utama). true = tombol dicegat. */
    fun handleKey(code: Int, down: Boolean): Boolean {
        if (!enabled) return false
        if (code !in profile.watched) return false
        h.post { key(code, down) }
        return true
    }

    private fun key(code: Int, down: Boolean) {
        if (!enabled) return
        val p = profile
        if (p.moveKeys.containsKey(code)) {
            if (down) { if (!held.add(code)) return } else held.remove(code)
            kx = 0f; ky = 0f
            for (c in held) p.moveKeys[c]?.let { kx += it.first; ky += it.second }
            updateJoy()
            return
        }
        val idx = p.buttons.indexOfFirst { code in it.keys }
        if (idx < 0) return
        val b = p.buttons[idx]
        val s = screen()
        if (down) {
            if (!held.add(code)) return
            TouchInjector.down(BTN0 + idx, b.pt.x * s.width(), b.pt.y * s.height())
        } else if (held.remove(code)) {
            TouchInjector.up(BTN0 + idx)
        }
    }

    private fun dz(v: Float) = if (abs(v) < DEADZONE) 0f else v

    // ---- dari pembaca evdev (thread binder) ----
    fun onAxis(code: Int, v: Float) {
        if (!enabled) return
        h.post {
            val d = dz(v)
            val ps = profile.psLayout
            when (code) {
                0 -> { sx = d; updateJoy() }
                1 -> { sy = d; updateJoy() }
                (if (ps) 2 else 3) -> { rx = d; kickTick() }
                (if (ps) 5 else 4) -> { ry = d; kickTick() }
            }
        }
    }

    fun onRel(dx: Int, dy: Int) {
        if (!enabled) return
        h.post { camMove(dx * profile.sens * 1.5f, dy * profile.sens * 1.5f) }
    }

    fun onButton(code: Int, down: Boolean) {
        if (!enabled) return
        val id = 10000 + code
        h.post { key(id, down) }
    }

    // ---- joystick ----
    private fun updateJoy() {
        var vx = kx + sx
        var vy = ky + sy
        val len = hypot(vx, vy)
        if (len > 1f) { vx /= len; vy /= len }
        if (len < 0.01f) {
            if (joyDown) { TouchInjector.up(JOY); joyDown = false }
            return
        }
        val s = screen()
        val md = min(s.width(), s.height()).toFloat()
        val cx = profile.joy.x * s.width()
        val cy = profile.joy.y * s.height()
        val r = profile.joyRadius * md
        if (!joyDown) { TouchInjector.down(JOY, cx, cy); joyDown = true }
        TouchInjector.move(JOY, cx + vx * r, cy + vy * r)
    }

    // ---- kamera ----
    private val camRelease = Runnable {
        if (camDown) { TouchInjector.up(CAM); camDown = false }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!enabled || (rx == 0f && ry == 0f)) { tickOn = false; return }
            camMove(rx * 22f * profile.sens, ry * 22f * profile.sens)
            h.postDelayed(this, 16)
        }
    }

    private fun kickTick() {
        if (!tickOn && (rx != 0f || ry != 0f)) { tickOn = true; h.post(tick) }
    }

    private fun camMove(dx: Float, dy: Float) {
        if (!enabled) return
        val s = screen()
        val md = min(s.width(), s.height()).toFloat()
        val cx = profile.cam.x * s.width()
        val cy = profile.cam.y * s.height()
        val rad = profile.camRadius * md
        if (!camDown) { camX = cx; camY = cy; TouchInjector.down(CAM, cx, cy); camDown = true }
        camX += dx; camY += dy
        if (hypot(camX - cx, camY - cy) > rad) {
            // jari sudah di tepi area: angkat dan taruh lagi di tengah
            TouchInjector.up(CAM)
            camX = cx; camY = cy
            TouchInjector.down(CAM, cx, cy)
        } else {
            TouchInjector.move(CAM, camX, camY)
        }
        h.removeCallbacks(camRelease)
        h.postDelayed(camRelease, 120)
    }
}
