package com.mobileclient.sdk.modules

import android.graphics.Canvas
import com.mobileclient.sdk.core.Category
import com.mobileclient.sdk.core.ClientModule
import com.mobileclient.sdk.core.ColorSetting
import com.mobileclient.sdk.core.FloatSetting

/** Tints the whole screen (night / warm filter). Purely an overlay - never touches game data. */
class ScreenFilterModule : ClientModule(
    "screen_filter", "Screen Filter", "Tints the screen with a colour (warm / night filter).", Category.VISUAL
) {
    val color = add(ColorSetting("color", "Colour", 0xFFFFA040.toInt()))
    val strength = add(FloatSetting("strength", "Strength", 0.20f, 0.02f, 0.70f, 0.01f))

    override fun drawOverlay(c: Canvas, vw: Int, vh: Int, density: Float) {
        val a = (strength.value * 255).toInt()
        c.drawColor((a shl 24) or (color.value and 0x00FFFFFF))
    }
}
