package app.padmapper

import android.graphics.PointF
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/** Menyuntikkan sentuhan multitouch lewat IInputManager (hak akses shell via Shizuku). Hanya dipakai dari thread engine. */
object TouchInjector {
    private const val TAG = "TouchInjector"
    private var im: Any? = null
    private var inject: Method? = null
    private val pointers = LinkedHashMap<Int, PointF>()
    private var downTime = 0L

    private fun ensure(): Boolean {
        if (im != null) return true
        return try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val iface = Class.forName("android.hardware.input.IInputManager")
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val obj = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            inject = iface.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            im = obj
            true
        } catch (t: Throwable) {
            Log.e(TAG, "init gagal", t)
            false
        }
    }

    fun down(id: Int, x: Float, y: Float) {
        if (pointers.containsKey(id)) return
        val first = pointers.isEmpty()
        if (first) downTime = SystemClock.uptimeMillis()
        pointers[id] = PointF(x, y)
        val idx = pointers.keys.indexOf(id)
        send(
            if (first) MotionEvent.ACTION_DOWN
            else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        )
    }

    fun move(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        p.set(x, y)
        send(MotionEvent.ACTION_MOVE)
    }

    fun up(id: Int) {
        val idx = pointers.keys.indexOf(id)
        if (idx < 0) return
        send(
            if (pointers.size == 1) MotionEvent.ACTION_UP
            else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        )
        pointers.remove(id)
    }

    fun releaseAll() {
        for (id in pointers.keys.toList().reversed()) up(id)
    }

    private fun send(action: Int) {
        if (!ensure()) return
        val ids = pointers.keys.toList()
        val n = ids.size
        val props = Array(n) {
            MotionEvent.PointerProperties().apply {
                id = ids[it]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(n) {
            MotionEvent.PointerCoords().apply {
                val p = pointers[ids[it]]!!
                x = p.x; y = p.y; pressure = 1f; size = 1f
            }
        }
        val ev = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            inject!!.invoke(im, ev, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        } catch (t: Throwable) {
            Log.e(TAG, "inject gagal", t)
        } finally {
            ev.recycle()
        }
    }
}
