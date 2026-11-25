package com.aryan.directsim

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log

class AssistantInCallService : InCallService() {

    companion object {
        private const val TAG = "AssistantInCallService"
        var instance: AssistantInCallService? = null
        var currentCall: Call? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var availableEndpoints: List<CallEndpoint> = emptyList()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")

            when (state) {
                Call.STATE_ACTIVE -> {
                    if (Storage.isAssistantEnabled(this@AssistantInCallService)) {
                        forceSpeakerphone()
                        launchCallUi(call)
                    }
                }
                Call.STATE_DISCONNECTED -> {
                    currentCall = null
                    CallActivity.instance?.onCallEnded()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)

        val assistantOn = Storage.isAssistantEnabled(this)
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            @Suppress("DEPRECATION")
            call.state
        }

        if (assistantOn && state == Call.STATE_RINGING) {
            val delayMs = Storage.getAutoAnswerDelaySec(this) * 1000L
            handler.postDelayed({
                if (Storage.isAssistantEnabled(this) && currentCall == call) {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                }
            }, delayMs)
        } else if (assistantOn && state == Call.STATE_ACTIVE) {
            forceSpeakerphone()
            launchCallUi(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
        }
        CallActivity.instance?.onCallEnded()
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        if (audioState != null && Storage.isAssistantEnabled(this)) {
            if (audioState.route != CallAudioState.ROUTE_SPEAKER) {
                Log.d(TAG, "System switched audio route away from speaker; re-enforcing speaker.")
                forceSpeakerphone()
            }
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        this.availableEndpoints = availableEndpoints
        if (Storage.isAssistantEnabled(this)) {
            forceSpeakerphone()
        }
    }

    fun forceSpeakerphone() {
        // 1. Telecom InCallService speaker routing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val speakerEndpoint = availableEndpoints.firstOrNull {
                it.endpointType == CallEndpoint.TYPE_SPEAKER
            }
            if (speakerEndpoint != null) {
                requestCallEndpointChange(speakerEndpoint, mainExecutor) { }
            } else {
                @Suppress("DEPRECATION")
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }

        // 2. AudioManager speakerphone & max media volume lock
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true

        val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMedia, 0)
    }

    fun restoreEarpieceForManualTakeover() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val earpieceEndpoint = availableEndpoints.firstOrNull {
                it.endpointType == CallEndpoint.TYPE_EARPIECE
            }
            if (earpieceEndpoint != null) {
                requestCallEndpointChange(earpieceEndpoint, mainExecutor) { }
            } else {
                @Suppress("DEPRECATION")
                setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }
        } else {
            @Suppress("DEPRECATION")
            setAudioRoute(CallAudioState.ROUTE_EARPIECE)
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
    }

    fun hangUpActiveCall() {
        currentCall?.disconnect()
    }

    private fun launchCallUi(call: Call) {
        val callerNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown Caller"
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALLER_NUMBER", callerNumber)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}