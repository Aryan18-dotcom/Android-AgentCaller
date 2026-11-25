package com.aryan.directsim

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CallNote(
    val id: Long,
    val callerNumber: String,
    val timestampMs: Long,
    val category: String, // "Urgent", "Callback", "General"
    val summary: String,
    val transcript: String
)

object Storage {
    private const val PREF_NAME = "direct_sim_assistant_prefs"
    private const val KEY_ENABLED = "assistant_enabled"
    private const val KEY_DELAY_SEC = "auto_answer_delay_sec"
    private const val KEY_GREETING = "greeting_message"
    private const val KEY_API_KEY = "llm_api_key"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
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

    fun getAutoAnswerDelaySec(context: Context): Int {
        return prefs(context).getInt(KEY_DELAY_SEC, 2)
    }

    fun setAutoAnswerDelaySec(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_DELAY_SEC, seconds.coerceIn(0, 15)).apply()
    }

    fun getGreetingMessage(context: Context): String {
        return prefs(context).getString(
            KEY_GREETING,
            "Hello! Aryan is unavailable right now and I am his AI assistant. Please tell me your name and why you are calling."
        ) ?: "Hello! How can I help you?"
    }

    fun setGreetingMessage(context: Context, greeting: String) {
        prefs(context).edit().putString(KEY_GREETING, greeting.trim()).apply()
    }

    fun getApiKey(context: Context): String {
        return prefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getSystemPrompt(context: Context): String {
        return prefs(context).getString(
            KEY_SYSTEM_PROMPT,
            "You are Aryan's personal phone assistant. Keep replies polite, natural, and under 2 sentences."
        ) ?: ""
    }

    fun setSystemPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_SYSTEM_PROMPT, prompt.trim()).apply()
    }

    fun saveCallNote(context: Context, note: CallNote) {
        val current = getCallNotes(context).toMutableList()
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