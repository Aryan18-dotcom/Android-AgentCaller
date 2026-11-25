package com.aryan.directsim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Storage {
    private const val PREF = "assistant_store"
    private const val ITEMS = "items"
    private const val AGENT = "agent_enabled"

    fun enabled(context: Context) = context.getSharedPreferences(PREF, 0).getBoolean(AGENT, false)
    fun setEnabled(context: Context, value: Boolean) = context.getSharedPreferences(PREF, 0).edit().putBoolean(AGENT, value).apply()

    fun add(context: Context, type: String, body: String) {
        val prefs = context.getSharedPreferences(PREF, 0)
        val old = try { JSONArray(prefs.getString(ITEMS, "[]")) } catch (_: Exception) { JSONArray() }
        val item = JSONObject().put("type", type).put("body", body)
            .put("time", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
        val next = JSONArray().put(item)
        for (i in 0 until old.length()) next.put(old.getJSONObject(i))
        prefs.edit().putString(ITEMS, next.toString()).apply()
    }

    fun all(context: Context): List<Triple<String, String, String>> {
        val array = try { JSONArray(context.getSharedPreferences(PREF, 0).getString(ITEMS, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Triple(o.optString("type"), o.optString("body"), o.optString("time"))
        }
    }
}
