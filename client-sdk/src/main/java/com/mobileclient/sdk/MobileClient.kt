package com.mobileclient.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mobileclient.sdk.core.ClientModule
import com.mobileclient.sdk.core.ModuleManager
import com.mobileclient.sdk.core.ServerPolicy
import com.mobileclient.sdk.modules.BatteryModule
import com.mobileclient.sdk.modules.ClientSettings
import com.mobileclient.sdk.modules.ClockModule
import com.mobileclient.sdk.modules.CpsModule
import com.mobileclient.sdk.modules.CrosshairModule
import com.mobileclient.sdk.modules.FpsModule
import com.mobileclient.sdk.modules.MemoryModule
import com.mobileclient.sdk.modules.PingModule
import com.mobileclient.sdk.modules.ReferenceImageModule
import com.mobileclient.sdk.modules.ScreenFilterModule

/**
 * Public entry point of the SDK.
 *
 *   Kotlin:  MobileClient.init(activity); MobileClient.openMenu()
 *   Java:    MobileClient.init(activity); MobileClient.openMenu();
 *   C++:     mc_attach(env, activity); mc_open_menu();      (see mobileclient.h)
 *
 * With the default manifest the client attaches by itself (ClientInitProvider), so for a patched
 * game you do not have to call anything.
 */
object MobileClient {
    const val VERSION = "1.0.0"

    /** Intent extra read from the game's launch intent. "vanilla" keeps the client dormant. */
    const val EXTRA_MODE = "mobileclient.mode"
    const val MODE_VANILLA = "vanilla"
    const val MODE_PATCH = "patch"

    internal const val TAG = "MobileClient"

    lateinit var appContext: Context
        private set
    lateinit var modules: ModuleManager
        private set

    /** Optional: choose which activities receive the overlay (default: manifest target_regex). */
    @Volatile var attachFilter: ((Activity) -> Boolean)? = null

    private var installed = false
    private var vanillaSession = false
    private var host: OverlayHost? = null
    private var targetRegex = Regex(".*")
    private val main = Handler(Looper.getMainLooper())

    // ---- setup ---------------------------------------------------------------------

    @JvmStatic
    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        appContext = app.applicationContext
        modules = ModuleManager(app.getSharedPreferences("mobileclient", Context.MODE_PRIVATE))
        modules.onModuleToggled = { m -> onToggled(m) }

        val builtIns: List<ClientModule> = listOf(
            ScreenFilterModule(),
            FpsModule(), CpsModule(), ClockModule(), BatteryModule(), MemoryModule(), PingModule(),
            CrosshairModule(), ReferenceImageModule(),
            ClientSettings(),
        )
        for (m in builtIns) modules.register(m)
        modules.loadSaved()

        val meta = readMeta(app)
        meta?.getString("mobileclient.target_regex")?.let { targetRegex = Regex(it) }
        if (meta?.getBoolean("mobileclient.auto_attach", true) != false) {
            app.registerActivityLifecycleCallbacks(lifecycle)
        }
    }

    /** Manual attach (use when auto_attach is disabled, or from native code via mc_attach). */
    @JvmStatic
    fun init(activity: Activity) {
        install(activity.application)
        val mode = activity.intent?.getStringExtra(EXTRA_MODE)
        if (mode != null) vanillaSession = (mode == MODE_VANILLA)
        runOnMain { if (!vanillaSession && !activity.isFinishing) attach(activity) }
    }

    // ---- public API ----------------------------------------------------------------

    @JvmStatic fun openMenu() = runOnMain { host?.openMenu() }
    @JvmStatic fun closeMenu() = runOnMain { host?.closeMenu() }
    @JvmStatic fun toggleMenu() = runOnMain { host?.toggleMenu() }

    @JvmStatic
    fun isModuleEnabled(id: String): Boolean = installed && modules.byId(id)?.enabled == true

    @JvmStatic
    fun setModuleEnabled(id: String, enabled: Boolean) = runOnMain {
        if (installed) modules.byId(id)?.setEnabled(enabled)
    }

    /** Add your own module (see ClientModule / TextHudModule). Call after install()/init(). */
    @JvmStatic
    fun registerModule(m: ClientModule) {
        check(installed) { "Call MobileClient.install() or init() first" }
        runOnMain {
            modules.register(m)
            modules.restore(m)
        }
    }

    /** Apply a server/world policy (see ServerPolicy for the JSON format). */
    @JvmStatic
    fun applyServerPolicy(json: String) {
        ServerPolicy.load(json)
        runOnMain { modules.enforcePolicy() }
    }

    // ---- internals -----------------------------------------------------------------

    internal fun refreshButton() { host?.rebuildButton() }
    internal fun refreshTheme() { host?.refreshTheme() }
    internal fun requestHudRedraw() { host?.invalidateHud() }

    private fun onToggled(m: ClientModule) {
        if (modules.silent || !m.toggleable) return
        val popups = (modules.byId("client") as? ClientSettings)?.popups?.value ?: true
        if (popups) host?.popup("${m.name}  ${if (m.enabled) "ON" else "OFF"}")
    }

    private fun shouldAttach(a: Activity): Boolean {
        if (vanillaSession) return false
        val f = attachFilter
        return if (f != null) f(a) else targetRegex.matches(a.javaClass.name)
    }

    private fun attach(a: Activity) {
        if (host?.activity === a) return
        host?.detach()
        NativeBridge.load()
        val h = OverlayHost(a)
        host = h
        h.attach()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    @Suppress("DEPRECATION")
    private fun readMeta(app: Application): Bundle? = try {
        app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
    } catch (e: Exception) {
        null
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val mode = activity.intent?.getStringExtra(EXTRA_MODE)
            if (mode != null) vanillaSession = (mode == MODE_VANILLA)
        }

        override fun onActivityResumed(activity: Activity) {
            if (shouldAttach(activity)) attach(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (host?.activity === activity) {
                host?.detach()
                host = null
            }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }
}
