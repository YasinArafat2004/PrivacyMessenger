package com.yasin.privacymessenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {

    private lateinit var tvCallerName: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var layoutIncoming: View
    private lateinit var btnEndCall: Button

    private var isIncoming = false
    private var targetUsername = ""
    private var targetSocketId = ""
    private var callInitiated = false   // 🔴 guard against double initiation

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                finish()
            } else {
                if (isIncoming) answerCallNow() else makeCallNow()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        tvCallerName = findViewById(R.id.tvCallerName)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        layoutIncoming = findViewById(R.id.layoutIncoming)
        btnEndCall = findViewById(R.id.btnEndCall)

        val btnAccept = findViewById<Button>(R.id.btnAccept)
        val btnDecline = findViewById<Button>(R.id.btnDecline)

        isIncoming = intent.getBooleanExtra("isIncoming", false)
        targetUsername = intent.getStringExtra("username") ?: "Unknown"
        targetSocketId = intent.getStringExtra("socketId") ?: ""

        tvCallerName.text = targetUsername

        if (isIncoming) {
            layoutIncoming.visibility = View.VISIBLE
            btnEndCall.visibility = View.GONE
            tvCallStatus.text = "Incoming call…"
        } else {
            layoutIncoming.visibility = View.GONE
            btnEndCall.visibility = View.VISIBLE
            tvCallStatus.text = "Calling…"
            // Do NOT initiate here; wait for permission result.
            checkPermissionAndInitiate()
        }

        btnAccept.setOnClickListener {
            layoutIncoming.visibility = View.GONE
            btnEndCall.visibility = View.VISIBLE
            tvCallStatus.text = "Connecting…"
            checkPermissionAndAnswer()
        }

        btnDecline.setOnClickListener {
            CallManager.endCall()
            finish()
        }

        btnEndCall.setOnClickListener {
            CallManager.endCall()
            finish()
        }

        // Observe call state.
        lifecycleScope.launch {
            CallManager.callState.collect { state ->
                when (state) {
                    "CONNECTED" -> tvCallStatus.text = "Connected"
                    "IDLE" -> {
                        if (!isFinishing) finish()
                    }
                }
            }
        }
    }

    private fun checkPermissionAndInitiate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            makeCallNow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkPermissionAndAnswer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            answerCallNow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun makeCallNow() {
        if (callInitiated) return
        callInitiated = true
        CallManager.makeCall(targetUsername, targetSocketId)
    }

    private fun answerCallNow() {
        if (callInitiated) return
        callInitiated = true
        CallManager.answerCall(targetSocketId)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 🔴 Ensure the call is cleaned up if the activity is destroyed.
        if (!callInitiated || CallManager.callState.value != "IDLE") {
            CallManager.endCall()
        }
    }
}
