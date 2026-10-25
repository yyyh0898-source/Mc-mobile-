package com.mobileclient.sdk.modules

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import android.view.Choreographer
import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.NativeBridge
import com.mobileclient.sdk.core.BoolSetting
import com.mobileclient.sdk.core.TextSetting
import com.mobileclient.sdk.hud.TextHudModule
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FPS of the game's render loop (native eglSwapBuffers hook; Choreographer as fallback). */
class FpsModule : TextHudModule("fps", "FPS", "Frames per second of the game.", 0.01f, 0.02f) {
    override val enabledByDefault: Boolean get() = true

    private var frames = 0
    private var windowStartMs = 0L
    private var fallbackFps = 0

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!enabled) return
            val now = frameTimeNanos / 1_000_000L
            if (windowStartMs == 0L) windowStartMs = now
            frames++
            val dt = now - windowStartMs
            if (dt >= 1000L) {
                fallbackFps = (frames * 1000L / dt).toInt()
                frames = 0
                windowStartMs = now
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onEnable() {
        frames = 0
        windowStartMs = 0L
        Choreographer.getInstance().removeFrameCallback(callback)
        Choreographer.getInstance().postFrameCallback(callback)
    }

    override fun onDisable() {
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    override fun text(): String {
        val n = NativeBridge.fps()
        return "FPS: " + (if (n >= 0) n else fallbackFps)
    }
}

/** Taps per second on the game screen (first-finger taps are always counted). */
class CpsModule : TextHudModule("cps", "CPS", "Counts your screen taps per second.", 0.01f, 0.11f) {
    val rightSideOnly = add(BoolSetting("right_only", "Count right half only", true))

    private val stamps = ArrayList<Long>()
    private var spySeen = false

    /** Called by the overlay for every new finger. [fromSpy] = event came from the Window callback. */
    fun touch(x: Float, screenW: Int, fromSpy: Boolean) {
        if (!enabled) return
        if (fromSpy) spySeen = true else if (spySeen) return // avoid counting a tap twice
        if (rightSideOnly.value && x < screenW / 2f) return
        stamps.add(SystemClock.uptimeMillis())
    }

    override fun text(): String {
        val now = SystemClock.uptimeMillis()
        stamps.removeAll { now - it > 1000L }
        return "CPS: ${stamps.size}"
    }
}

class ClockModule : TextHudModule("clock", "Clock", "Shows the device time.", 0.01f, 0.20f) {
    val use24h = add(BoolSetting("h24", "24-hour format", true))
    private val f24 = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val f12 = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun text(): String = (if (use24h.value) f24 else f12).format(Date())
}

class BatteryModule : TextHudModule("battery", "Battery", "Shows the battery level.", 0.01f, 0.29f) {
    private var cached = "Battery: --"
    private var last = 0L

    override fun text(): String {
        val now = SystemClock.uptimeMillis()
        if (now - last < 5000L) return cached
        last = now
        val i: Intent? = MobileClient.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i != null) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
            cached = "Battery: $pct%" + (if (charging) " +" else "")
        }
        return cached
    }
}

class MemoryModule : TextHudModule("memory", "Memory", "RAM used by the game process.", 0.01f, 0.38f) {
    private var cached = "RAM: -- MB"
    private var last = 0L

    override fun text(): String {
        val now = SystemClock.uptimeMillis()
        if (now - last >= 2000L) {
            last = now
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            cached = "RAM: ${mi.totalPss / 1024} MB"
        }
        return cached
    }
}

/** RakNet "unconnected ping" to a Bedrock server - the same packet the server list uses. */
class PingModule : TextHudModule(
    "ping", "Server Ping", "Latency to a Bedrock server. Set its address in the settings.", 0.01f, 0.47f
) {
    val address = add(TextSetting("address", "Server address", "", "host:19132"))

    @Volatile private var ms = -1
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onEnable() {
        running = true
        worker = Thread({ loop() }, "mc-ping").apply {
            isDaemon = true
            start()
        }
    }

    override fun onDisable() {
        running = false
        worker?.interrupt()
    }

    private fun loop() {
        while (running) {
            val a = address.value.trim()
            ms = if (a.isEmpty()) -1 else {
                val idx = a.lastIndexOf(':')
                val host = if (idx > 0) a.substring(0, idx) else a
                val port = if (idx > 0) a.substring(idx + 1).toIntOrNull() ?: 19132 else 19132
                RakNet.ping(host, port)
            }
            try {
                Thread.sleep(3000L)
            } catch (e: InterruptedException) { /* woken up on disable */ }
        }
    }

    override fun text(): String = when {
        address.value.isBlank() -> "Ping: set address"
        ms < 0 -> "Ping: --"
        else -> "Ping: $ms ms"
    }
}

internal object RakNet {
    private val MAGIC = intArrayOf(
        0x00, 0xff, 0xff, 0x00, 0xfe, 0xfe, 0xfe, 0xfe,
        0xfd, 0xfd, 0xfd, 0xfd, 0x12, 0x34, 0x56, 0x78,
    )

    /** @return round-trip time in ms, or -1 on timeout / error. Must not run on the main thread. */
    fun ping(host: String, port: Int, timeoutMs: Int = 1500): Int {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val buf = ByteBuffer.allocate(33)
                buf.put(0x01.toByte())              // ID_UNCONNECTED_PING
                buf.putLong(System.currentTimeMillis())
                for (b in MAGIC) buf.put(b.toByte())
                buf.putLong(0x4D43L)                // client GUID
                val addr = InetAddress.getByName(host)
                val t0 = System.nanoTime()
                socket.send(DatagramPacket(buf.array(), buf.position(), addr, port))
                val rx = DatagramPacket(ByteArray(1500), 1500)
                socket.receive(rx)
                if (rx.length > 0 && rx.data[0] == 0x1c.toByte()) // ID_UNCONNECTED_PONG
                    ((System.nanoTime() - t0) / 1_000_000L).toInt() else -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}
