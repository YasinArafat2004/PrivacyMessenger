package com.yasin.privacymessenger

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OtpActivity : AppCompatActivity() {

    private lateinit var etOtp: EditText
    private lateinit var btnVerify: Button
    private lateinit var tvResend: TextView
    private lateinit var tvOtpDescription: TextView

    private var countdownTimer: CountDownTimer? = null

    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_otp)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        etOtp = findViewById(R.id.etOtp)
        btnVerify = findViewById(R.id.btnVerify)
        tvResend = findViewById(R.id.tvResend)
        tvOtpDescription = findViewById(R.id.tvOtpDescription)

        // Receive phone number from PhoneNumberActivity
        phoneNumber = intent.getStringExtra("phone_number") ?: ""

        // Show the number on the OTP screen
        if (phoneNumber.isNotEmpty()) {
            tvOtpDescription.text =
                "Enter the 6-digit verification code sent to\n$phoneNumber"
        }

        startCountdown()

        btnVerify.setOnClickListener {
            verifyOtp()
        }

        tvResend.setOnClickListener {
            if (tvResend.isEnabled) {

                Toast.makeText(
                    this,
                    "OTP resend will be connected later.",
                    Toast.LENGTH_SHORT
                ).show()

                startCountdown()
            }
        }
    }

    private fun verifyOtp() {

        val otp = etOtp.text.toString().trim()

        if (otp.length != 6) {
            etOtp.error = "Enter a 6-digit OTP"
            etOtp.requestFocus()
            return
        }

        /*
         * DEVELOPMENT MODE
         *
         * Any 6-digit OTP is accepted for now.
         *
         * Later we will replace this with
         * real server-side OTP verification.
         */

        Toast.makeText(
            this,
            "Phone number verified!",
            Toast.LENGTH_SHORT
        ).show()

        val intent = Intent(
            this,
            ProfileSetupActivity::class.java
        )

        // Pass phone number to profile screen
        intent.putExtra(
            "phone_number",
            phoneNumber
        )

        startActivity(intent)

        finish()
    }

    private fun startCountdown() {

        tvResend.isEnabled = false

        countdownTimer?.cancel()

        countdownTimer = object : CountDownTimer(30000, 1000) {

            override fun onTick(millisUntilFinished: Long) {

                val seconds = millisUntilFinished / 1000

                tvResend.text =
                    "Resend code in ${seconds}s"
            }

            override fun onFinish() {

                tvResend.text = "Resend code"
                tvResend.isEnabled = true
            }
        }.start()
    }

    override fun onDestroy() {

        countdownTimer?.cancel()

        super.onDestroy()
    }
}