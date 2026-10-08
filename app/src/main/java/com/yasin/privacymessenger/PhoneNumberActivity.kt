package com.yasin.privacymessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PhoneNumberActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: EditText
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_phone_number)

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

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        btnContinue = findViewById(R.id.btnContinue)

        btnContinue.setOnClickListener {
            continueToOtp()
        }
    }

    private fun continueToOtp() {

        val phoneNumber = etPhoneNumber.text.toString().trim()

        if (phoneNumber.isEmpty()) {
            etPhoneNumber.error = "Enter your mobile number"
            etPhoneNumber.requestFocus()
            return
        }

        val normalizedNumber = phoneNumber
            .replace(" ", "")
            .replace("-", "")

        val validBangladeshNumber =
            normalizedNumber.matches(
                Regex("^(01[3-9][0-9]{8}|\\+8801[3-9][0-9]{8})$")
            )

        if (!validBangladeshNumber) {
            etPhoneNumber.error =
                "Enter a valid Bangladesh mobile number"
            etPhoneNumber.requestFocus()
            return
        }

        val intent = Intent(this, OtpActivity::class.java)

        intent.putExtra(
            "phone_number",
            normalizedNumber
        )

        startActivity(intent)
    }
}