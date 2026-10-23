package app.padmapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Overlay layar penuh untuk menggeser posisi joystick, kamera, dan tombol. */
class EditorView(
    ctx: Context,
    private val p: Profile,
    private val done: (Boolean) -> Unit
) : View(ctx) {

    private class Item(val label: String, val pt: Pt, val radius: Float, val color: Int)

    private val dp = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val loc = IntArray(2)
    private var drag: Item? = null
    private val saveRect = RectF()
    private val cancelRect = RectF()

    private fun items(): List<Item> {
        val s = Engine.screen()
        val md = min(s.width(), s.height()).toFloat()
        val list = ArrayList<Item>()
        list.add(Item("Joystick (WASD / stik kiri)", p.joy, p.joyRadius * md, Color.argb(255, 66, 165, 245)))
        list.add(Item("Kamera (mouse / stik kanan)", p.cam, p.camRadius * md, Color.argb(255, 102, 187, 106)))
        for (b in p.buttons) list.add(Item(b.name, b.pt, 30 * dp, Color.argb(255, 255, 167, 38)))
        return list
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(loc)
        val s = Engine.screen()
        canvas.drawColor(Color.argb(120, 0, 0, 0))
        paint.textSize = 13 * dp
        paint.textAlign = Paint.Align.CENTER
        for (item in items()) {
            val x = item.pt.x * s.width() - loc[0]
            val y = item.pt.y * s.height() - loc[1]
            paint.style = Paint.Style.FILL
            paint.color = (item.color and 0x00FFFFFF) or (0x44 shl 24)
            canvas.drawCircle(x, y, item.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * dp
            paint.color = item.color
            canvas.drawCircle(x, y, item.radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawText(item.label, x, y + 5 * dp, paint)
        }
        // tombol Simpan / Batal (koordinat layar)
        val cx = s.width() / 2f
        saveRect.set(cx - 130 * dp, 24 * dp, cx - 10 * dp, 72 * dp)
        cancelRect.set(cx + 10 * dp, 24 * dp, cx + 130 * dp, 72 * dp)
        drawBtn(canvas, saveRect, "Simpan", Color.rgb(46, 125, 50))
        drawBtn(canvas, cancelRect, "Batal", Color.rgb(183, 28, 28))
    }

    private fun drawBtn(canvas: Canvas, r: RectF, text: String, color: Int) {
        val o = RectF(r.left - loc[0], r.top - loc[1], r.right - loc[0], r.bottom - loc[1])
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(o, 8 * dp, 8 * dp, paint)
        paint.color = Color.WHITE
        paint.textSize = 16 * dp
        canvas.drawText(text, o.centerX(), o.centerY() + 6 * dp, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val s = Engine.screen()
        val rx = e.rawX
        val ry = e.rawY
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (saveRect.contains(rx, ry)) { done(true); return true }
                if (cancelRect.contains(rx, ry)) { done(false); return true }
                var best: Item? = null
                var bestDist = 0f
                for (item in items()) {
                    val d = hypot(rx - item.pt.x * s.width(), ry - item.pt.y * s.height())
                    if (d > maxOf(item.radius, 40 * dp)) continue
                    val b = best
                    if (b == null || item.radius < b.radius || (item.radius == b.radius && d < bestDist)) {
                        best = item
                        bestDist = d
                    }
                }
                drag = best
            }
            MotionEvent.ACTION_MOVE -> {
                val d = drag
                if (d != null) {
                    d.pt.x = (rx / s.width()).coerceIn(0f, 1f)
                    d.pt.y = (ry / s.height()).coerceIn(0f, 1f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = null
        }
        return true
    }
}
