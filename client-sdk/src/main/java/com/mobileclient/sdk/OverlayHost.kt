package com.mobileclient.sdk

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import com.mobileclient.sdk.hud.HudView
import com.mobileclient.sdk.modules.ClientSettings
import com.mobileclient.sdk.modules.CpsModule
import com.mobileclient.sdk.ui.ClickMenu
import com.mobileclient.sdk.ui.MenuButton
import kotlin.math.abs

/**
 * Owns the windows the client puts on top of the game Activity.
 *
 * Every piece is its own sub-window (TYPE_APPLICATION_PANEL) of the game's window, so it works
 * even when the game uses NativeActivity and swallows input before the view hierarchy sees it,
 * and it needs no SYSTEM_ALERT_WINDOW permission.
 *
 *   hud      full screen, NOT_TOUCHABLE   - draws FPS/CPS/crosshair/... (touchable only in edit mode)
 *   watcher  1x1 px, WATCH_OUTSIDE_TOUCH  - sees the first finger of every touch (for CPS)
 *   button   small, NOT_TOUCH_MODAL       - floating menu button
 *   menu     full screen, touchable       - the click menu (only while open)
 */
internal class OverlayHost(val activity: Activity) {

    private val wm: WindowManager = activity.windowManager
    private var hud: HudView? = null
    private var hudLp: LP? = null
    private var watcher: View? = null
    private var button: MenuButton? = null
    private var menu: ClickMenu? = null
    private var spy: TouchSpy? = null
    private var attached = false
    private var dead = false

    // ---- lifecycle -----------------------------------------------------------------

    fun attach() {
        val decor = activity.window.decorView
        if (decor.isAttachedToWindow) {
            doAttach()
        } else {
            decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.post { doAttach() }
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    private fun doAttach() {
        if (attached || dead) return
        attached = true
        try {
            addHud()
            addWatcher()
            addButton()
            installTouchSpy()
        } catch (t: Throwable) {
            Log.e(MobileClient.TAG, "overlay attach failed", t)
        }
    }

    fun detach() {
        dead = true
        closeMenu()
        removeView(button); button = null
        removeView(watcher); watcher = null
        removeView(hud); hud = null
        spy?.let { s ->
            if (activity.window.callback === s) activity.window.callback = s.base
        }
        spy = null
        attached = false
    }

    private fun removeView(v: View?) {
        if (v == null) return
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) { /* already gone */ }
    }

    private fun params(w: Int, h: Int, flags: Int): LP {
        val lp = LP(w, h, LP.TYPE_APPLICATION_PANEL, flags or LP.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.title = "MobileClient"
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    // ---- windows -------------------------------------------------------------------

    private fun addHud() {
        val v = HudView(activity, this)
        val lp = params(
            LP.MATCH_PARENT, LP.MATCH_PARENT,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
        )
        wm.addView(v, lp)
        hud = v
        hudLp = lp
    }

    private fun cps(): CpsModule? = MobileClient.modules.byId("cps") as? CpsModule

    private fun addWatcher() {
        val v = object : View(activity) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    cps()?.touch(e.x, activity.resources.displayMetrics.widthPixels, false)
                }
                return false
            }
        }
        val lp = params(
            1, 1,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL or LP.FLAG_WATCH_OUTSIDE_TOUCH or LP.FLAG_LAYOUT_NO_LIMITS,
        )
        wm.addView(v, lp)
        watcher = v
    }

    /** Games that use GameActivity / views deliver touches through Window.Callback: count them there. */
    private fun installTouchSpy() {
        val w = activity.window
        val base = w.callback ?: return
        if (base is TouchSpy) return
        val s = TouchSpy(base) { x -> cps()?.touch(x, activity.resources.displayMetrics.widthPixels, true) }
        spy = s
        w.callback = s
    }

    private class TouchSpy(
        val base: Window.Callback,
        private val onDown: (Float) -> Unit,
    ) : Window.Callback by base {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val a = event.actionMasked
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                onDown(event.getX(event.actionIndex))
            }
            return base.dispatchTouchEvent(event)
        }
    }

    // ---- menu button ---------------------------------------------------------------

    private fun clientSettings(): ClientSettings? = MobileClient.modules.byId("client") as? ClientSettings

    private fun addButton() {
        val cs = clientSettings() ?: return
        if (!cs.showButton.value || dead) return
        val dm = activity.resources.displayMetrics
        val size = (cs.buttonSize.value * dm.density).toInt()
        val maxX = (dm.widthPixels - size).coerceAtLeast(1)
        val maxY = (dm.heightPixels - size).coerceAtLeast(1)

        val v = MenuButton(activity, cs)
        val lp = params(size, size, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL or LP.FLAG_LAYOUT_NO_LIMITS)
        lp.x = (cs.buttonX.value * maxX).toInt()
        lp.y = (cs.buttonY.value * maxY).toInt()
        val slop = ViewConfiguration.get(activity).scaledTouchSlop

        v.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = lp.x
                        startY = lp.y
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                        if (moved) {
                            lp.x = (startX + dx).toInt().coerceIn(0, maxX)
                            lp.y = (startY + dy).toInt().coerceIn(0, maxY)
                            try {
                                wm.updateViewLayout(view, lp)
                            } catch (ex: Exception) { /* window gone */ }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            cs.buttonX.value = lp.x / maxX.toFloat()
                            cs.buttonY.value = lp.y / maxY.toFloat()
                        } else {
                            toggleMenu()
                        }
                    }
                }
                return true
            }
        })
        wm.addView(v, lp)
        button = v
    }

    fun rebuildButton() {
        if (!attached || dead) return
        removeView(button)
        button = null
        try {
            addButton()
        } catch (t: Throwable) {
            Log.e(MobileClient.TAG, "button rebuild failed", t)
        }
    }

    // ---- menu ----------------------------------------------------------------------

    fun openMenu() {
        if (!attached || dead || menu != null) return
        exitHudEdit()
        val m = ClickMenu(activity, this)
        val lp = params(
            LP.MATCH_PARENT, LP.MATCH_PARENT,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
        )
        try {
            wm.addView(m, lp)
            menu = m
            m.alpha = 0f
            m.animate().alpha(1f).setDuration(140L).start()
        } catch (t: Throwable) {
            Log.e(MobileClient.TAG, "cannot open menu", t)
        }
    }

    fun closeMenu() {
        val m = menu ?: return
        menu = null
        removeView(m)
        MobileClient.modules.saveNow()
    }

    fun toggleMenu() {
        if (menu != null) closeMenu() else openMenu()
    }

    fun refreshTheme() {
        val m = menu ?: return
        m.post { m.applyTheme() }
    }

    // ---- HUD editor / popups -------------------------------------------------------

    fun enterHudEdit() {
        val v = hud ?: return
        val lp = hudLp ?: return
        closeMenu()
        lp.flags = lp.flags and LP.FLAG_NOT_TOUCHABLE.inv()
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) { return }
        v.editMode = true
    }

    fun exitHudEdit() {
        val v = hud ?: return
        val lp = hudLp ?: return
        if (!v.editMode) return
        v.editMode = false
        lp.flags = lp.flags or LP.FLAG_NOT_TOUCHABLE
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) { /* window gone */ }
        MobileClient.modules.saveNow()
    }

    fun popup(text: String) { hud?.showPopup(text) }
    fun invalidateHud() { hud?.invalidate() }
}
