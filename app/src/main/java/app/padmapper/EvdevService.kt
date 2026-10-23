package app.padmapper

import android.os.Process
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Berjalan sebagai proses shell (Shizuku UserService). Membaca /dev/input/event* langsung:
 * - mouse   -> gerak relatif + tombol
 * - gamepad -> stik analog (dinormalisasi -1..1)
 * Perangkat TIDAK di-grab, jadi game tetap bisa menerima input aslinya juga.
 */
class EvdevService : IEvdevService.Stub() {
    @Volatile private var cb: IEvdevCallback? = null
    @Volatile private var running = false
    private val streams = ArrayList<FileInputStream>()

    override fun destroy() {
        stop()
        System.exit(0)
    }

    override fun stop() {
        running = false
        synchronized(streams) {
            for (s in streams) try { s.close() } catch (_: Exception) {}
            streams.clear()
        }
    }

    override fun start(c: IEvdevCallback) {
        stop()
        cb = c
        running = true
        val files = File("/dev/input").listFiles { f -> f.name.startsWith("event") } ?: return
        for (f in files) {
            val kind = classify(f.name)
            if (kind == 0) continue
            try {
                val fis = FileInputStream(f)
                synchronized(streams) { streams.add(fis) }
                val ranges = if (kind == 2) readRanges(f.absolutePath) else null
                Thread { readLoop(fis, kind, ranges) }.apply { isDaemon = true; start() }
            } catch (_: Throwable) {
            }
        }
    }

    /** 1 = mouse, 2 = gamepad, 0 = abaikan. */
    private fun classify(name: String): Int {
        val base = "/sys/class/input/$name/device"
        val props = try {
            File("$base/properties").readText().trim().toLong(16)
        } catch (_: Exception) { 0L }
        if (props and 0x2L != 0L || props and 0x40L != 0L) return 0 // layar sentuh / sensor gerak
        if (bit("$base/capabilities/rel", 0) && bit("$base/capabilities/rel", 1)) return 1
        if (bit("$base/capabilities/abs", 0) && bit("$base/capabilities/abs", 1) &&
            (bit("$base/capabilities/key", 304) || bit("$base/capabilities/key", 288))
        ) return 2
        return 0
    }

    private fun bit(path: String, n: Int): Boolean {
        val t = try { File(path).readText().trim() } catch (_: Exception) { return false }
        if (t.isEmpty()) return false
        val words = t.split(Regex("\\s+")).reversed()
        val bits = if (Process.is64Bit()) 64 else 32
        val wi = n / bits
        if (wi >= words.size) return false
        val v = try { java.lang.Long.parseUnsignedLong(words[wi], 16) } catch (_: Exception) { return false }
        return (v ushr (n % bits)) and 1L == 1L
    }

    /** Membaca min/max tiap sumbu lewat `getevent -lp`. Hasil: [lo0..lo5, hi0..hi5]. */
    private fun readRanges(path: String): FloatArray {
        val r = FloatArray(12)
        for (i in 0 until 6) { r[i] = -32768f; r[6 + i] = 32767f }
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("getevent", "-lp", path))
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val names = mapOf("ABS_X" to 0, "ABS_Y" to 1, "ABS_Z" to 2, "ABS_RX" to 3, "ABS_RY" to 4, "ABS_RZ" to 5)
            val re = Regex("(ABS_\\w+)\\s*:\\s*(?:value\\s+-?\\d+,\\s*)?min\\s+(-?\\d+),\\s*max\\s+(-?\\d+)")
            for (m in re.findAll(text)) {
                val i = names[m.groupValues[1]] ?: continue
                r[i] = m.groupValues[2].toFloat()
                r[6 + i] = m.groupValues[3].toFloat()
            }
        } catch (_: Throwable) {
        }
        return r
    }

    private fun readLoop(ins: FileInputStream, kind: Int, ranges: FloatArray?) {
        val rec = if (Process.is64Bit()) 24 else 16
        val buf = ByteArray(rec * 64)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        var dx = 0
        var dy = 0
        try {
            while (running) {
                val n = ins.read(buf)
                if (n <= 0) break
                var off = 0
                while (off + rec <= n) {
                    val type = bb.getShort(off + rec - 8).toInt() and 0xFFFF
                    val code = bb.getShort(off + rec - 6).toInt() and 0xFFFF
                    val value = bb.getInt(off + rec - 4)
                    off += rec
                    val c = cb ?: continue
                    if (kind == 1) {
                        if (type == 2 && code == 0) dx += value
                        else if (type == 2 && code == 1) dy += value
                        else if (type == 0) {
                            if (dx != 0 || dy != 0) { c.onRel(dx, dy); dx = 0; dy = 0 }
                        } else if (type == 1 && code in 272..274) {
                            c.onButton(code, value != 0)
                        }
                    } else if (kind == 2 && ranges != null && type == 3 && code in 0..5) {
                        val lo = ranges[code]
                        val hi = ranges[6 + code]
                        val mid = (lo + hi) / 2f
                        val half = (hi - lo) / 2f
                        if (half > 0f) c.onAxis(code, ((value - mid) / half).coerceIn(-1f, 1f))
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
