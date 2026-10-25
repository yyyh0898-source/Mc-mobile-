package com.mobileclient.sdk.modules

import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.core.ActionSetting
import com.mobileclient.sdk.core.BoolSetting
import com.mobileclient.sdk.core.Category
import com.mobileclient.sdk.core.ClientModule
import com.mobileclient.sdk.core.ColorSetting
import com.mobileclient.sdk.core.FloatSetting
import com.mobileclient.sdk.core.InfoSetting
import com.mobileclient.sdk.ui.Theme

/** Always-on module that holds the client's own settings (menu button, popups, theme). */
class ClientSettings : ClientModule(
    "client", "Client", "Menu button, popups and theme.", Category.CLIENT
) {
    override val toggleable: Boolean get() = false

    val accent = add(ColorSetting("accent", "Accent colour", Theme.DEFAULT_ACCENT))
    val showButton = add(BoolSetting("show_button", "Show menu button", true))
    val buttonSize = add(FloatSetting("button_size", "Button size", 44f, 32f, 72f, 2f, "dp"))
    val buttonOpacity = add(FloatSetting("button_opacity", "Button opacity", 0.9f, 0.2f, 1f, 0.05f))
    val popups = add(BoolSetting("popups", "Show module ON/OFF popups", true))
    val buttonX = add(FloatSetting("bx", "Button X", 0.01f, 0f, 1f)).also { it.hidden = true }
    val buttonY = add(FloatSetting("by", "Button Y", 0.42f, 0f, 1f)).also { it.hidden = true }

    private val reset = add(ActionSetting("Reset menu button position") {
        buttonX.value = 0.01f
        buttonY.value = 0.42f
        MobileClient.refreshButton()
    })
    private val about = add(InfoSetting("About") {
        "Mobile Client ${MobileClient.VERSION} - unofficial; not affiliated with Mojang, Microsoft or MarkClient."
    })

    init {
        accent.addListener {
            Theme.accent = accent.value
            MobileClient.refreshTheme()
            MobileClient.refreshButton()
        }
        showButton.addListener { MobileClient.refreshButton() }
        buttonSize.addListener { MobileClient.refreshButton() }
        buttonOpacity.addListener { MobileClient.refreshButton() }
    }
}
