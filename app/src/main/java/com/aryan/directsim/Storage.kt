package com.aryan.directsim

import android.content.Context
import android.content.SharedPreferences

object Storage {
    private const val PREF_NAME = "direct_sim_assistant_prefs"
    private const val KEY_ENABLED = "assistant_enabled"
    private const val KEY_DELAY_SEC = "auto_answer_delay_sec"
    private const val KEY_GREETING = "greeting_message"
    private const val KEY_API_KEY = "llm_api_key"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"

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
            "Hello! Aryan is unavailable right now and I am his AI assistant. How can I help you?"
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
            "You are Aryan's personal phone assistant. Keep replies concise, polite, and under 2 sentences."
        ) ?: ""
    }

    fun setSystemPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_SYSTEM_PROMPT, prompt.trim()).apply()
    }
}