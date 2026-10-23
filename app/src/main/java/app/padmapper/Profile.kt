package app.padmapper

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** Posisi sebagai pecahan layar (0..1) supaya tidak tergantung resolusi. */
class Pt(var x: Float, var y: Float)

class Btn(val name: String, val keys: List<Int>, val pt: Pt)

object K {
    // Tombol mouse dari evdev dipetakan ke id 10000 + kode BTN_*
    const val MOUSE_L = 10272
    const val MOUSE_R = 10273
    const val MOUSE_M = 10274
}

class Profile {
    val joy = Pt(0.16f, 0.68f)
    val joyRadius = 0.12f          // pecahan dari sisi layar terpendek
    val cam = Pt(0.68f, 0.45f)
    val camRadius = 0.22f
    var sens = 1.0f
    var psLayout = false           // false: stik kanan = RX/RY (Xbox), true: Z/RZ (PlayStation)

    val moveKeys: Map<Int, Pair<Float, Float>> = mapOf(
        KeyEvent.KEYCODE_W to Pair(0f, -1f),
        KeyEvent.KEYCODE_S to Pair(0f, 1f),
        KeyEvent.KEYCODE_A to Pair(-1f, 0f),
        KeyEvent.KEYCODE_D to Pair(1f, 0f),
    )

    val buttons: List<Btn> = listOf(
        Btn("Lompat", listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A), Pt(0.90f, 0.72f)),
        Btn("Tembak", listOf(K.MOUSE_L, KeyEvent.KEYCODE_BUTTON_R1), Pt(0.84f, 0.55f)),
        Btn("Bidik", listOf(K.MOUSE_R, KeyEvent.KEYCODE_BUTTON_L1), Pt(0.92f, 0.42f)),
        Btn("Reload", listOf(KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_BUTTON_X), Pt(0.76f, 0.84f)),
        Btn("Jongkok", listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_BUTTON_B), Pt(0.80f, 0.92f)),
        Btn("Lari", listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BUTTON_THUMBL), Pt(0.24f, 0.42f)),
        Btn("Ganti", listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_Y), Pt(0.60f, 0.90f)),
    )

    /** Semua kode tombol yang dicegat (selain F8/F9). */
    val watched: Set<Int> by lazy { buttons.flatMap { it.keys }.toSet() + moveKeys.keys }
}

object ProfileStore {
    private const val PREF = "padmapper"

    fun load(ctx: Context): Profile {
        val p = Profile()
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("profile", null) ?: return p
        try {
            val o = JSONObject(s)
            p.joy.x = o.getDouble("jx").toFloat(); p.joy.y = o.getDouble("jy").toFloat()
            p.cam.x = o.getDouble("cx").toFloat(); p.cam.y = o.getDouble("cy").toFloat()
            p.sens = o.optDouble("sens", 1.0).toFloat()
            p.psLayout = o.optBoolean("ps", false)
            val b = o.optJSONObject("btn")
            if (b != null) for (btn in p.buttons) {
                val a = b.optJSONArray(btn.name) ?: continue
                btn.pt.x = a.getDouble(0).toFloat(); btn.pt.y = a.getDouble(1).toFloat()
            }
        } catch (_: Exception) {
        }
        return p
    }

    fun save(ctx: Context, p: Profile) {
        val o = JSONObject()
        o.put("jx", p.joy.x.toDouble()); o.put("jy", p.joy.y.toDouble())
        o.put("cx", p.cam.x.toDouble()); o.put("cy", p.cam.y.toDouble())
        o.put("sens", p.sens.toDouble()); o.put("ps", p.psLayout)
        val b = JSONObject()
        for (btn in p.buttons) b.put(btn.name, JSONArray().put(btn.pt.x.toDouble()).put(btn.pt.y.toDouble()))
        o.put("btn", b)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("profile", o.toString()).apply()
    }
}
