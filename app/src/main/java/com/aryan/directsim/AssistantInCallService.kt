package com.aryan.directsim

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService

class AssistantInCallService : InCallService() {

    companion object {
        var instance: AssistantInCallService? = null
        var currentCall: Call? = null
    }

    private var availableEndpoints: List<CallEndpoint> = emptyList()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_ACTIVE && Storage.isAssistantEnabled(this@AssistantInCallService)) {
                keepDeviceSilent()
            } else if (state == Call.STATE_DISCONNECTED) {
                currentCall = null
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

        if (Storage.isAssistantEnabled(this)) {
            keepDeviceSilent()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
        }
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        this.availableEndpoints = availableEndpoints
    }

    fun keepDeviceSilent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val earpiece = availableEndpoints.firstOrNull {
                it.endpointType == CallEndpoint.TYPE_EARPIECE
            }
            if (earpiece != null) {
                requestCallEndpointChange(earpiece, mainExecutor) { }
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

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}