package app.padmapper

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class MapperService : AccessibilityService() {
    companion object {
        @Volatile var instance: MapperService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var editor: EditorView? = null

    override fun onServiceConnected() {
        instance = this
        Engine.ensureLoaded(this)
        Engine.screen = {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            Rect(wm.maximumWindowMetrics.bounds)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Engine.setEnabled(false)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_F8 -> {
                if (down && e.repeatCount == 0) toggleMapping()
                return true
            }
            KeyEvent.KEYCODE_F9 -> {
                if (down && e.repeatCount == 0) toggleEditor()
                return true
            }
        }
        return Engine.handleKey(e.keyCode, down)
    }

    fun toggleMapping() {
        main.post {
            Engine.setEnabled(!Engine.enabled)
            Toast.makeText(this, if (Engine.enabled) "Pemetaan AKTIF" else "Pemetaan MATI", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleEditor() {
        main.post { if (editor != null) closeEditor(false) else showEditor() }
    }

    fun showEditor() {
        main.post {
            if (editor != null) return@post
            Engine.setEnabled(false)
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val v = EditorView(this, Engine.profile) { save -> closeEditor(save) }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            wm.addView(v, lp)
            editor = v
        }
    }

    private fun closeEditor(save: Boolean) {
        main.post {
            val v = editor ?: return@post
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            try { wm.removeView(v) } catch (_: Exception) {}
            editor = null
            if (save) ProfileStore.save(this, Engine.profile) else Engine.reload(this)
            Toast.makeText(this, if (save) "Tata letak disimpan" else "Dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }
}
