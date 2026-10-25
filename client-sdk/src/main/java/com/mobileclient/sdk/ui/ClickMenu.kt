package com.mobileclient.sdk.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mobileclient.sdk.MobileClient
import com.mobileclient.sdk.OverlayHost
import com.mobileclient.sdk.core.ActionSetting
import com.mobileclient.sdk.core.BoolSetting
import com.mobileclient.sdk.core.Category
import com.mobileclient.sdk.core.ChoiceSetting
import com.mobileclient.sdk.core.ClientModule
import com.mobileclient.sdk.core.ColorSetting
import com.mobileclient.sdk.core.FloatSetting
import com.mobileclient.sdk.core.InfoSetting
import com.mobileclient.sdk.core.Palette
import com.mobileclient.sdk.core.ServerPolicy
import com.mobileclient.sdk.core.Setting
import com.mobileclient.sdk.core.TextSetting

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The click menu: sidebar (Visual / HUD / Client / Profiles) + module cards. Tapping a card opens
 * that module's settings page; going back returns to the same scroll position.
 * Built from plain Views so it works inside any host process (no AppCompat / Compose needed).
 */
internal class ClickMenu(
    private val activity: Activity,
    private val host: OverlayHost,
) : FrameLayout(activity) {

    private enum class Tab(val title: String, val category: Category?) {
        VISUAL("Visual", Category.VISUAL),
        HUD("HUD", Category.HUD),
        CLIENT("Client", Category.CLIENT),
        PROFILES("Profiles", null),
    }

    private val mm = MobileClient.modules
    private var tab = Tab.VISUAL
    private var openModule: ClientModule? = null
    private val scrollMemory = HashMap<String, Int>()
    private val navItems = LinkedHashMap<Tab, TextView>()

    private lateinit var titleView: TextView
    private lateinit var backView: TextView
    private lateinit var actions: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout

    init {
        setBackgroundColor(Theme.scrim)
        setOnClickListener { host.closeMenu() } // tap outside the panel closes the menu
        build()
        showTab(Tab.VISUAL)
    }

    /** Rebuild after the accent colour changed. */
    fun applyTheme() {
        saveScroll()
        removeAllViews()
        navItems.clear()
        build()
        val m = openModule
        if (m != null) showModule(m, save = false) else showTab(tab, save = false)
    }

    // ---- frame ---------------------------------------------------------------------

    private fun build() {
        val dm = resources.displayMetrics
        val pw = minOf((dm.widthPixels * 0.94f).toInt(), dp(780))
        val ph = minOf((dm.heightPixels * 0.90f).toInt(), dp(440))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.round(Theme.panel, dp(14).toFloat(), Theme.stroke, dp(1))
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            isClickable = true // swallow taps so they do not reach the "close" listener
        }
        addView(panel, FrameLayout.LayoutParams(pw, ph, Gravity.CENTER))

        // sidebar
        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.sidebar)
            setPadding(dp(12), dp(16), dp(12), dp(12))
        }
        panel.addView(side, LinearLayout.LayoutParams(dp(172), MATCH))
        side.addView(Ui.text(context, "MOBILE CLIENT", 14f, Theme.accent, true).apply { letterSpacing = 0.12f })
        side.addView(Ui.text(context, "for Minecraft Bedrock", 10f, Theme.muted).apply { setPadding(0, 0, 0, dp(14)) })
        for (t in Tab.values()) {
            val item = Ui.text(context, t.title, 14f, Theme.muted, true).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { showTab(t) }
            }
            navItems[t] = item
            side.addView(item, lin(MATCH, dp(40)).apply { bottomMargin = dp(4) })
        }
        side.addView(View(context), lin(1, 0, 1f))
        side.addView(Ui.text(context, "v${MobileClient.VERSION} - unofficial client", 9f, Theme.muted))

        // content
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        panel.addView(content, lin(0, MATCH, 1f))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backView = Ui.text(context, "\u2039", 30f, Theme.text, true).apply {
            setPadding(0, 0, dp(12), dp(4))
            visibility = View.GONE
            setOnClickListener {
                openModule = null
                showTab(tab, save = false)
            }
        }
        titleView = Ui.text(context, "", 20f, Theme.text, true)
        actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val close = Ui.text(context, "\u00D7", 30f, Theme.muted, true).apply {
            setPadding(dp(14), 0, dp(4), dp(4))
            setOnClickListener { host.closeMenu() }
        }
        header.addView(backView)
        header.addView(titleView, lin(0, WRAP, 1f))
        header.addView(actions)
        header.addView(close)
        content.addView(header, lin(MATCH, WRAP).apply { bottomMargin = dp(10) })

        scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(scroll, lin(MATCH, 0, 1f))
    }

    // ---- navigation ----------------------------------------------------------------

    private fun scrollKey(): String = openModule?.let { "m:${it.id}" } ?: "t:${tab.name}"
    private fun saveScroll() { scrollMemory[scrollKey()] = scroll.scrollY }
    private fun restoreScroll() {
        val y = scrollMemory[scrollKey()] ?: 0
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun refreshNav() {
        for ((t, v) in navItems) {
            val sel = t == tab
            v.setTextColor(if (sel) Theme.text else Theme.muted)
            v.background = if (sel) Ui.round(Theme.withAlpha(Theme.accent, 0x33), dp(8).toFloat()) else null
        }
    }

    private fun showTab(t: Tab, save: Boolean = true) {
        if (save) saveScroll()
        tab = t
        openModule = null
        refreshNav()
        backView.visibility = View.GONE
        titleView.text = t.title
        actions.removeAllViews()
        body.removeAllViews()
        val cat = t.category
        if (cat == null) buildProfiles() else buildModuleList(cat)
        if (t == Tab.HUD) actions.addView(pill("Edit HUD") { host.enterHudEdit() })
        restoreScroll()
    }

    private fun showModule(m: ClientModule, save: Boolean = true) {
        if (save) saveScroll()
        openModule = m
        m.onSettingsOpened()
        refreshNav()
        backView.visibility = View.VISIBLE
        titleView.text = m.name
        actions.removeAllViews()
        body.removeAllViews()
        if (m.toggleable) actions.addView(moduleSwitch(m))
        body.addView(Ui.text(context, m.description, 12f, Theme.muted).apply { setPadding(0, 0, 0, dp(10)) })
        for (s in m.visibleSettings) body.addView(settingRow(s), lin(MATCH, WRAP).apply { bottomMargin = dp(8) })
        restoreScroll()
    }

    // ---- module list ---------------------------------------------------------------

    private fun buildModuleList(cat: Category) {
        val list = mm.inCategory(cat)
        if (list.isEmpty()) {
            body.addView(Ui.text(context, "No modules in this category yet.", 13f, Theme.muted))
            return
        }
        for (m in list) body.addView(moduleCard(m), lin(MATCH, WRAP).apply { bottomMargin = dp(8) })
    }

    private fun moduleSwitch(m: ClientModule): SwitchView {
        val s = SwitchView(context)
        s.setState(m.enabled, false)
        s.onToggle = { want ->
            m.setEnabled(want)
            if (m.enabled != want) s.setState(m.enabled) // blocked by server policy
        }
        return s
    }

    private fun moduleCard(m: ClientModule): View {
        val allowed = ServerPolicy.isAllowed(m)
        val hasSettings = m.visibleSettings.isNotEmpty()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = Ui.round(Theme.card, dp(10).toFloat(), Theme.stroke, dp(1))
            alpha = if (allowed) 1f else 0.55f
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(context, m.name, 15f, Theme.text, true))
        col.addView(Ui.text(context, m.description, 12f, Theme.muted).apply { setPadding(0, dp(2), 0, 0) })
        card.addView(col, lin(0, WRAP, 1f))

        if (!allowed) {
            card.addView(Ui.text(context, "LOCKED", 10f, Theme.danger, true).apply { setPadding(dp(8), 0, dp(8), 0) })
        }
        val sw: SwitchView? = if (m.toggleable) moduleSwitch(m) else null
        if (sw != null) card.addView(sw)
        if (hasSettings) {
            card.addView(Ui.text(context, "\u203A", 24f, Theme.muted, true).apply { setPadding(dp(10), 0, dp(2), dp(2)) })
        }
        card.setOnClickListener { if (hasSettings) showModule(m) else sw?.performClick() }
        return card
    }

    // ---- setting rows --------------------------------------------------------------

    private fun settingRow(s: Setting): View = when (s) {
        is BoolSetting -> boolRow(s)
        is FloatSetting -> floatRow(s)
        is ColorSetting -> colorRow(s)
        is ChoiceSetting -> choiceRow(s)
        is TextSetting -> textRow(s)
        is ActionSetting -> pill(s.buttonText, filled = true) { s.action() }
        is InfoSetting -> row().apply {
            addView(Ui.text(context, s.label, 12f, Theme.muted))
            addView(Ui.text(context, s.text(), 12f, Theme.text).apply { setPadding(0, dp(3), 0, 0) })
        }
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(Theme.card, dp(10).toFloat(), Theme.stroke, dp(1))
        setPadding(dp(14), dp(11), dp(14), dp(11))
    }

    private fun label(s: String): TextView = Ui.text(context, s, 14f, Theme.text)

    private fun boolRow(s: BoolSetting): View {
        val r = row().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sw = SwitchView(context)
        sw.setState(s.value, false)
        sw.onToggle = { s.value = it }
        r.addView(label(s.label), lin(0, WRAP, 1f))
        r.addView(sw)
        r.setOnClickListener { sw.performClick() }
        return r
    }

    private fun fmt(s: FloatSetting): String {
        val v = s.value
        val whole = s.step >= 1f || v == Math.rint(v.toDouble()).toFloat()
        val num = if (whole) "%.0f".format(v) else "%.2f".format(v)
        return if (s.unit.isEmpty()) num else "$num ${s.unit}"
    }

    private fun floatRow(s: FloatSetting): View {
        val r = row()
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val value = Ui.text(context, fmt(s), 13f, Theme.accent, true)
        top.addView(label(s.label), lin(0, WRAP, 1f))
        top.addView(value)
        val slider = SliderView(context, s.min, s.max, s.step)
        slider.value = s.value
        slider.onChange = {
            s.value = it
            value.text = fmt(s)
        }
        r.addView(top, lin(MATCH, WRAP))
        r.addView(slider, lin(MATCH, WRAP))
        return r
    }

    private fun colorRow(s: ColorSetting): View {
        val r = row()
        r.addView(label(s.label), lin(MATCH, WRAP).apply { bottomMargin = dp(8) })
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val dots = ArrayList<Pair<View, Int>>()
        for (col in Palette.COLORS) {
            val dot = View(context)
            dots.add(dot to col)
            dot.setOnClickListener {
                s.value = col
                styleDots(dots, s.value)
            }
            line.addView(dot, lin(dp(26), dp(26)).apply { rightMargin = dp(8) })
        }
        styleDots(dots, s.value)
        r.addView(scrollRow(line), lin(MATCH, WRAP))
        return r
    }

    private fun styleDots(dots: List<Pair<View, Int>>, current: Int) {
        for ((v, c) in dots) {
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                if (c == current) setStroke(dp(2), Color.WHITE) else setStroke(dp(1), Theme.stroke)
            }
        }
    }

    private fun choiceRow(s: ChoiceSetting): View {
        val r = row()
        r.addView(label(s.label), lin(MATCH, WRAP).apply { bottomMargin = dp(8) })
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<Pair<TextView, String>>()
        for (opt in s.options) {
            val chip = Ui.text(context, opt, 13f, Theme.text).apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
            chips.add(chip to opt)
            chip.setOnClickListener {
                s.value = opt
                styleChips(chips, s.value)
            }
            line.addView(chip, lin(WRAP, WRAP).apply { rightMargin = dp(8) })
        }
        styleChips(chips, s.value)
        r.addView(scrollRow(line), lin(MATCH, WRAP))
        return r
    }

    private fun styleChips(chips: List<Pair<TextView, String>>, current: String) {
        for ((v, opt) in chips) {
            val sel = opt == current
            v.setTextColor(if (sel) Color.WHITE else Theme.muted)
            v.background = Ui.round(if (sel) Theme.accent else Theme.chip, dp(16).toFloat())
        }
    }

    private fun scrollRow(inner: View): HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(inner)
    }

    private fun textRow(s: TextSetting): View {
        val r = row().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun shown() = if (s.value.isEmpty()) "(tap to set)" else s.value
        val value = Ui.text(context, shown(), 13f, Theme.accent, true)
        r.addView(label(s.label), lin(0, WRAP, 1f))
        r.addView(value)
        r.setOnClickListener {
            prompt(s.label, s.value, s.hint) { v ->
                s.value = v
                value.text = shown()
            }
        }
        return r
    }

    // ---- profiles ------------------------------------------------------------------

    private fun buildProfiles() {
        actions.addView(pill("Save current", filled = true) {
            prompt("Profile name", "Profile ${mm.profileNames().size + 1}", "name") { n ->
                if (n.isNotEmpty()) {
                    mm.saveProfile(n)
                    showTab(Tab.PROFILES)
                }
            }
        })
        val names = mm.profileNames()
        if (names.isEmpty()) {
            body.addView(Ui.text(context, "No profiles yet. Save your current modules and settings to switch setups quickly.", 13f, Theme.muted))
            return
        }
        for (n in names) {
            val r = row().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            r.addView(Ui.text(context, n, 14f, Theme.text, true), lin(0, WRAP, 1f))
            r.addView(pill("Load") {
                mm.silent = true
                try { mm.loadProfile(n) } finally { mm.silent = false }
                host.closeMenu()
                host.popup("Profile loaded: $n")
            }, lin(WRAP, WRAP).apply { rightMargin = dp(8) })
            r.addView(pill("Delete", danger = true) {
                mm.deleteProfile(n)
                showTab(Tab.PROFILES)
            })
            body.addView(r, lin(MATCH, WRAP).apply { bottomMargin = dp(8) })
        }
    }

    // ---- small helpers -------------------------------------------------------------

    private fun pill(text: String, filled: Boolean = false, danger: Boolean = false, onClick: () -> Unit): TextView {
        val base = if (danger) Theme.danger else Theme.accent
        return Ui.text(context, text, 13f, if (filled) Color.WHITE else base, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = Ui.round(if (filled) base else Theme.withAlpha(base, 0x2E), dp(8).toFloat())
            setOnClickListener { onClick() }
        }
    }

    private fun prompt(title: String, initial: String, hintText: String, onOk: (String) -> Unit) {
        val input = EditText(activity).apply {
            setText(initial)
            hint = hintText
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lin(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun dp(v: Int): Int = Ui.dp(context, v.toFloat())
}
