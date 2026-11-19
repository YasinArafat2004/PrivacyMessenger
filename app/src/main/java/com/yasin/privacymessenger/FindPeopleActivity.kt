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
import com.yasin.privacymessenger.network.FriendRequestSend
import com.yasin.privacymessenger.network.RetrofitClient

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

        val username = etSearchUsername.text.toString().trim().removePrefix("@")

        if (username.isEmpty()) {
            etSearchUsername.error = "Enter a username"
            etSearchUsername.requestFocus()
            return
        }

        btnSearch.isEnabled = false
        tvSearchMessage.text = "Searching..."
        resultContainer.visibility = View.GONE
        currentResultUsername = ""

        lifecycleScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@FindPeopleActivity)
                        .userAccountDao()
                        .getAccount()
                }

                if (account != null && account.username.equals(username, ignoreCase = true)) {
                    tvSearchMessage.text = "You cannot add yourself"
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.search(username)
                }

                if (!response.isSuccessful) {
                    tvSearchMessage.text = "Server error (${response.code()})"
                    return@launch
                }

                val user = response.body()?.data

                if (user == null) {
                    tvSearchMessage.text = "No user found"
                    return@launch
                }

                currentResultUsername = user.username
                tvResultName.text = user.name
                tvResultUsername.text = "@${user.username}"
                resultContainer.visibility = View.VISIBLE
                tvSearchMessage.text = ""
                btnAddFriend.isEnabled = true
                btnAddFriend.text = "Add Friend"

            } catch (e: Exception) {
                tvSearchMessage.text = "Network error: ${e.message}"
            } finally {
                btnSearch.isEnabled = true
            }
        }
    }

    private fun sendFriendRequest() {

        if (currentResultUsername.isEmpty()) return

        btnAddFriend.isEnabled = false
        btnAddFriend.text = "Sending..."

        lifecycleScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@FindPeopleActivity)
                        .userAccountDao()
                        .getAccount()
                }

                if (account == null) {
                    Toast.makeText(this@FindPeopleActivity, "Account not found", Toast.LENGTH_SHORT).show()
                    btnAddFriend.isEnabled = true
                    btnAddFriend.text = "Add Friend"
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.sendFriendRequest(
                        FriendRequestSend(account.username, currentResultUsername)
                    )
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    btnAddFriend.text = "Request Sent"
                    Toast.makeText(this@FindPeopleActivity, "Friend request sent", Toast.LENGTH_SHORT).show()
                } else {
                    btnAddFriend.isEnabled = true
                    btnAddFriend.text = "Add Friend"
                    val msg = if (response.code() == 404) "User not found"
                    else "Failed (${response.code()})"
                    Toast.makeText(this@FindPeopleActivity, msg, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                btnAddFriend.isEnabled = true
                btnAddFriend.text = "Add Friend"
                Toast.makeText(this@FindPeopleActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}