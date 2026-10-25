package com.mobileclient.sdk.core

import android.graphics.Canvas

enum class Category(val title: String) {
    VISUAL("Visual"),
    HUD("HUD"),
    CLIENT("Client"),
}

/**
 * A feature of the client. Subclass it, add settings with [add], and register it with
 * MobileClient.registerModule(). Modules that draw on screen override [drawOverlay].
 */
abstract class ClientModule(
    val id: String,
    val name: String,
    val description: String,
    val category: Category,
) {
    val settings = ArrayList<Setting>()

    var enabled: Boolean = false
        private set

    /** false = always on, the card only opens a settings page (used by the "Client" module). */
    open val toggleable: Boolean get() = true
    open val enabledByDefault: Boolean get() = false

    /** Access level while connected to a multiplayer server (see [ServerPolicy]). */
    open val multiplayerAccess: Access get() = Access.ENABLED

    internal var toggleListener: ((ClientModule) -> Unit)? = null

    protected fun <T : Setting> add(setting: T): T {
        settings.add(setting)
        return setting
    }

    val visibleSettings: List<Setting> get() = settings.filter { !it.hidden }

    /** @return true if the state changed (false if already in that state or blocked by policy). */
    fun setEnabled(value: Boolean): Boolean {
        if (value == enabled) return false
        if (value && !ServerPolicy.isAllowed(this)) return false
        enabled = value
        if (value) onEnable() else onDisable()
        toggleListener?.invoke(this)
        return true
    }

    fun toggle(): Boolean = setEnabled(!enabled)

    open fun onEnable() {}
    open fun onDisable() {}
    /** Called when the user opens this module's settings page. */
    open fun onSettingsOpened() {}

    /** Draw on the full-screen overlay. Visual modules are drawn first, then HUD modules. */
    open fun drawOverlay(c: Canvas, vw: Int, vh: Int, density: Float) {}
}
