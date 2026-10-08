package com.yasin.privacymessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var btnContinue: Button

    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_profile_setup)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        etName = findViewById(R.id.etName)
        etUsername = findViewById(R.id.etUsername)
        btnContinue = findViewById(R.id.btnContinueProfile)

        phoneNumber =
            intent.getStringExtra("phone_number") ?: ""

        btnContinue.setOnClickListener {
            createProfile()
        }
    }

    private fun createProfile() {

        val name = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Enter your name"
            etName.requestFocus()
            return
        }

        if (username.isEmpty()) {
            etUsername.error = "Choose a username"
            etUsername.requestFocus()
            return
        }

        if (username.length < 4) {
            etUsername.error =
                "Username must be at least 4 characters"
            etUsername.requestFocus()
            return
        }

        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            etUsername.error =
                "Use only letters, numbers and underscore"
            etUsername.requestFocus()
            return
        }

        val account = UserAccount(
            id = 1,
            phoneNumber = phoneNumber,
            name = name,
            username = username
        )

        btnContinue.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {

            try {

                val database =
                    AppDatabase.getDatabase(
                        this@ProfileSetupActivity
                    )

                database.userAccountDao()
                    .insertAccount(account)

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@ProfileSetupActivity,
                        "Account saved securely on this device!",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this@ProfileSetupActivity,
                        HomeActivity::class.java
                    )

                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    btnContinue.isEnabled = true

                    Toast.makeText(
                        this@ProfileSetupActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}