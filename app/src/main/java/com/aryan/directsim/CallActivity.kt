package com.aryan.directsim

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

class CallActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var started = false
    private var statusText by mutableStateOf("Incoming call")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9B87F5), background = Color(0xFF10111A))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF10111A)) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Personal Call Assistant", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(14.dp))
                        Text(statusText, color = Color.LightGray)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { answerManually() }, modifier = Modifier.fillMaxWidth()) { Text("Answer") }
                        OutlinedButton(onClick = { CallSession.call?.disconnect(); finish() }, modifier = Modifier.fillMaxWidth()) { Text("End call") }
                    }
                }
            }
        }
        if (Storage.enabled(this)) handler.postDelayed({ answerAndStartAgent() }, 400)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { }
                override fun onDone(utteranceId: String?) {
                    val action = pendingAfterSpeech
                    pendingAfterSpeech = null
                    if (action != null) handler.post(action)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val action = pendingAfterSpeech
                    pendingAfterSpeech = null
                    if (action != null) handler.post(action)
                }
            })
            if (Storage.enabled(this) && !started) handler.postDelayed({ answerAndStartAgent() }, 250)
        }
    }

    private var pendingAfterSpeech: (() -> Unit)? = null

    private fun answerManually() {
        val call = CallSession.call ?: return
        if (call.state == Call.STATE_RINGING) call.answer(VideoProfile.STATE_AUDIO_ONLY)
        statusText = "Call connected · Agent Mode is OFF"
    }

    private fun answerAndStartAgent() {
        if (started || isFinishing) return
        val call = CallSession.call
        if (call == null) { statusText = "Waiting for call details…"; return }
        started = true
        if (call.state == Call.STATE_RINGING) call.answer(VideoProfile.STATE_AUDIO_ONLY)
        statusText = "Agent active · testing SIM-call audio"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speak("The microphone permission is missing. I cannot take a voice message. Goodbye.") { disconnect() }
            return
        }

        // This is an acoustic workaround, not access to the protected cellular call stream.
        handler.postDelayed({
            try { CallSession.service?.setAudioRoute(CallAudioState.ROUTE_SPEAKER) } catch (_: Exception) { }
            speak("Hello. This is an automated personal assistant. Your spoken message may be saved on this phone. Say one for a note, two for a callback reminder, or three to end the call.") {
                listen("MENU")
            }
        }, 700)
    }

    private fun speak(message: String, after: (() -> Unit)? = null) {
        statusText = message
        pendingAfterSpeech = after
        val id = "utterance-${System.nanoTime()}"
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR || tts == null) {
            pendingAfterSpeech = null
            if (after != null) handler.postDelayed(after, 1200)
        }
    }

    private fun listen(stage: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            failAudio("Speech recognition is unavailable.")
            return
        }
        if (recognizer == null) {
            recognizer = if (android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText = if (stage == "MENU") "Listening for your choice…" else "Listening to your message…" }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onEndOfSpeech() { }
            override fun onError(error: Int) { retry(stage) }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (stage == "MENU") handleChoice(spoken) else saveMessage(stage, spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) { }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { retry(stage) }
    }

    private fun handleChoice(raw: String) {
        val s = raw.lowercase(Locale.ROOT)
        val choice = when {
            Regex("\\b(1|one|won)\\b").containsMatchIn(s) -> 1
            Regex("\\b(2|two|to|too)\\b").containsMatchIn(s) -> 2
            Regex("\\b(3|three)\\b").containsMatchIn(s) -> 3
            else -> 0
        }
        when (choice) {
            1 -> speak("Please speak your note now.") { listen("NOTE") }
            2 -> speak("Please speak your callback reminder now.") { listen("REMINDER") }
            3 -> speak("Thank you for calling. Goodbye.") { disconnect() }
            else -> retry("MENU")
        }
    }

    private fun saveMessage(type: String, raw: String) {
        val message = raw.trim()
        if (message.isBlank()) { retry(type); return }
        Storage.add(this, type, message)
        val confirmation = if (type == "NOTE") "Your note has been saved. Thank you. Goodbye." else "Your callback reminder has been saved. Thank you. Goodbye."
        speak(confirmation) { disconnect() }
    }

    private fun retry(stage: String) {
        attempts++
        if (attempts > 2) {
            speak("Sorry, I could not understand. Please call again. Goodbye.") { disconnect() }
        } else {
            val prompt = if (stage == "MENU") {
                "Sorry, I did not understand. Say one for a note, two for a reminder, or three to end the call."
            } else {
                "Sorry, I did not catch that. Please speak again."
            }
            speak(prompt) { listen(stage) }
        }
    }

    private fun failAudio(message: String) {
        statusText = message
        speak("Sorry, I cannot access voice input during this call. Goodbye.") { disconnect() }
    }

    private fun disconnect() {
        recognizer?.cancel()
        CallSession.call?.disconnect()
        finish()
    }

    override fun onDestroy() {
        pendingAfterSpeech = null
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
