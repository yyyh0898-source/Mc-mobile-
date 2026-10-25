package com.mobileclient.sdk.hud

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.mobileclient.sdk.core.BoolSetting
import com.mobileclient.sdk.core.Category
import com.mobileclient.sdk.core.ClientModule
import com.mobileclient.sdk.core.ColorSetting
import com.mobileclient.sdk.core.FloatSetting
import com.mobileclient.sdk.core.Palette

/** A module that draws something on the HUD layer and can be moved in the HUD editor. */
abstract class HudModule(
    id: String,
    name: String,
    description: String,
    defX: Float,
    defY: Float,
) : ClientModule(id, name, description, Category.HUD) {

    /** Position as a fraction (0..1) of the free screen space. Saved, but hidden in the menu. */
    val posX = add(FloatSetting("x", "X", defX, 0f, 1f)).also { it.hidden = true }
    val posY = add(FloatSetting("y", "Y", defY, 0f, 1f)).also { it.hidden = true }

    /** Screen rectangle of the last draw; used by the HUD editor for hit-testing. */
    val bounds = RectF()

    open val draggable: Boolean get() = true

    final override fun drawOverlay(c: Canvas, vw: Int, vh: Int, density: Float) = draw(c, vw, vh, density)

    protected abstract fun draw(c: Canvas, vw: Int, vh: Int, density: Float)

    /** Put a [w]x[h] box at posX/posY and remember it in [bounds]. */
    protected fun place(w: Float, h: Float, vw: Int, vh: Int) {
        val left = posX.value * (vw - w).coerceAtLeast(0f)
        val top = posY.value * (vh - h).coerceAtLeast(0f)
        bounds.set(left, top, left + w, top + h)
    }

    fun moveTo(left: Float, top: Float, vw: Int, vh: Int) {
        val fw = (vw - bounds.width()).coerceAtLeast(1f)
        val fh = (vh - bounds.height()).coerceAtLeast(1f)
        posX.value = left / fw
        posY.value = top / fh
    }
}

/** A HUD element that shows one line of text (FPS, CPS, clock, ...). */
abstract class TextHudModule(
    id: String,
    name: String,
    description: String,
    defX: Float,
    defY: Float,
) : HudModule(id, name, description, defX, defY) {

    val scale = add(FloatSetting("scale", "Scale", 1f, 0.5f, 2.5f, 0.05f, "x"))
    val color = add(ColorSetting("color", "Text colour", Palette.WHITE))
    val background = add(BoolSetting("bg", "Background", true))
    val bgOpacity = add(FloatSetting("bg_opacity", "Background opacity", 0.45f, 0f, 1f, 0.05f))
    val shadow = add(BoolSetting("shadow", "Text shadow", true))

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    abstract fun text(): String

    override fun draw(c: Canvas, vw: Int, vh: Int, density: Float) {
        val s = scale.value * density
        textPaint.textSize = 14f * s
        val txt = text()
        val padX = 8f * s
        val padY = 5f * s
        val fm = textPaint.fontMetrics
        val w = textPaint.measureText(txt) + padX * 2
        val h = (fm.descent - fm.ascent) + padY * 2
        place(w, h, vw, vh)
        if (background.value) {
            bgPaint.color = (bgOpacity.value * 255).toInt() shl 24
            c.drawRoundRect(bounds, 6f * s, 6f * s, bgPaint)
        }
        textPaint.color = color.value
        if (shadow.value) textPaint.setShadowLayer(2f * s, 1f * s, 1f * s, 0xAA000000.toInt())
        else textPaint.clearShadowLayer()
        c.drawText(txt, bounds.left + padX, bounds.top + padY - fm.ascent, textPaint)
    }
}
