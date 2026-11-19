package com.yasin.privacymessenger

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Contact
import com.yasin.privacymessenger.network.FriendRequestItem
import com.yasin.privacymessenger.network.FriendRespondRequest
import com.yasin.privacymessenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FriendRequestsActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var requestsContainer: LinearLayout
    private lateinit var tvEmptyRequests: TextView

    private val database by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_friend_requests)

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

        btnBack = findViewById(R.id.btnBack)
        requestsContainer = findViewById(R.id.requestsContainer)
        tvEmptyRequests = findViewById(R.id.tvEmptyRequests)

        btnBack.setOnClickListener { finish() }

        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun loadRequests() {

        lifecycleScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    database.userAccountDao().getAccount()
                }

                if (account == null) {
                    displayRequests(emptyList())
                    return@launch
                }

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.getReceivedRequests(account.username)
                }

                if (!response.isSuccessful) {
                    displayRequests(emptyList())
                    return@launch
                }

                displayRequests(response.body()?.data ?: emptyList())

            } catch (e: Exception) {
                displayRequests(emptyList())
            }
        }
    }

    private fun displayRequests(requests: List<FriendRequestItem>) {

        requestsContainer.removeAllViews()

        if (requests.isEmpty()) {
            tvEmptyRequests.visibility = android.view.View.VISIBLE
            return
        }

        tvEmptyRequests.visibility = android.view.View.GONE

        for (request in requests) {
            addRequestView(request)
        }
    }

    private fun addRequestView(request: FriendRequestItem) {

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(20, 20, 20, 20)
        card.setBackgroundColor(android.graphics.Color.WHITE)

        val name = TextView(this)
        name.text = request.senderName
        name.textSize = 19f
        name.setTextColor(android.graphics.Color.rgb(23, 32, 51))
        name.setTypeface(null, android.graphics.Typeface.BOLD)

        val username = TextView(this)
        username.text = "@${request.senderUsername}"
        username.textSize = 14f
        username.setTextColor(android.graphics.Color.rgb(104, 115, 134))

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END
        buttons.setPadding(0, 16, 0, 0)

        val acceptButton = Button(this)
        acceptButton.text = "Accept"

        val rejectButton = Button(this)
        rejectButton.text = "Reject"

        buttons.addView(acceptButton)
        buttons.addView(rejectButton)

        card.addView(name)
        card.addView(username)
        card.addView(buttons)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)

        requestsContainer.addView(card, params)

        acceptButton.setOnClickListener { acceptRequest(request) }
        rejectButton.setOnClickListener { rejectRequest(request) }
    }

    private fun acceptRequest(request: FriendRequestItem) {

        lifecycleScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    database.userAccountDao().getAccount()
                }

                if (account == null) return@launch

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.respondToRequest(
                        request.id,
                        FriendRespondRequest(account.username, true)
                    )
                }

                if (response.isSuccessful && response.body()?.success == true) {

                    withContext(Dispatchers.IO) {
                        val exists = database.contactDao()
                            .getAllContacts()
                            .any { it.username == request.senderUsername }

                        if (!exists) {
                            database.contactDao().insertContact(
                                Contact(
                                    username = request.senderUsername,
                                    displayName = request.senderName
                                )
                            )
                        }
                    }

                    Toast.makeText(
                        this@FriendRequestsActivity,
                        "Friend added!",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadRequests()
                } else {
                    Toast.makeText(
                        this@FriendRequestsActivity,
                        "Could not accept (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@FriendRequestsActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun rejectRequest(request: FriendRequestItem) {

        lifecycleScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    database.userAccountDao().getAccount()
                }

                if (account == null) return@launch

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.userApi.respondToRequest(
                        request.id,
                        FriendRespondRequest(account.username, false)
                    )
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@FriendRequestsActivity,
                        "Request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadRequests()
                } else {
                    Toast.makeText(
                        this@FriendRequestsActivity,
                        "Could not reject (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@FriendRequestsActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}