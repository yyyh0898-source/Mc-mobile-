package com.mobileclient.sdk.core

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class ModuleManager(private val prefs: SharedPreferences) {
    val all = ArrayList<ClientModule>()

    /** While true, toggle popups are suppressed (used when loading profiles). */
    @Volatile var silent: Boolean = false

    internal var onModuleToggled: ((ClientModule) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { saveNow() }

    fun register(m: ClientModule) {
        if (byId(m.id) != null) return
        all.add(m)
        m.toggleListener = { mod ->
            scheduleSave()
            onModuleToggled?.invoke(mod)
        }
        for (s in m.settings) s.addListener { scheduleSave() }
    }

    fun byId(id: String): ClientModule? = all.firstOrNull { it.id == id }
    fun inCategory(c: Category): List<ClientModule> = all.filter { it.category == c }

    // ---- persistence ---------------------------------------------------------------

    fun scheduleSave() {
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 400)
    }

    fun saveNow() {
        handler.removeCallbacks(saveTask)
        prefs.edit().putString(KEY_STATE, snapshot().toString()).apply()
    }

    fun snapshot(): JSONObject {
        val root = JSONObject()
        for (m in all) {
            val o = JSONObject()
            o.put("enabled", m.enabled)
            val s = JSONObject()
            for (st in m.settings) if (st.persistent) s.put(st.key, st.toJson())
            o.put("settings", s)
            root.put(m.id, o)
        }
        return root
    }

    fun apply(json: JSONObject) {
        for (m in all) {
            val o = json.optJSONObject(m.id) ?: continue
            val s = o.optJSONObject("settings")
            if (s != null) {
                for (st in m.settings) if (st.persistent && s.has(st.key)) st.fromJson(s.get(st.key))
            }
            if (m.toggleable && o.has("enabled")) m.setEnabled(o.optBoolean("enabled"))
        }
    }

    fun loadSaved() {
        val raw = prefs.getString(KEY_STATE, null)
        if (raw != null) {
            try {
                apply(JSONObject(raw))
            } catch (e: Exception) { /* corrupt state -> keep defaults */ }
        } else {
            for (m in all) if (m.enabledByDefault) m.setEnabled(true)
        }
        for (m in all) if (!m.toggleable) m.setEnabled(true)
    }

    /** Apply the stored state to a module that was registered after [loadSaved]. */
    fun restore(m: ClientModule) {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        try {
            val root = JSONObject(raw)
            val one = JSONObject()
            root.optJSONObject(m.id)?.let { one.put(m.id, it) }
            apply(one)
        } catch (e: Exception) { /* ignore */ }
        if (!m.toggleable) m.setEnabled(true)
    }

    /** Disable every enabled module that the server policy no longer allows. */
    fun enforcePolicy() {
        for (m in all.toList()) if (m.enabled && !ServerPolicy.isAllowed(m)) m.setEnabled(false)
    }

    // ---- profiles ------------------------------------------------------------------

    fun profileNames(): List<String> = profiles().keys().asSequence().toList().sorted()

    fun saveProfile(name: String) {
        val p = profiles()
        p.put(name, snapshot())
        prefs.edit().putString(KEY_PROFILES, p.toString()).apply()
    }

    fun loadProfile(name: String): Boolean {
        val snap = profiles().optJSONObject(name) ?: return false
        apply(snap)
        scheduleSave()
        return true
    }

    fun deleteProfile(name: String) {
        val p = profiles()
        p.remove(name)
        prefs.edit().putString(KEY_PROFILES, p.toString()).apply()
    }

    private fun profiles(): JSONObject = try {
        JSONObject(prefs.getString(KEY_PROFILES, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_PROFILES = "profiles"
    }
}
