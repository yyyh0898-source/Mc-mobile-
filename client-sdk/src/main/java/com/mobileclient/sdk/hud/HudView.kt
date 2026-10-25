package com.mobileclient.sdk.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.OverlayHost
import com.mobileclient.sdk.core.Category
import com.mobileclient.sdk.ui.Theme

/**
 * Full-screen, non-touchable layer above the game. Draws every enabled module's overlay.
 * In edit mode it becomes touchable so HUD elements can be dragged.
 */
internal class HudView(context: Context, private val host: OverlayHost) : View(context) {

    var editMode: Boolean = false
        set(v) {
            field = v
            dragging = null
            invalidate()
        }

    private var dragging: HudModule? = null
    private var offX = 0f
    private var offY = 0f
    private var popup: String? = null
    private var popupUntil = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val doneRect = RectF()
    private val tmp = RectF()

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 200L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    fun showPopup(text: String) {
        popup = text
        popupUntil = SystemClock.uptimeMillis() + 1600L
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val modules = MobileClient.modules.all
        if (editMode) {
            paint.style = Paint.Style.FILL
            paint.color = 0x66000000
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        for (cat in Category.values()) {
            for (m in modules) if (m.enabled && m.category == cat) m.drawOverlay(c, width, height, d)
        }
        if (editMode) drawEditUi(c, d)
        drawPopup(c, d)
    }

    private fun drawEditUi(c: Canvas, d: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * d
        paint.color = Theme.accent
        for (m in MobileClient.modules.all) {
            if (m is HudModule && m.enabled && m.draggable) {
                val b = m.bounds
                tmp.set(b.left - 3 * d, b.top - 3 * d, b.right + 3 * d, b.bottom + 3 * d)
                c.drawRoundRect(tmp, 7 * d, 7 * d, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 14f * d
        paint.color = 0xFFFFFFFF.toInt()
        c.drawText("HUD editor - drag elements to move them", width / 2f, height / 2f, paint)

        val bw = 120f * d
        val bh = 42f * d
        doneRect.set((width - bw) / 2f, height - bh - 24f * d, (width + bw) / 2f, height - 24f * d)
        paint.color = Theme.accent
        c.drawRoundRect(doneRect, 10f * d, 10f * d, paint)
        paint.color = 0xFFFFFFFF.toInt()
        c.drawText("Done", doneRect.centerX(), doneRect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPopup(c: Canvas, d: Float) {
        val text = popup ?: return
        if (SystemClock.uptimeMillis() > popupUntil) {
            popup = null
            return
        }
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 13f * d
        val w = paint.measureText(text) + 28f * d
        val h = 32f * d
        val left = (width - w) / 2f
        val top = 14f * d
        tmp.set(left, top, left + w, top + h)
        paint.style = Paint.Style.FILL
        paint.color = 0xE60F151C.toInt()
        c.drawRoundRect(tmp, h / 2f, h / 2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * d
        paint.color = Theme.accent
        c.drawRoundRect(tmp, h / 2f, h / 2f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        c.drawText(text, left + 14f * d, top + h / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editMode) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (doneRect.contains(e.x, e.y)) {
                    host.exitHudEdit()
                    return true
                }
                dragging = null
                for (m in MobileClient.modules.all.asReversed()) {
                    if (m is HudModule && m.enabled && m.draggable && m.bounds.contains(e.x, e.y)) {
                        dragging = m
                        offX = e.x - m.bounds.left
                        offY = e.y - m.bounds.top
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> dragging?.let { m ->
                m.moveTo(e.x - offX, e.y - offY, width, height)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = null
        }
        return true
    }
}
