package com.aryan.directsim

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class AssistantInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSession.call = call
        CallSession.service = this

        // The call screen is shown for both modes. The activity answers automatically
        // only when Agent Mode is enabled; otherwise the caller continues to ring.
        if (call.state == Call.STATE_RINGING) {
            val screen = Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(screen)
        }
    }

    override fun onCallRemoved(call: Call) {
        if (CallSession.call === call) {
            CallSession.call = null
            CallSession.service = null
        }
        super.onCallRemoved(call)
    }
}

object CallSession {
    @Volatile var call: Call? = null
    @Volatile var service: AssistantInCallService? = null
}
