package com.aryan.directsim

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CallNote(
    val id: Long,
    val callerNumber: String,
    val timestampMs: Long,
    val category: String,
    val summary: String,
    val transcript: String
)

object Storage {
    private const val PREF_NAME = "direct_sim_assistant_prefs"
    private const val KEY_ENABLED = "assistant_enabled"
    private const val KEY_FORWARD_NUMBER = "voip_forward_number"
    private const val KEY_BACKEND_URL = "backend_notes_url"
    private const val KEY_GREETING = "greeting_message"
    private const val KEY_CALL_NOTES = "call_notes_json"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isAssistantEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setAssistantEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getForwardNumber(context: Context): String {
        return prefs(context).getString(KEY_FORWARD_NUMBER, "") ?: ""
    }

    fun setForwardNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_FORWARD_NUMBER, number.trim()).apply()
    }

    fun getBackendUrl(context: Context): String {
        return prefs(context).getString(KEY_BACKEND_URL, "") ?: ""
    }

    fun setBackendUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_BACKEND_URL, url.trim()).apply()
    }

    fun getGreetingMessage(context: Context): String {
        return prefs(context).getString(
            KEY_GREETING,
            "Hello! Aryan is in a meeting right now and I am his AI assistant. Please leave your name and message."
        ) ?: "Hello! How can I help you?"
    }

    fun setGreetingMessage(context: Context, greeting: String) {
        prefs(context).edit().putString(KEY_GREETING, greeting.trim()).apply()
    }

    fun saveCallNote(context: Context, note: CallNote) {
        val current = getCallNotes(context).toMutableList()
        if (current.any { it.id == note.id }) return
        current.add(0, note)
        persistNotes(context, current)
    }

    fun deleteCallNote(context: Context, noteId: Long) {
        val updated = getCallNotes(context).filterNot { it.id == noteId }
        persistNotes(context, updated)
    }

    fun clearAllCallNotes(context: Context) {
        prefs(context).edit().remove(KEY_CALL_NOTES).apply()
    }

    fun getCallNotes(context: Context): List<CallNote> {
        val raw = prefs(context).getString(KEY_CALL_NOTES, null) ?: return emptyList()
        val list = mutableListOf<CallNote>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CallNote(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        callerNumber = obj.optString("callerNumber", "Unknown Caller"),
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        category = obj.optString("category", "General"),
                        summary = obj.optString("summary", ""),
                        transcript = obj.optString("transcript", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun persistNotes(context: Context, notes: List<CallNote>) {
        val arr = JSONArray()
        notes.take(100).forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("callerNumber", note.callerNumber)
                put("timestampMs", note.timestampMs)
                put("category", note.category)
                put("summary", note.summary)
                put("transcript", note.transcript)
            }
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_CALL_NOTES, arr.toString()).apply()
    }
}