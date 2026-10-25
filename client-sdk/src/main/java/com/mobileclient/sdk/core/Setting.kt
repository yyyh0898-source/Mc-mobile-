package com.mobileclient.sdk.core

/** A user-editable value of a [ClientModule]. Persisted as JSON by [ModuleManager]. */
sealed class Setting(val key: String, val label: String) {
    /** Hidden settings are saved but never shown in the menu (HUD positions, ...). */
    var hidden: Boolean = false

    private val listeners = ArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    protected fun fireChanged() { for (l in listeners) l() }

    open val persistent: Boolean get() = true
    abstract fun toJson(): Any
    abstract fun fromJson(o: Any?)
}

class BoolSetting(key: String, label: String, default: Boolean) : Setting(key, label) {
    var value: Boolean = default
        set(v) { if (field != v) { field = v; fireChanged() } }

    override fun toJson(): Any = value
    override fun fromJson(o: Any?) { if (o is Boolean) value = o }
}

class FloatSetting(
    key: String,
    label: String,
    default: Float,
    val min: Float,
    val max: Float,
    val step: Float = 0f,
    val unit: String = "",
) : Setting(key, label) {
    var value: Float = default
        set(v) {
            var n = v.coerceIn(min, max)
            if (step > 0f) n = (Math.round((n - min) / step) * step + min).coerceIn(min, max)
            if (n != field) { field = n; fireChanged() }
        }

    override fun toJson(): Any = value.toDouble()
    override fun fromJson(o: Any?) { if (o is Number) value = o.toFloat() }
}

/** ARGB colour. */
class ColorSetting(key: String, label: String, default: Int) : Setting(key, label) {
    var value: Int = default
        set(v) { if (field != v) { field = v; fireChanged() } }

    override fun toJson(): Any = value
    override fun fromJson(o: Any?) { if (o is Number) value = o.toInt() }
}

/** One value out of a list. [options] may be replaced at runtime (e.g. a list of files). */
class ChoiceSetting(
    key: String,
    label: String,
    var options: List<String>,
    default: String,
) : Setting(key, label) {
    var value: String = default
        set(v) { if (field != v) { field = v; fireChanged() } }

    override fun toJson(): Any = value
    override fun fromJson(o: Any?) { if (o is String) value = o }
}

/** Free text, edited through a dialog. */
class TextSetting(key: String, label: String, default: String, val hint: String = "") : Setting(key, label) {
    var value: String = default
        set(v) { if (field != v) { field = v; fireChanged() } }

    override fun toJson(): Any = value
    override fun fromJson(o: Any?) { if (o is String) value = o }
}

/** A button inside the settings page. Not persisted. */
class ActionSetting(
    label: String,
    val buttonText: String = label,
    val action: () -> Unit,
) : Setting("action_$label", label) {
    override val persistent: Boolean get() = false
    override fun toJson(): Any = 0
    override fun fromJson(o: Any?) {}
}

/** Read-only information line. Not persisted. */
class InfoSetting(label: String, val text: () -> String) : Setting("info_$label", label) {
    override val persistent: Boolean get() = false
    override fun toJson(): Any = 0
    override fun fromJson(o: Any?) {}
}

object Palette {
    val WHITE: Int = 0xFFFFFFFF.toInt()
    val COLORS: IntArray = intArrayOf(
        WHITE,
        0xFF3A9BDC.toInt(), // blue
        0xFF4CD08C.toInt(), // green
        0xFFF2C94C.toInt(), // yellow
        0xFFF2994A.toInt(), // orange
        0xFFEB5757.toInt(), // red
        0xFFBB6BD9.toInt(), // purple
        0xFFFF7EB6.toInt(), // pink
        0xFF9AA5B1.toInt(), // grey
        0xFF000000.toInt(), // black
    )
}
