package com.mobileclient.sdk.modules

import android.graphics.Canvas
import android.graphics.Paint
import com.mobileclient.sdk.core.ChoiceSetting
import com.mobileclient.sdk.core.ColorSetting
import com.mobileclient.sdk.core.FloatSetting
import com.mobileclient.sdk.core.Palette
import com.mobileclient.sdk.hud.HudModule

/** Custom crosshair drawn in the centre of the screen (colour / shape / size). */
class CrosshairModule : HudModule(
    "crosshair", "Crosshair", "Custom crosshair in the screen centre.", 0.5f, 0.5f
) {
    override val draggable: Boolean get() = false

    val shape = add(ChoiceSetting("shape", "Shape", listOf("Plus", "Dot", "Circle", "Cross X"), "Plus"))
    val size = add(FloatSetting("size", "Size", 10f, 3f, 40f, 1f, "dp"))
    val thickness = add(FloatSetting("thick", "Thickness", 2f, 1f, 6f, 0.5f, "dp"))
    val gap = add(FloatSetting("gap", "Gap", 3f, 0f, 20f, 1f, "dp"))
    val color = add(ColorSetting("color", "Colour", Palette.WHITE))

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas, vw: Int, vh: Int, density: Float) {
        val cx = vw / 2f
        val cy = vh / 2f
        val sz = size.value * density
        val gp = gap.value * density
        val th = thickness.value * density
        bounds.set(cx - sz - gp, cy - sz - gp, cx + sz + gp, cy + sz + gp)

        for (pass in 0..1) { // pass 0 = dark outline, pass 1 = colour
            val outline = pass == 0
            paint.color = if (outline) 0xCC000000.toInt() else color.value
            paint.strokeWidth = if (outline) th + 2f * density else th
            paint.style = Paint.Style.STROKE
            when (shape.value) {
                "Dot" -> {
                    paint.style = Paint.Style.FILL
                    c.drawCircle(cx, cy, th * 1.5f + (if (outline) density else 0f), paint)
                }
                "Circle" -> c.drawCircle(cx, cy, sz / 2f + gp, paint)
                "Cross X" -> {
                    val k = 0.7071f
                    c.drawLine(cx - gp * k, cy - gp * k, cx - (gp + sz) * k, cy - (gp + sz) * k, paint)
                    c.drawLine(cx + gp * k, cy - gp * k, cx + (gp + sz) * k, cy - (gp + sz) * k, paint)
                    c.drawLine(cx - gp * k, cy + gp * k, cx - (gp + sz) * k, cy + (gp + sz) * k, paint)
                    c.drawLine(cx + gp * k, cy + gp * k, cx + (gp + sz) * k, cy + (gp + sz) * k, paint)
                }
                else -> { // Plus
                    c.drawLine(cx - gp - sz, cy, cx - gp, cy, paint)
                    c.drawLine(cx + gp, cy, cx + gp + sz, cy, paint)
                    c.drawLine(cx, cy - gp - sz, cx, cy - gp, paint)
                    c.drawLine(cx, cy + gp, cx, cy + gp + sz, paint)
                }
            }
        }
    }
}
