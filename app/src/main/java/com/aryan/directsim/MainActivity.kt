package com.aryan.directsim

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val REQ_DIALER_ROLE = 1002
    }

    private var testTts: TextToSpeech? = null

    private lateinit var warningCard: LinearLayout
    private lateinit var warningTitleText: TextView
    private lateinit var warningBodyText: TextView
    private lateinit var fixSetupBtn: Button

    private lateinit var agentBadgeText: TextView
    private lateinit var agentSubText: TextView
    private lateinit var bigToggleCta: Button
    private lateinit var greetingInput: EditText
    private lateinit var delayButtons: List<Button>
    private lateinit var notesNavBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        testTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTts?.language = Locale.US
                testTts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }

        buildUi()
        refreshDashboardState()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardState()
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
        // Outer container for Moto Edge 70 Fusion edge-to-edge responsiveness
        val screenRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val topHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }

        val title = TextView(this).apply {
            text = "DirectSIM Assistant"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F5F5F5"))
        }

        val subtitle = TextView(this).apply {
            text = "Personal SIM Call Answering Agent"
            textSize = 13f
            setTextColor(Color.parseColor("#A3A3A3"))
            setPadding(0, dp(2), 0, 0)
        }

        topHeader.addView(title)
        topHeader.addView(subtitle)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }

        // 1. PERSISTENT SETUP WARNING BANNER (Auto-hides once Default App + Permissions are ready)
        warningCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#261D12", "#D97706", 16)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        warningTitleText = TextView(this).apply {
            text = "Action Required: Default Phone App Not Set"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FBBF24"))
        }

        warningBodyText = TextView(this).apply {
            text = "Tap below to open the app selector dropdown and choose DirectSIM Call Assistant so it can answer calls and route speaker audio."
            textSize = 13f
            setTextColor(Color.parseColor("#E5E5E5"))
            setPadding(0, dp(6), 0, dp(12))
        }

        fixSetupBtn = Button(this).apply {
            text = "SET AS DEFAULT PHONE APP"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#D97706", null, 12)
            setTextColor(Color.parseColor("#121212"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { handleOneClickSetup() }
        }

        warningCard.addView(warningTitleText)
        warningCard.addView(warningBodyText)
        warningCard.addView(fixSetupBtn)
        content.addView(warningCard)

        // 2. HERO ON/OFF CTA CARD
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#1E1E1E", "#383838", 18)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        agentBadgeText = TextView(this).apply {
            text = "● ASSISTANT STANDBY (OFF)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A3A3A3"))
        }

        agentSubText = TextView(this).apply {
            text = "Incoming calls ring normally until you turn the AI agent ON."
            textSize = 13f
            setTextColor(Color.parseColor("#D4D4D4"))
            setPadding(0, dp(6), 0, dp(16))
        }

        bigToggleCta = Button(this).apply {
            text = "TURN ON AI CALL AGENT"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setOnClickListener { toggleAgentState() }
        }

        heroCard.addView(agentBadgeText)
        heroCard.addView(agentSubText)
        heroCard.addView(bigToggleCta)
        content.addView(heroCard)

        // 3. GREETING TEXTAREA CARD
        val greetingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#1E1E1E", "#383838", 18)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val greetingTitle = TextView(this).apply {
            text = "Agent Greeting Message"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F5F5F5"))
        }

        val greetingHint = TextView(this).apply {
            text = "Spoken aloud automatically as soon as the agent picks up an incoming call."
            textSize = 12f
            setTextColor(Color.parseColor("#A3A3A3"))
            setPadding(0, dp(4), 0, dp(12))
        }

        greetingInput = EditText(this).apply {
            setText(Storage.getGreetingMessage(this@MainActivity))
            hint = "Enter greeting message..."
            setHintTextColor(Color.parseColor("#737373"))
            setTextColor(Color.parseColor("#F5F5F5"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            background = roundedBg("#262626", "#404040", 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val saveGreetingBtn = Button(this).apply {
            text = "SAVE GREETING"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#F5F5F5", null, 12)
            setTextColor(Color.parseColor("#121212"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = dp(8) }
            setOnClickListener {
                val updated = greetingInput.text.toString().trim()
                if (updated.isNotEmpty()) {
                    Storage.setGreetingMessage(this@MainActivity, updated)
                    Toast.makeText(this@MainActivity, "Greeting updated", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val testVoiceBtn = Button(this).apply {
            text = "TEST SPEAKER"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#404040", 12)
            setTextColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(8) }
            setOnClickListener {
                val textToSpeak = greetingInput.text.toString().trim()
                Storage.setGreetingMessage(this@MainActivity, textToSpeak)
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    0
                )
                testTts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "TEST_TTS")
            }
        }

        actionRow.addView(saveGreetingBtn)
        actionRow.addView(testVoiceBtn)

        greetingCard.addView(greetingTitle)
        greetingCard.addView(greetingHint)
        greetingCard.addView(greetingInput)
        greetingCard.addView(actionRow)
        content.addView(greetingCard)

        // 4. AUTO-ANSWER DELAY CARD
        val delayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg("#1E1E1E", "#383838", 18)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val delayTitle = TextView(this).apply {
            text = "Auto-Answer Delay"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F5F5F5"))
            setPadding(0, 0, 0, dp(10))
        }

        val delayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val options = listOf(0 to "Instant (0s)", 2 to "2 Secs", 5 to "5 Secs")
        delayButtons = options.map { (sec, label) ->
            Button(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                tag = sec
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = if (sec != 5) dp(8) else 0
                }
                setOnClickListener {
                    Storage.setAutoAnswerDelaySec(this@MainActivity, sec)
                    updateDelayButtons()
                }
            }
        }
        delayButtons.forEach { delayRow.addView(it) }

        delayCard.addView(delayTitle)
        delayCard.addView(delayRow)
        content.addView(delayCard)

        scroll.addView(content)

        // 5. BOTTOM NAVIGATION BAR
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }

        val dashboardNavBtn = Button(this).apply {
            text = "Dashboard"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#262626", "#525252", 12)
            setTextColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = dp(8) }
        }

        notesNavBtn = Button(this).apply {
            text = "Call Notes"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg("#181818", "#383838", 12)
            setTextColor(Color.parseColor("#A3A3A3"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dp(8) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, NotesActivity::class.java))
            }
        }

        bottomNav.addView(dashboardNavBtn)
        bottomNav.addView(notesNavBtn)

        screenRoot.addView(topHeader)
        screenRoot.addView(scroll)
        screenRoot.addView(bottomNav)

        // Handle Moto Edge 70 Fusion status bar, gesture nav bar & keyboard insets
        screenRoot.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                topInset = sys.top
                bottomInset = sys.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            topHeader.setPadding(dp(20), topInset + dp(14), dp(20), dp(12))
            bottomNav.setPadding(dp(16), dp(10), dp(16), bottomInset + dp(10))
            insets
        }

        setContentView(screenRoot)
    }

    private fun isDefaultPhoneApp(): Boolean {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isTelecomDefault = telecom.defaultDialerPackage == packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            isTelecomDefault || roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            isTelecomDefault
        }
    }

    private fun getMissingPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun refreshDashboardState() {
        val defaultReady = isDefaultPhoneApp()
        val missingPerms = getMissingPermissions()

        // Requirement 3 & 4: Show warning + direct button when unconfigured; hide completely when ready
        if (defaultReady && missingPerms.isEmpty()) {
            warningCard.visibility = View.GONE
        } else {
            warningCard.visibility = View.VISIBLE
            if (!defaultReady) {
                warningTitleText.text = "Warning: App Not Set as Default Phone App"
                warningBodyText.text = "Tap the button below to open the apps dropdown and select DirectSIM Call Assistant so it can answer incoming calls."
                fixSetupBtn.text = "SELECT AS DEFAULT PHONE APP"
            } else {
                warningTitleText.text = "Warning: Required Call & Mic Permissions Missing"
                warningBodyText.text = "Grant microphone and phone permissions so the AI agent can hear callers and speak back."
                fixSetupBtn.text = "ALLOW PERMISSIONS NOW"
            }
        }

        // Update Hero Toggle CTA
        val enabled = Storage.isAssistantEnabled(this)
        if (enabled) {
            agentBadgeText.text = "● AI CALL AGENT IS ACTIVE (ON)"
            agentBadgeText.setTextColor(Color.parseColor("#10B981"))
            agentSubText.text = "Incoming SIM calls will be auto-answered on loudspeaker and logged to Call Notes."
            bigToggleCta.text = "TURN OFF AI CALL AGENT"
            bigToggleCta.background = roundedBg("#DC2626", null, 14)
            bigToggleCta.setTextColor(Color.WHITE)
        } else {
            agentBadgeText.text = "● AI CALL AGENT IS OFF"
            agentBadgeText.setTextColor(Color.parseColor("#A3A3A3"))
            agentSubText.text = "Turn ON before stepping away so the assistant answers incoming calls for you."
            bigToggleCta.text = "TURN ON AI CALL AGENT"
            bigToggleCta.background = roundedBg("#10B981", null, 14)
            bigToggleCta.setTextColor(Color.parseColor("#121212"))
        }

        updateDelayButtons()

        val noteCount = Storage.getCallNotes(this).size
        notesNavBtn.text = if (noteCount > 0) "Call Notes ($noteCount)" else "Call Notes"
    }

    private fun updateDelayButtons() {
        val selected = Storage.getAutoAnswerDelaySec(this)
        delayButtons.forEach { btn ->
            val sec = btn.tag as Int
            if (sec == selected) {
                btn.background = roundedBg("#F5F5F5", null, 10)
                btn.setTextColor(Color.parseColor("#121212"))
            } else {
                btn.background = roundedBg("#262626", "#404040", 10)
                btn.setTextColor(Color.parseColor("#D4D4D4"))
            }
        }
    }

    private fun toggleAgentState() {
        val currentlyOn = Storage.isAssistantEnabled(this)
        if (!currentlyOn) {
            if (!isDefaultPhoneApp()) {
                Toast.makeText(
                    this,
                    "Please select DirectSIM Call Assistant in the default apps dropdown first.",
                    Toast.LENGTH_LONG
                ).show()
                openDefaultDialerDropdown()
                return
            }
            val missing = getMissingPermissions()
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
                return
            }
        }

        Storage.setAssistantEnabled(this, !currentlyOn)
        refreshDashboardState()
    }

    private fun handleOneClickSetup() {
        if (!isDefaultPhoneApp()) {
            openDefaultDialerDropdown()
        } else {
            val missing = getMissingPermissions()
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
            }
        }
    }

    /**
     * Opens the system Default Phone App dropdown listing all dialer apps,
     * falling back to Android's Default Apps settings screen if suppressed by OEM rules.
     */
    private fun openDefaultDialerDropdown() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                    !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                ) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                        REQ_DIALER_ROLE
                    )
                    return
                }
            } else {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_DIALER_ROLE)
                return
            }
        } catch (_: Exception) {
        }

        // Fallback: Direct Default Apps dropdown list in Android Settings
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Open Settings > Apps > Default apps > Phone app", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DIALER_ROLE) {
            if (!isDefaultPhoneApp() && resultCode != RESULT_OK) {
                // If the quick dialog was dismissed or blocked, open the full Default Apps dropdown list
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                }
            }
            val missing = getMissingPermissions()
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
            }
            refreshDashboardState()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshDashboardState()
    }

    override fun onDestroy() {
        testTts?.stop()
        testTts?.shutdown()
        super.onDestroy()
    }
}