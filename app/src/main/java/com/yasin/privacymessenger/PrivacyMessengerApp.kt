package com.yasin.privacymessenger

import android.app.Application
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivacyMessengerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Global observer: launches CallActivity whenever an incoming
        // call arrives, regardless of which Activity is currently visible.
        appScope.launch {
            CallManager.incomingCall.collect { socketId ->
                if (socketId != null) {
                    val callerName =
                        CallManager.incomingCallerName.value ?: "Unknown"

                    val intent = Intent(
                        this@PrivacyMessengerApp,
                        CallActivity::class.java
                    ).apply {
                        putExtra("isIncoming", true)
                        putExtra("username", callerName)
                        putExtra("socketId", socketId)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }

                    startActivity(intent)
                }
            }
        }
    }
}
