package com.yasin.privacymessenger

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var btnFindPeople: Button

    private lateinit var btnFriendRequests: Button

    private lateinit var btnContacts: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
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

        tvWelcome = findViewById(R.id.tvWelcome)
        btnFindPeople = findViewById(R.id.btnFindPeople)
        btnFriendRequests = findViewById(R.id.btnFriendRequests)
        btnContacts = findViewById(R.id.navContacts)

        loadAccount()

        btnFindPeople.setOnClickListener {

            val intent = Intent(
                this,
                FindPeopleActivity::class.java
            )

            startActivity(intent)
        }
        btnFriendRequests.setOnClickListener {

            val intent = Intent(
                this,
                FriendRequestsActivity::class.java
            )

            startActivity(intent)
        }
        btnContacts.setOnClickListener {

            val intent = Intent(
                this,
                ContactsActivity::class.java
            )

            startActivity(intent)
        }
    }

    private fun loadAccount() {

        val database = AppDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {

            val account =
                database.userAccountDao().getAccount()

            withContext(Dispatchers.Main) {

                if (account != null) {

                    tvWelcome.text =
                        "Hello, ${account.name}"

                } else {

                    tvWelcome.text =
                        "Hello, User"
                }
            }
        }
    }
}