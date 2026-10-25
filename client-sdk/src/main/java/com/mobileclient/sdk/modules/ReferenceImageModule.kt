package com.mobileclient.sdk.modules

import android.annotation.TargetApi
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.core.ChoiceSetting
import com.mobileclient.sdk.core.FloatSetting
import com.mobileclient.sdk.core.InfoSetting
import com.mobileclient.sdk.hud.HudModule
import java.io.File

/**
 * Shows a picture / GIF / animated WebP over the game (build references, guides, ...).
 * Put files in  <Android/media/<package>/MobileClient/reference/  (the path is shown in the settings).
 */
class ReferenceImageModule : HudModule(
    "reference_image", "Reference Image", "Show a picture or GIF over the game.", 0.72f, 0.04f
) {
    val file = add(ChoiceSetting("file", "Image", listOf(NONE), NONE))
    val widthPct = add(FloatSetting("width", "Width", 25f, 5f, 100f, 1f, "%"))
    val opacity = add(FloatSetting("opacity", "Opacity", 0.85f, 0.1f, 1f, 0.05f))
    private val folderInfo = add(InfoSetting("Folder") { folder().absolutePath })

    private var drawable: Drawable? = null
    private var loadedName: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val redraw = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) { MobileClient.requestHudRedraw() }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, who, `when`)
        }
        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what, who)
        }
    }

    fun folder(): File {
        val ctx = MobileClient.appContext
        @Suppress("DEPRECATION")
        val base = ctx.externalMediaDirs.firstOrNull() ?: ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "MobileClient/reference").also { it.mkdirs() }
    }

    override fun onEnable() { refresh() }
    override fun onSettingsOpened() { refresh() }

    override fun onDisable() {
        release()
        loadedName = null
    }

    private fun refresh() {
        val names = folder().listFiles()
            ?.filter { it.isFile && isImage(it.name) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        file.options = listOf(NONE) + names
    }

    private fun isImage(n: String): Boolean {
        val l = n.lowercase()
        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") ||
            l.endsWith(".webp") || l.endsWith(".gif") || l.endsWith(".bmp")
    }

    override fun draw(c: Canvas, vw: Int, vh: Int, density: Float) {
        val name = file.value
        if (name == NONE) {
            bounds.setEmpty()
            return
        }
        if (name != loadedName) load(name)
        val dr = drawable ?: return
        val iw = dr.intrinsicWidth.coerceAtLeast(1).toFloat()
        val ih = dr.intrinsicHeight.coerceAtLeast(1).toFloat()
        val w = vw * widthPct.value / 100f
        val h = w * ih / iw
        place(w, h, vw, vh)
        dr.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        dr.alpha = (opacity.value * 255).toInt()
        dr.draw(c)
    }

    private fun load(name: String) {
        loadedName = name
        release()
        val f = File(folder(), name)
        if (!f.isFile) return
        try {
            val d: Drawable? = if (Build.VERSION.SDK_INT >= 28) decodeModern(f) else decodeLegacy(f)
            d?.callback = redraw
            drawable = d
        } catch (t: Throwable) {
            Log.w(MobileClient.TAG, "cannot load $name: $t")
        }
    }

    private fun release() {
        val d = drawable ?: return
        d.callback = null
        if (Build.VERSION.SDK_INT >= 28) stopAnimation(d)
        drawable = null
    }

    @TargetApi(28)
    private fun stopAnimation(d: Drawable) {
        if (d is AnimatedImageDrawable) d.stop()
    }

    @TargetApi(28)
    private fun decodeModern(f: File): Drawable {
        val dr = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f)) { decoder, info, _ ->
            val s = info.size
            val big = maxOf(s.width, s.height)
            if (big > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / big
                decoder.setTargetSize(maxOf(1, (s.width * k).toInt()), maxOf(1, (s.height * k).toInt()))
            }
        }
        if (dr is AnimatedImageDrawable) {
            dr.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            dr.start()
        }
        return dr
    }

    private fun decodeLegacy(f: File): Drawable? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return BitmapDrawable(MobileClient.appContext.resources, bmp)
    }

    private companion object {
        const val NONE = "(none)"
        const val MAX_SIDE = 1600
    }
}
