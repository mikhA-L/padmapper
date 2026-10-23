package app.padmapper

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

/** Mengikat EvdevService (proses shell) lewat Shizuku dan meneruskan event ke Engine. */
object EvdevClient {
    @Volatile var service: IEvdevService? = null
        private set

    private val callback = object : IEvdevCallback.Stub() {
        override fun onAxis(code: Int, value: Float) = Engine.onAxis(code, value)
        override fun onRel(dx: Int, dy: Int) = Engine.onRel(dx, dy)
        override fun onButton(code: Int, down: Boolean) = Engine.onButton(code, down)
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                val s = IEvdevService.Stub.asInterface(binder)
                service = s
                try { s.start(callback) } catch (_: Exception) {}
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun args(ctx: Context) =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, EvdevService::class.java.name))
            .processNameSuffix("evdev")
            .daemon(false)
            .debuggable(false)
            .version(1)

    fun start(ctx: Context) {
        Shizuku.bindUserService(args(ctx), conn)
    }

    fun stop(ctx: Context) {
        try { service?.stop() } catch (_: Exception) {}
        try { Shizuku.unbindUserService(args(ctx), conn, true) } catch (_: Exception) {}
        service = null
    }
}
