
package com.yasin.privacymessenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Contact
import com.yasin.privacymessenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvMenu: TextView
    private lateinit var btnFriendRequests: Button
    private lateinit var fabFindPeople: FloatingActionButton
    private lateinit var btnContacts: TextView
    private lateinit var rvChats: RecyclerView
    private lateinit var emptyState: LinearLayout

    private lateinit var chatListAdapter: ChatListAdapter

    private val database by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

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

        tvWelcome = findViewById(R.id.tvWelcome)
        tvMenu = findViewById(R.id.tvMenu)
        btnFriendRequests = findViewById(R.id.btnFriendRequests)
        fabFindPeople = findViewById(R.id.fabFindPeople)
        btnContacts = findViewById(R.id.navContacts)
        rvChats = findViewById(R.id.rvChats)
        emptyState = findViewById(R.id.emptyState)

        setupRecyclerView()
        loadAccount()

        // Start the central Socket.IO message event listeners.
        MessageSocketManager.start(applicationContext)

        fabFindPeople.setOnClickListener {
            val intent = Intent(this, FindPeopleActivity::class.java)
            startActivity(intent)
        }

        btnFriendRequests.setOnClickListener {
            val intent = Intent(this, FriendRequestsActivity::class.java)
            startActivity(intent)
        }

        btnContacts.setOnClickListener {
            val intent = Intent(this, ContactsActivity::class.java)
            startActivity(intent)
        }

        tvMenu.setOnClickListener {
            showMenuDialog()
        }

        startChatListObserver()
        startFriendSync()

        // Initialize the existing Socket.IO and WebRTC manager.
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                database.userAccountDao().getAccount()
            }

            if (account != null) {
                CallManager.init(
                    this@HomeActivity,
                    account.username
                )
            }
        }
    }

    private fun setupRecyclerView() {
        chatListAdapter = ChatListAdapter { chat ->

            val intent = Intent(
                this,
                ChatActivity::class.java
            )

            intent.putExtra("username", chat.username)
            intent.putExtra("display_name", chat.displayName)

            startActivity(intent)
        }

        rvChats.layoutManager = LinearLayoutManager(this)
        rvChats.adapter = chatListAdapter
    }

    private fun loadAccount() {
        lifecycleScope.launch(Dispatchers.IO) {

            val account = database.userAccountDao().getAccount()

            withContext(Dispatchers.Main) {
                if (account != null) {
                    tvWelcome.text = "Chats"
                }
            }
        }
    }

    /**
     * Observes local messages and displays the latest message
     * from each conversation in the home screen.
     */
    private fun startChatListObserver() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                combine(
                    database.messageDao().observeLatestMessages(),
                    database.contactDao().getAllContactsFlow()
                ) { messages, contacts ->

                    val contactMap =
                        contacts.associateBy { it.username }

                    messages.map { msg ->

                        val displayName =
                            contactMap[msg.chatUsername]?.displayName
                                ?: msg.chatUsername

                        ChatPreview(
                            username = msg.chatUsername,
                            displayName = displayName,
                            lastMessage = msg.content,
                            timestamp = msg.timestamp
                        )
                    }

                }.collect { chats ->

                    if (chats.isEmpty()) {

                        emptyState.visibility = View.VISIBLE
                        rvChats.visibility = View.GONE

                    } else {

                        emptyState.visibility = View.GONE
                        rvChats.visibility = View.VISIBLE

                        chatListAdapter.submitList(chats)
                    }
                }
            }
        }
    }

    private fun showMenuDialog() {

        val options = arrayOf("Logout")

        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(options) { _, which ->

                if (which == 0) {
                    confirmLogout()
                }
            }
            .show()
    }

    private fun confirmLogout() {

        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .show()
    }

    private fun performLogout() {

        lifecycleScope.launch {

            withContext(Dispatchers.IO) {
                database.userAccountDao().deleteAccount()
            }

            val intent = Intent(
                this@HomeActivity,
                MainActivity::class.java
            )

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            finish()
        }
    }

    private fun startFriendSync() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.RESUMED) {

                while (true) {

                    syncAcceptedFriendRequests()

                    delay(4000)
                }
            }
        }
    }

    private suspend fun syncAcceptedFriendRequests() {

        try {

            val account = withContext(Dispatchers.IO) {
                database.userAccountDao().getAccount()
            } ?: return

            val response = withContext(Dispatchers.IO) {
                RetrofitClient.userApi.getSentRequests(
                    account.username
                )
            }

            if (!response.isSuccessful) return

            val sent = response.body()?.data ?: return

            withContext(Dispatchers.IO) {

                val existing = database.contactDao()
                    .getAllContacts()
                    .map { it.username }
                    .toSet()

                for (item in sent) {

                    if (
                        item.status == "ACCEPTED" &&
                        item.receiverUsername !in existing
                    ) {

                        database.contactDao().insertContact(
                            Contact(
                                username = item.receiverUsername,
                                displayName = item.receiverName
                            )
                        )
                    }
                }
            }

        } catch (_: Exception) {
            // Retry during the next synchronization cycle.
        }
    }
}
