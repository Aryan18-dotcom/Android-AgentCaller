package com.aryan.directsim

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class CallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CallActivity"
        var instance: CallActivity? = null
    }

    private lateinit var audioManager: AudioManager
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var isTtsReady = false
    private var isAgentSpeaking = false
    private var isCallActive = true
    private var hasSpokenGreeting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Keep screen awake and visible over lockscreen during the call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        buildUi()

        // Lock audio route to speaker immediately
        AssistantInCallService.instance?.forceSpeakerphone()
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun buildUi() {
        val caller = intent.getStringExtra("CALLER_NUMBER") ?: "Incoming Call"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#171717"))
            setPadding(48, 64, 48, 64)
        }

        val header = TextView(this).apply {
            text = "AI Call Assistant Active\n$caller"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Initializing speaker & voice engine..."
            textSize = 14f
            setTextColor(Color.parseColor("#34D399"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        transcriptText = TextView(this).apply {
            text = ""
            textSize = 15f
            setTextColor(Color.parseColor("#E5E5E5"))
        }
        scrollView.addView(transcriptText)

        val takeoverBtn = Button(this).apply {
            text = "Take Over Call (Turn Off Agent)"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener { takeOverCallManually() }
        }

        val endBtn = Button(this).apply {
            text = "Hang Up Call"
            setBackgroundColor(Color.parseColor("#DC2626"))
            setTextColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            layoutParams = params
            setOnClickListener {
                AssistantInCallService.instance?.hangUpActiveCall()
                onCallEnded()
            }
        }

        root.addView(header)
        root.addView(statusText)
        root.addView(scrollView)
        root.addView(takeoverBtn)
        root.addView(endBtn)
        setContentView(root)
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                // Use USAGE_MEDIA + CONTENT_TYPE_SPEECH so telephony AEC doesn't mute it
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)

                // Slight pitch & rate shift prevents acoustic echo canceler suppression
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.08f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isAgentSpeaking = true
                        mainHandler.post {
                            stopListening()
                            statusText.text = "Agent speaking to caller..."
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        isAgentSpeaking = false
                        // Wait 400ms for speaker room reverb to fade before opening mic
                        mainHandler.postDelayed({
                            if (isCallActive) {
                                AssistantInCallService.instance?.forceSpeakerphone()
                                startListening()
                            }
                        }, 400L)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isAgentSpeaking = false
                        mainHandler.postDelayed({
                            if (isCallActive) startListening()
                        }, 400L)
                    }
                })

                isTtsReady = true
                if (!hasSpokenGreeting && isCallActive) {
                    hasSpokenGreeting = true
                    speakToCaller(Storage.getGreetingMessage(this))
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                statusText.text = "TTS init failed. Check device TTS engine."
            }
        }
    }

    private fun speakToCaller(message: String) {
        if (!isTtsReady || !isCallActive) return

        // Re-lock speaker route & maximize media volume before every utterance
        AssistantInCallService.instance?.forceSpeakerphone()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

        appendTranscript("Agent: $message")

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "AGENT_UTTERANCE_${System.currentTimeMillis()}")
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Speech recognition unavailable on this device."
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "Listening to caller..."
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Caller is speaking..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText.text = "Processing caller message..."
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
                        appendTranscript("Caller: $spokenText")
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
            Log.e(TAG, "Error starting SpeechRecognizer", e)
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer", e)
        }
    }

    private fun processCallerInput(callerSpeech: String) {
        statusText.text = "Thinking..."
        bgExecutor.execute {
            val reply = generateAgentReply(callerSpeech)
            mainHandler.post {
                if (isCallActive) {
                    speakToCaller(reply)
                }
            }
        }
    }

    /**
     * Replace the body of this method with your existing LLM HTTP request (Groq / Gemini / OpenAI)
     * using Storage.getApiKey(this) and Storage.getSystemPrompt(this).
     */
    private fun generateAgentReply(callerSpeech: String): String {
        val lower = callerSpeech.lowercase(Locale.getDefault())
        return when {
            "urgent" in lower || "emergency" in lower ->
                "Understood, I will alert Aryan immediately that this is urgent."
            "callback" in lower || "call back" in lower || "message" in lower ->
                "Got it. I have noted your message and will ask Aryan to call you back as soon as possible."
            "bye" in lower || "thank" in lower ->
                "You are welcome. Have a great day!"
            else ->
                "Got it, you said: $callerSpeech. Should I pass any specific message along to Aryan?"
        }
    }

    private fun takeOverCallManually() {
        isCallActive = false
        stopListening()
        tts?.stop()
        Storage.setAssistantEnabled(this, false)
        AssistantInCallService.instance?.restoreEarpieceForManualTakeover()
        finish()
    }

    fun onCallEnded() {
        if (!isCallActive) return
        isCallActive = false
        mainHandler.post {
            stopListening()
            tts?.stop()
            finish()
        }
    }

    private fun appendTranscript(line: String) {
        transcriptText.append("$line\n\n")
    }

    override fun onDestroy() {
        isCallActive = false
        instance = null
        stopListening()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        bgExecutor.shutdownNow()
        super.onDestroy()
    }
}