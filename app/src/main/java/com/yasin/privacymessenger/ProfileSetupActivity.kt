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
import com.yasin.privacymessenger.network.RegisterRequest
import com.yasin.privacymessenger.network.RetrofitClient

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

        if (name.isEmpty()) { etName.error = "Enter your name"; etName.requestFocus(); return }
        if (username.isEmpty()) { etUsername.error = "Choose a username"; etUsername.requestFocus(); return }
        if (username.length < 4) { etUsername.error = "Username must be at least 4 characters"; etUsername.requestFocus(); return }
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            etUsername.error = "Use only letters, numbers and underscore"; etUsername.requestFocus(); return
        }

        val account = UserAccount(
            id = 1,
            phoneNumber = phoneNumber,
            name = name,
            username = username
        )

        btnContinue.isEnabled = false

        lifecycleScope.launch {
            try {
                // 1. Tell the server
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.register(RegisterRequest(username, name))
                }
                if (!response.isSuccessful || response.body()?.success != true) {
                    throw Exception("Server refused (code ${response.code()})")
                }

                // 2. Save locally
                val database = AppDatabase.getDatabase(this@ProfileSetupActivity)
                withContext(Dispatchers.IO) {
                    database.userAccountDao().insertAccount(account)
                }

                // 3. Go home
                Toast.makeText(
                    this@ProfileSetupActivity,
                    "Account created!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@ProfileSetupActivity, HomeActivity::class.java))
                finish()

            } catch (e: Exception) {
                btnContinue.isEnabled = true
                Toast.makeText(
                    this@ProfileSetupActivity,
                    "Could not create account: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}