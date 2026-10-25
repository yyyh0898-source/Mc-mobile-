package com.mobileclient.sdk.core

import org.json.JSONArray
import org.json.JSONObject

enum class Access { ENABLED, OPERATOR, DISABLED }

/**
 * Lets a server / world owner decide which modules players may use (same idea as MarkClient's
 * "Server settings" page). Modules that could be abused in multiplayer should override
 * [ClientModule.multiplayerAccess] with DISABLED or OPERATOR.
 *
 * JSON accepted by [load]:
 *   {"format":1,"modules":{"fps":"enabled","crosshair":"disabled","ping":["Steve","Alex"]}}
 * Values: "enabled" | "operator" | "disabled" | list of gamertags.
 *
 * Feed it from your own game integration: set [inMultiplayer] / [isOperator] / [playerName],
 * then call MobileClient.applyServerPolicy(json).
 */
object ServerPolicy {
    @Volatile var inMultiplayer: Boolean = false
    @Volatile var isOperator: Boolean = false
    @Volatile var playerName: String? = null

    private val overrides = HashMap<String, Any>() // Access or List<String>

    fun reset() { overrides.clear() }

    fun load(json: String) {
        overrides.clear()
        val mods = JSONObject(json).optJSONObject("modules") ?: return
        for (id in mods.keys()) {
            val v = mods.opt(id)
            if (v is String) {
                parse(v)?.let { overrides[id] = it }
            } else if (v is JSONArray) {
                overrides[id] = List(v.length()) { i -> v.optString(i) }
            }
        }
    }

    fun isAllowed(m: ClientModule): Boolean {
        if (!m.toggleable) return true
        val o = overrides[m.id]
        if (o != null) {
            return when (o) {
                is Access -> check(o)
                is List<*> -> playerName?.let { me -> o.any { it.toString().equals(me, ignoreCase = true) } } ?: false
                else -> true
            }
        }
        return if (inMultiplayer) check(m.multiplayerAccess) else true
    }

    private fun parse(s: String): Access? = when (s.lowercase()) {
        "enabled" -> Access.ENABLED
        "operator" -> Access.OPERATOR
        "disabled" -> Access.DISABLED
        else -> null
    }

    private fun check(a: Access): Boolean = when (a) {
        Access.ENABLED -> true
        Access.OPERATOR -> isOperator
        Access.DISABLED -> false
    }
}
