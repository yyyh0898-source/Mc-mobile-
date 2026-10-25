package com.mobileclient.sdk.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/**
 * Colours of the menu. The accent is the brand blue (#3A9BDC) that markclient.com uses as its
 * theme colour; everything else is a dark palette in the same spirit. Tweak freely.
 */
object Theme {
    val DEFAULT_ACCENT: Int = 0xFF3A9BDC.toInt()
    @JvmField var accent: Int = DEFAULT_ACCENT

    val scrim: Int = 0xB8000000.toInt()
    val sidebar: Int = 0xFF0B1016.toInt()
    val panel: Int = 0xFF11171E.toInt()
    val card: Int = 0xFF18212B.toInt()
    val chip: Int = 0xFF222D39.toInt()
    val stroke: Int = 0xFF263241.toInt()
    val off: Int = 0xFF3A4654.toInt()
    val text: Int = 0xFFE8EEF4.toInt()
    val muted: Int = 0xFF8794A3.toInt()
    val danger: Int = 0xFFE5534B.toInt()

    fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)
}

/** Small view-building helpers shared by the menu and the launcher. */
object Ui {
    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
}
