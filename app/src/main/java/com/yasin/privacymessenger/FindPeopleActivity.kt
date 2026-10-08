package com.yasin.privacymessenger

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.FriendRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FindPeopleActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var etSearchUsername: EditText
    private lateinit var btnSearch: Button

    private lateinit var resultContainer: LinearLayout
    private lateinit var tvResultName: TextView
    private lateinit var tvResultUsername: TextView
    private lateinit var btnAddFriend: Button
    private lateinit var tvSearchMessage: TextView

    private var currentResultUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_find_people)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        btnBack = findViewById(R.id.btnBack)
        etSearchUsername = findViewById(R.id.etSearchUsername)
        btnSearch = findViewById(R.id.btnSearch)

        resultContainer =
            findViewById(R.id.resultContainer)

        tvResultName =
            findViewById(R.id.tvResultName)

        tvResultUsername =
            findViewById(R.id.tvResultUsername)

        btnAddFriend =
            findViewById(R.id.btnAddFriend)

        tvSearchMessage =
            findViewById(R.id.tvSearchMessage)

        btnBack.setOnClickListener {
            finish()
        }

        btnSearch.setOnClickListener {
            searchPerson()
        }

        btnAddFriend.setOnClickListener {
            sendFriendRequest()
        }
    }

    private fun searchPerson() {

        val username =
            etSearchUsername.text.toString()
                .trim()
                .removePrefix("@")

        if (username.isEmpty()) {
            etSearchUsername.error =
                "Enter a username"

            etSearchUsername.requestFocus()
            return
        }

        /*
         * Development-only users.
         *
         * Later this search will use our backend.
         */

        when (username.lowercase()) {

            "rahim123" -> {

                currentResultUsername = "rahim123"

                tvResultName.text = "Rahim"
                tvResultUsername.text = "@rahim123"

                resultContainer.visibility =
                    View.VISIBLE

                tvSearchMessage.text = ""
            }

            "karim123" -> {

                currentResultUsername = "karim123"

                tvResultName.text = "Karim"
                tvResultUsername.text = "@karim123"

                resultContainer.visibility =
                    View.VISIBLE

                tvSearchMessage.text = ""
            }

            else -> {

                currentResultUsername = ""

                resultContainer.visibility =
                    View.GONE

                tvSearchMessage.text =
                    "No user found"

            }
        }
    }

    private fun sendFriendRequest() {

        if (currentResultUsername.isEmpty()) {
            return
        }

        val database =
            AppDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {

            val account =
                database.userAccountDao()
                    .getAccount()

            if (account == null) {

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@FindPeopleActivity,
                        "Account not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return@launch
            }

            if (
                account.username.equals(
                    currentResultUsername,
                    ignoreCase = true
                )
            ) {

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@FindPeopleActivity,
                        "You cannot add yourself",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return@launch
            }

            val existingRequest =
                database.friendRequestDao()
                    .findRequest(
                        account.username,
                        currentResultUsername
                    )

            if (existingRequest != null) {

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@FindPeopleActivity,
                        "Friend request already sent",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return@launch
            }

            val request = FriendRequest(

                senderUsername =
                    currentResultUsername,

                receiverUsername =
                    account.username,

                status = "PENDING"
            )

            database.friendRequestDao()
                .insertRequest(request)

            withContext(Dispatchers.Main) {

                btnAddFriend.isEnabled = false

                btnAddFriend.text =
                    "Request Sent"

                Toast.makeText(
                    this@FindPeopleActivity,
                    "Development request created!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}