package com.mobileclient.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.ui.Theme
import com.mobileclient.sdk.ui.Ui

private const val DEFAULT_PKG = "com.mojang.minecraftpe"
private const val PROVIDER = "com.mobileclient.sdk.ClientInitProvider"
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Launcher: pick "Minecraft PE" (original game, client stays dormant) or "Patch" (game with the
 * client menu + HUD) and press Play. Both start the same installed Minecraft; the choice is passed
 * as an Intent extra that the SDK inside the patched game reads (MobileClient.EXTRA_MODE).
 */
class LauncherActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private var patchMode: Boolean
        get() = prefs.getBoolean("patch_mode", true)
        set(v) { prefs.edit().putBoolean("patch_mode", v).apply() }

    private var gamePkg: String
        get() = prefs.getString("pkg", DEFAULT_PKG) ?: DEFAULT_PKG
        set(v) { prefs.edit().putString("pkg", v).apply() }

    private lateinit var statusView: TextView
    private lateinit var pkgView: TextView
    private lateinit var warnView: TextView
    private lateinit var peTile: LinearLayout
    private lateinit var patchTile: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.sidebar
        window.navigationBarColor = Theme.sidebar
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---- UI ------------------------------------------------------------------------

    private fun buildUi(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(Theme.sidebar)
            isFillViewport = true
        }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }
        root.addView(outer, ViewGroup.LayoutParams(MATCH, WRAP))

        val maxW = minOf(resources.displayMetrics.widthPixels - dp(40), dp(520))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(col, LinearLayout.LayoutParams(maxW, WRAP))

        col.addView(Ui.text(this, "MOBILE CLIENT", 24f, Theme.accent, true).apply { letterSpacing = 0.12f })
        col.addView(
            Ui.text(this, "Quality-of-life client for Minecraft Bedrock on Android", 13f, Theme.muted)
                .apply { setPadding(0, dp(4), 0, dp(20)) }
        )

        // game status
        val status = card()
        statusView = Ui.text(this, "", 14f, Theme.text, true)
        pkgView = Ui.text(this, "", 12f, Theme.muted).apply { setPadding(0, dp(4), 0, 0) }
        val change = Ui.text(this, "Change package", 12f, Theme.accent, true).apply {
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { askPackage() }
        }
        status.addView(statusView)
        status.addView(pkgView)
        status.addView(change)
        col.addView(status, lin(MATCH, WRAP, bottom = 14))

        // mode tiles
        col.addView(
            Ui.text(this, "START AS", 11f, Theme.muted, true).apply { letterSpacing = 0.1f; setPadding(0, 0, 0, dp(8)) }
        )
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        peTile = tile("Minecraft PE", "Original game.\nClient stays off.") { patchMode = false; refresh() }
        patchTile = tile("Patch", "Game with the client menu & HUD.") { patchMode = true; refresh() }
        tiles.addView(peTile, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        tiles.addView(patchTile, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) })
        col.addView(tiles, lin(MATCH, WRAP, bottom = 10))

        warnView = Ui.text(this, "", 12f, Theme.danger)
        col.addView(warnView, lin(MATCH, WRAP, bottom = 10))

        // play
        val play = Ui.text(this, "PLAY", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = Ui.round(Theme.accent, dp(12).toFloat())
            letterSpacing = 0.15f
            setOnClickListener { play() }
        }
        col.addView(play, lin(MATCH, dp(54), bottom = 18))

        col.addView(
            Ui.text(
                this,
                "In game: tap the floating \u2630 button to open the click menu.\n" +
                    "If Minecraft is already running, close it first to switch mode.",
                12f, Theme.muted,
            ).apply { setPadding(0, 0, 0, dp(14)) }
        )
        col.addView(
            Ui.text(
                this,
                "Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. " +
                    "Not affiliated with MarkClient.",
                10f, Theme.muted,
            )
        )
        return root
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(Theme.card, dp(12).toFloat(), Theme.stroke, dp(1))
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun tile(title: String, desc: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(Ui.text(context, title, 16f, Theme.text, true))
            addView(Ui.text(context, desc, 12f, Theme.muted).apply { setPadding(0, dp(4), 0, 0) })
            setOnClickListener { onClick() }
        }

    private fun lin(w: Int, h: Int, weight: Float = 0f, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { bottomMargin = dp(bottom) }

    private fun dp(v: Int): Int = Ui.dp(this, v.toFloat())

    // ---- logic ---------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun refresh() {
        val info = try {
            packageManager.getPackageInfo(gamePkg, PackageManager.GET_PROVIDERS)
        } catch (e: Exception) {
            null
        }
        val patched = info?.providers?.any { it.name == PROVIDER } == true

        pkgView.text = "Package: $gamePkg"
        if (info == null) {
            statusView.text = "Minecraft not found"
            statusView.setTextColor(Theme.danger)
        } else {
            statusView.text = "Minecraft ${info.versionName ?: ""}  \u00B7  " +
                (if (patched) "client patch detected" else "original build")
            statusView.setTextColor(Theme.text)
        }

        val r = dp(12).toFloat()
        peTile.background = Ui.round(Theme.card, r, if (!patchMode) Theme.accent else Theme.stroke, dp(if (!patchMode) 2 else 1))
        patchTile.background = Ui.round(Theme.card, r, if (patchMode) Theme.accent else Theme.stroke, dp(if (patchMode) 2 else 1))

        if (patchMode && info != null && !patched) {
            warnView.text = "This build has no client patch yet - the game will start without the client. " +
                "Patch the APK with tools/patch_apk.sh first."
            warnView.visibility = View.VISIBLE
        } else {
            warnView.visibility = View.GONE
        }
    }

    private fun play() {
        val intent = packageManager.getLaunchIntentForPackage(gamePkg)
        if (intent == null) {
            Toast.makeText(this, "Minecraft ($gamePkg) is not installed", Toast.LENGTH_LONG).show()
            return
        }
        intent.putExtra(MobileClient.EXTRA_MODE, if (patchMode) MobileClient.MODE_PATCH else MobileClient.MODE_VANILLA)
        startActivity(intent)
    }

    private fun askPackage() {
        val input = EditText(this).apply {
            setText(gamePkg)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Minecraft package name")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val v = input.text.toString().trim()
                gamePkg = if (v.isEmpty()) DEFAULT_PKG else v
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
