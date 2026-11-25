package com.aryan.directsim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

class CallActivity : Activity() {

    companion object {
        private const val TAG = "CallActivity"
        var instance: CallActivity? = null
    }

    private lateinit var audioManager: AudioManager
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptScroll: ScrollView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var callerNumber: String = "Unknown Caller"
    private val transcriptLines = mutableListOf<String>()
    private val callerUtterances = mutableListOf<String>()

    private var isTtsReady = false
    private var isAgentSpeaking = false
    private var isCallActive = true
    private var hasSpokenGreeting = false
    private var hasSavedNote = false

    override fun onCreate(savedInstanceState: Bundle?) {
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
        buildUi()

        AssistantInCallService.instance?.forceSpeakerphone()
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun roundedBg(fillHex: String, strokeHex: String? = null, radiusDp: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.setOnApplyWindowInsetsListener { v, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars())
                topInset = sys.top
                bottomInset = sys.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            v.setPadding(dp(20), topInset + dp(16), dp(20), bottomInset + dp(16))
            insets
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#1E1E1E", "#383838", 18)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val badge = TextView(this).apply {
            text = "LIVE CALL • SPEAKER COUPLING ACTIVE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER
        }

        val header = TextView(this).apply {
            text = callerNumber
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F5F5F5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        }

        statusText = TextView(this).apply {
            text = "Initializing loudspeaker & voice engine..."
            textSize = 14f
            setTextColor(Color.parseColor("#A3A3A3"))
            gravity = Gravity.CENTER
        }

        headerCard.addView(badge)
        headerCard.addView(header)
        headerCard.addView(statusText)

        transcriptScroll = ScrollView(this).apply {
            background = roundedBg("#181818", "#2E2E2E", 16)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(16)
            }
        }

        transcriptText = TextView(this).apply {
            text = ""
            textSize = 15f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(Color.parseColor("#E5E5E5"))
        }
        transcriptScroll.addView(transcriptText)

        val takeoverBtn = Button(this).apply {
            text = "TAKE OVER CALL MANUALLY"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#525252", 14)
            setTextColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { takeOverCallManually() }
        }

        val endBtn = Button(this).apply {
            text = "END CALL & SAVE NOTE"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#DC2626", null, 14)
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                AssistantInCallService.instance?.hangUpActiveCall()
                onCallEnded()
            }
        }

        root.addView(headerCard)
        root.addView(transcriptScroll)
        root.addView(takeoverBtn)
        root.addView(endBtn)
        setContentView(root)
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
                tts?.setPitch(1.08f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isAgentSpeaking = true
                        mainHandler.post {
                            stopListening()
                            statusText.text = "Agent speaking to caller..."
                            statusText.setTextColor(Color.parseColor("#10B981"))
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        isAgentSpeaking = false
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
                statusText.text = "TTS engine error (code $status)."
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
                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Caller is speaking..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText.text = "Processing reply..."
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
        statusText.text = "Generating response..."
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
        if (transcriptLines.isEmpty()) return

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

        Storage.saveCallNote(
            this,
            CallNote(
                id = System.currentTimeMillis(),
                callerNumber = callerNumber,
                timestampMs = System.currentTimeMillis(),
                category = category,
                summary = summary,
                transcript = transcriptLines.joinToString("\n\n")
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

    private fun appendTranscript(line: String) {
        transcriptLines.add(line)
        transcriptText.append("$line\n\n")
        transcriptScroll.post {
            transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN)
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