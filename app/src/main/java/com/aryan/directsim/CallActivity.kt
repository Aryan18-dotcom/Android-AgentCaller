package com.aryan.directsim

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.Executors

data class ChatTurn(val isAgent: Boolean, val text: String)

class CallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CallActivity"
        var instance: CallActivity? = null
    }

    private lateinit var audioManager: AudioManager
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var callerNumber by mutableStateOf("Unknown Caller")
    private var statusText by mutableStateOf("Initializing speaker & voice engine...")
    private var isAgentSpeaking by mutableStateOf(false)
    private val turns = mutableStateListOf<ChatTurn>()
    private val callerUtterances = mutableListOf<String>()

    private var isTtsReady = false
    private var isCallActive = true
    private var hasSpokenGreeting = false
    private var hasSavedNote = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        callerNumber = intent.getStringExtra("CALLER_NUMBER") ?: "Unknown Caller"
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setContent {
            val neutralScheme = darkColorScheme(
                primary = Color(0xFF10B981),
                background = Color(0xFF111113),
                surface = Color(0xFF1A1A1E),
                onBackground = Color(0xFFF4F4F6),
                onSurface = Color(0xFFF4F4F6)
            )
            MaterialTheme(colorScheme = neutralScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF111113)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 28.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF1A1A1E),
                            border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "LIVE CALL • SPEAKER COUPLING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = callerNumber,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF4F4F6)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 13.sp,
                                    color = if (isAgentSpeaking) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF161619),
                            border = BorderStroke(1.dp, Color(0xFF2A2A30)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(turns) { turn ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (turn.isAgent) Color(0xFF1E2925) else Color(0xFF242429),
                                        border = BorderStroke(
                                            1.dp,
                                            if (turn.isAgent) Color(0xFF10B981).copy(alpha = 0.4f)
                                            else Color(0xFF383842)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (turn.isAgent) "AI Agent" else "Caller",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (turn.isAgent) Color(0xFF34D399) else Color(0xFFA1A1AA)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = turn.text,
                                                fontSize = 14.sp,
                                                color = Color(0xFFF4F4F6)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { takeOverCallManually() },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "TAKE OVER CALL MANUALLY",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF4F4F6)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                AssistantInCallService.instance?.hangUpActiveCall()
                                onCallEnded()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "END CALL & SAVE NOTE",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        AssistantInCallService.instance?.forceSpeakerphone()
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)

                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.06f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            isAgentSpeaking = true
                            stopListening()
                            statusText = "Agent speaking to caller..."
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.postDelayed({
                            isAgentSpeaking = false
                            if (isCallActive) {
                                AssistantInCallService.instance?.forceSpeakerphone()
                                startListening()
                            }
                        }, 450L)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.postDelayed({
                            isAgentSpeaking = false
                            if (isCallActive) startListening()
                        }, 450L)
                    }
                })

                isTtsReady = true
                if (!hasSpokenGreeting && isCallActive) {
                    hasSpokenGreeting = true
                    speakToCaller(Storage.getGreetingMessage(this))
                }
            } else {
                Log.e(TAG, "TTS init failed: $status")
                statusText = "TTS engine error ($status)"
            }
        }
    }

    private fun speakToCaller(message: String) {
        if (!isTtsReady || !isCallActive) return

        AssistantInCallService.instance?.forceSpeakerphone()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

        turns.add(ChatTurn(isAgent = true, text = message))

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "AGENT_${System.currentTimeMillis()}")
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText = "Speech recognition unavailable"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText = "Listening to caller..."
                }

                override fun onBeginningOfSpeech() {
                    statusText = "Caller is speaking..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText = "Processing reply..."
                }

                override fun onError(error: Int) {
                    if (isCallActive && !isAgentSpeaking) {
                        mainHandler.postDelayed({
                            if (isCallActive && !isAgentSpeaking) startListening()
                        }, 300L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()?.trim().orEmpty()
                    if (spokenText.isNotEmpty() && isCallActive && !isAgentSpeaking) {
                        callerUtterances.add(spokenText)
                        turns.add(ChatTurn(isAgent = false, text = spokenText))
                        processCallerInput(spokenText)
                    } else if (isCallActive && !isAgentSpeaking) {
                        startListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        if (!isCallActive || isAgentSpeaking) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting STT", e)
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping STT", e)
        }
    }

    private fun processCallerInput(callerSpeech: String) {
        statusText = "Generating response..."
        bgExecutor.execute {
            val reply = generateAgentReply(callerSpeech)
            mainHandler.post {
                if (isCallActive) {
                    speakToCaller(reply)
                }
            }
        }
    }

    private fun generateAgentReply(callerSpeech: String): String {
        val lower = callerSpeech.lowercase(Locale.getDefault())
        return when {
            "urgent" in lower || "emergency" in lower || "asap" in lower ->
                "Understood, I will alert Aryan immediately that this is urgent."
            "callback" in lower || "call back" in lower || "message" in lower ->
                "Got it. I have noted your message and will ask Aryan to call you back shortly."
            "bye" in lower || "thank" in lower ->
                "You are welcome. Have a great day!"
            else ->
                "Got it, you said: $callerSpeech. Is there anything else you want me to note down for Aryan?"
        }
    }

    private fun persistNoteIfNeeded() {
        if (hasSavedNote) return
        hasSavedNote = true
        if (turns.isEmpty()) return

        val joinedCaller = callerUtterances.joinToString(" | ")
        val lower = joinedCaller.lowercase(Locale.getDefault())
        val category = when {
            "urgent" in lower || "emergency" in lower || "asap" in lower -> "Urgent"
            "callback" in lower || "call back" in lower || "message" in lower -> "Callback"
            else -> "General"
        }
        val summary = if (joinedCaller.isNotBlank()) {
            joinedCaller.take(140)
        } else {
            "Greeting played; caller did not leave a voice message."
        }
        val fullTranscript = turns.joinToString("\n\n") {
            "${if (it.isAgent) "Agent" else "Caller"}: ${it.text}"
        }

        Storage.saveCallNote(
            this,
            CallNote(
                id = System.currentTimeMillis(),
                callerNumber = callerNumber,
                timestampMs = System.currentTimeMillis(),
                category = category,
                summary = summary,
                transcript = fullTranscript
            )
        )
    }

    private fun takeOverCallManually() {
        isCallActive = false
        persistNoteIfNeeded()
        stopListening()
        tts?.stop()
        Storage.setAssistantEnabled(this, false)
        AssistantInCallService.instance?.restoreEarpieceForManualTakeover()
        finish()
    }

    fun onCallEnded() {
        if (!isCallActive) return
        isCallActive = false
        persistNoteIfNeeded()
        mainHandler.post {
            stopListening()
            tts?.stop()
            finish()
        }
    }

    override fun onDestroy() {
        isCallActive = false
        persistNoteIfNeeded()
        instance = null
        stopListening()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        bgExecutor.shutdownNow()
        super.onDestroy()
    }
}