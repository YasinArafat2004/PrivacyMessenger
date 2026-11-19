package com.yasin.privacymessenger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.network.BackendTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        BackendTest.testConnection()

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

        val btnGetStarted = findViewById<Button>(R.id.btnGetStarted)

        btnGetStarted.setOnClickListener {
            val intent = Intent(this, PhoneNumberActivity::class.java)
            startActivity(intent)
        }

        // Check if user is already logged in
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).userAccountDao().getAccount()
            }

            if (account != null) {
                // User is logged in, skip to HomeActivity
                val intent = Intent(this@MainActivity, HomeActivity::class.java)
                startActivity(intent)
                finish() // Close MainActivity so they can't press back to get here
            }
        }
    }
}