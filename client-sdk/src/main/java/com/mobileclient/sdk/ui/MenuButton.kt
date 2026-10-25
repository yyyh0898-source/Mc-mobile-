package com.mobileclient.sdk.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.mobileclient.sdk.modules.ClientSettings

/** Round floating button with a "hamburger" icon. Tap = open menu, drag = move. */
internal class MenuButton(context: Context, private val cs: ClientSettings) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val r = w / 2f
        val a = (cs.buttonOpacity.value * 255).toInt()
        val stroke = Ui.dp(context, 2f).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Theme.withAlpha(0xFF0F151C.toInt(), a)
        c.drawCircle(r, r, r - 1f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = Theme.withAlpha(Theme.accent, a)
        c.drawCircle(r, r, r - stroke, paint)

        paint.style = Paint.Style.FILL
        paint.color = Theme.withAlpha(0xFFFFFFFF.toInt(), a)
        val lw = w * 0.38f
        val lh = w * 0.065f
        val gap = w * 0.13f
        for (i in -1..1) {
            c.drawRoundRect(r - lw / 2, r + i * gap - lh / 2, r + lw / 2, r + i * gap + lh / 2, lh / 2, lh / 2, paint)
        }
    }
}
