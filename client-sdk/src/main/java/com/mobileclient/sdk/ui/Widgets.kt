package com.mobileclient.sdk.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Animated on/off switch in the accent colour. */
internal class SwitchView(context: Context) : View(context) {
    var onToggle: ((Boolean) -> Unit)? = null
    var checked: Boolean = false
        private set

    private var t = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argb = ArgbEvaluator()

    init { isClickable = true }

    fun setState(on: Boolean, animate: Boolean = true) {
        checked = on
        anim?.cancel()
        val target = if (on) 1f else 0f
        if (!animate) {
            t = target
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(t, target).apply {
            duration = 140L
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 44f), Ui.dp(context, 24f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = argb.evaluate(t, Theme.off, Theme.accent) as Int
        c.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
        val r = h / 2f - Ui.dp(context, 3f)
        paint.color = Color.WHITE
        c.drawCircle(h / 2f + t * (w - h), h / 2f, r, paint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        setState(!checked)
        onToggle?.invoke(checked)
        return true
    }
}

/** Horizontal slider with optional snapping step. */
internal class SliderView(
    context: Context,
    private val min: Float,
    private val max: Float,
    private val step: Float,
) : View(context) {
    var onChange: ((Float) -> Unit)? = null
    var value: Float = min
        set(v) {
            field = v.coerceIn(min, max)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Ui.dp(context, 30f))
    }

    override fun onDraw(c: Canvas) {
        val pad = Ui.dp(context, 10f).toFloat()
        val cy = height / 2f
        val th = Ui.dp(context, 4f).toFloat()
        val span = (width - 2 * pad).coerceAtLeast(1f)
        val frac = if (max > min) (value - min) / (max - min) else 0f
        paint.color = Theme.off
        c.drawRoundRect(pad, cy - th / 2, pad + span, cy + th / 2, th / 2, th / 2, paint)
        paint.color = Theme.accent
        c.drawRoundRect(pad, cy - th / 2, pad + span * frac, cy + th / 2, th / 2, th / 2, paint)
        paint.color = Color.WHITE
        c.drawCircle(pad + span * frac, cy, Ui.dp(context, 8f).toFloat(), paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun update(x: Float) {
        val pad = Ui.dp(context, 10f).toFloat()
        val span = (width - 2 * pad).coerceAtLeast(1f)
        val frac = ((x - pad) / span).coerceIn(0f, 1f)
        var v = min + frac * (max - min)
        if (step > 0f) v = Math.round((v - min) / step) * step + min
        v = v.coerceIn(min, max)
        if (v != value) {
            value = v
            onChange?.invoke(v)
        }
    }
}
