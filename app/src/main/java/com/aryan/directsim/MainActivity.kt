package com.aryan.directsim

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var testTts: TextToSpeech? = null
    private lateinit var roleStatusText: TextView

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateRoleStatus()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateRoleStatus()
    }

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
        requestNeededPermissions()
        updateRoleStatus()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#171717"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "DirectSIM Call Assistant"
            textSize = 22f
            setTextColor(Color.WHITE)
        }

        roleStatusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FBBF24"))
            setPadding(0, 16, 0, 24)
        }

        val dialerRoleBtn = Button(this).apply {
            text = "1. Set as Default Phone App (Required)"
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setOnClickListener { requestDefaultDialerRole() }
        }

        val toggleSwitch = Switch(this).apply {
            text = "2. AI Call Assistant Active (Auto-Answer)"
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = Storage.isAssistantEnabled(this@MainActivity)
            setPadding(0, 36, 0, 36)
            setOnCheckedChangeListener { _, isChecked ->
                Storage.setAssistantEnabled(this@MainActivity, isChecked)
                Toast.makeText(
                    this@MainActivity,
                    if (isChecked) "AI Assistant ON" else "AI Assistant OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val greetingLabel = TextView(this).apply {
            text = "Initial Greeting Spoken to Caller:"
            setTextColor(Color.parseColor("#D4D4D4"))
        }

        val greetingInput = EditText(this).apply {
            setText(Storage.getGreetingMessage(this@MainActivity))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#262626"))
            setPadding(24, 24, 24, 24)
        }

        val delayLabel = TextView(this).apply {
            text = "Auto-Answer Delay (seconds):"
            setTextColor(Color.parseColor("#D4D4D4"))
            setPadding(0, 24, 0, 8)
        }

        val delayInput = EditText(this).apply {
            setText(Storage.getAutoAnswerDelaySec(this@MainActivity).toString())
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#262626"))
            setPadding(24, 24, 24, 24)
        }

        val saveBtn = Button(this).apply {
            text = "Save Settings"
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            layoutParams = params
            setOnClickListener {
                Storage.setGreetingMessage(this@MainActivity, greetingInput.text.toString())
                val delay = delayInput.text.toString().toIntOrNull() ?: 2
                Storage.setAutoAnswerDelaySec(this@MainActivity, delay)
                Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
            }
        }

        val testSpeakerBtn = Button(this).apply {
            text = "Test Loudspeaker TTS Output"
            setBackgroundColor(Color.parseColor("#404040"))
            setTextColor(Color.WHITE)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            layoutParams = params
            setOnClickListener {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    0
                )
                testTts?.speak(
                    greetingInput.text.toString(),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "TEST_TTS"
                )
            }
        }

        root.addView(title)
        root.addView(roleStatusText)
        root.addView(dialerRoleBtn)
        root.addView(toggleSwitch)
        root.addView(greetingLabel)
        root.addView(greetingInput)
        root.addView(delayLabel)
        root.addView(delayInput)
        root.addView(saveBtn)
        root.addView(testSpeakerBtn)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            roleRequestLauncher.launch(intent)
        }
    }

    private fun updateRoleStatus() {
        val telecom = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isDefault = telecom.defaultDialerPackage == packageName
        roleStatusText.text = if (isDefault) {
            "Status: Active Default Phone App (Ready)"
        } else {
            "Status: NOT Default Phone App — Android will block InCallService audio routing until enabled."
        }
        roleStatusText.setTextColor(
            if (isDefault) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        )
    }

    override fun onDestroy() {
        testTts?.stop()
        testTts?.shutdown()
        super.onDestroy()
    }
}