package com.yasin.privacymessenger

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.view.Gravity
import com.yasin.privacymessenger.network.MessageRequest
import com.yasin.privacymessenger.network.RetrofitClient
import android.util.Log
import java.time.Instant

class ChatActivity : AppCompatActivity() {

    private lateinit var tvChatTitle: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClearChat: Button
    private lateinit var chatUsername: String
    private lateinit var currentUsername: String

    private lateinit var messageAdapter: MessageAdapter

    private val database by lazy {
        AppDatabase.getDatabase(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

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
        btnClearChat = findViewById(R.id.btnClearChat)

        btnClearChat.setOnClickListener {
            showClearChatConfirmation()
        }

        tvChatTitle = findViewById(R.id.tvChatTitle)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        chatUsername =
            intent.getStringExtra("username") ?: ""

        val displayName =
            intent.getStringExtra("display_name")
                ?: chatUsername

        tvChatTitle.text = displayName

        rvMessages.layoutManager =
            LinearLayoutManager(this)

        loadCurrentUsername()

        btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun showClearChatConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear chat?")
            .setMessage(
                "This will permanently delete all messages in this conversation from this device."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                clearCurrentChat()
            }
            .show()
    }

    private fun clearCurrentChat() {
        btnClearChat.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.messageDao().clearChat(chatUsername)
                }

                loadMessages()

                Toast.makeText(
                    this@ChatActivity,
                    "Chat cleared on this device.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatActivity,
                    "Could not clear chat.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnClearChat.isEnabled = true
            }
        }
    }

    private fun loadCurrentUsername() {

        lifecycleScope.launch {

            val account = withContext(Dispatchers.IO) {
                database.userAccountDao().getAccount()
            }

            if (account == null) {

                Toast.makeText(
                    this@ChatActivity,
                    "Could not load your account.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
                return@launch
            }

            currentUsername = account.username

            messageAdapter = MessageAdapter(currentUsername) { message ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.messageDao().deleteMessage(
                                messageId = message.id,
                                chatUsername = chatUsername
                            )
                        }

                        loadMessages()
                        Toast.makeText(
                            this@ChatActivity,
                            "Message deleted from this device.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ChatActivity,
                            "Could not delete message.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            rvMessages.adapter = messageAdapter

// Show locally stored messages immediately.
            loadMessages()

// Retrieve messages from the server, then refresh the chat.
            lifecycleScope.launch {
                syncMessagesFromServer()
                loadMessages()
            }
        }
    }

    private fun loadMessages() {

        lifecycleScope.launch {

            val messages = withContext(Dispatchers.IO) {
                database.messageDao()
                    .getMessagesForChat(chatUsername)
            }

            messageAdapter.setMessages(messages)

            if (messages.isNotEmpty()) {
                rvMessages.scrollToPosition(
                    messages.size - 1
                )
            }
        }
    }


    private fun sendMessage() {
        val content = etMessage.text.toString().trim()

        if (content.isEmpty()) return

        if (chatUsername.isBlank()) {
            Toast.makeText(
                this,
                "Chat recipient is missing.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!::currentUsername.isInitialized) {
            Toast.makeText(
                this,
                "Please wait for your account to load.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        btnSend.isEnabled = false

        lifecycleScope.launch {
            try {
                var serverSaved = false
                var serverMessageId: Int? = null

                // Step 1: Send the message to the backend.
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.api.sendMessage(
                            MessageRequest(
                                senderUsername = currentUsername,
                                receiverUsername = chatUsername,
                                content = content
                            )
                        )
                    }

                    val body = response.body()

                    serverSaved =
                        response.isSuccessful && body?.success == true

                    if (serverSaved) {
                        serverMessageId = body?.data?.id
                    }

                } catch (e: Exception) {
                    Log.e(
                        "PrivacyMessengerAPI",
                        "Message upload failed: ${e.message}",
                        e
                    )
                }

                // Step 2: Save the message locally.
                val localMessage = Message(
                    chatUsername = chatUsername,
                    senderUsername = currentUsername,
                    content = content,
                    serverMessageId = serverMessageId
                )

                withContext(Dispatchers.IO) {
                    database.messageDao().insertMessage(localMessage)
                }

                // Step 3: Refresh the conversation.
                etMessage.text.clear()
                loadMessages()

                // Step 4: Show the result.
                val status = if (serverSaved) {
                    "Message saved locally and on the server."
                } else {
                    "Message saved on this device, but server sync failed."
                }

                Toast.makeText(
                    this@ChatActivity,
                    status,
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Log.e(
                    "PrivacyMessengerAPI",
                    "Could not save local message: ${e.message}",
                    e
                )

                Toast.makeText(
                    this@ChatActivity,
                    "Could not save message on this device.",
                    Toast.LENGTH_SHORT
                ).show()

            } finally {
                btnSend.isEnabled = true
            }
        }
    }

    private suspend fun syncMessagesFromServer() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.api.getMessages(currentUsername)
            }

            if (!response.isSuccessful) {
                Log.e(
                    "PrivacyMessengerAPI",
                    "Failed to retrieve messages. HTTP ${response.code()}"
                )
                return
            }

            val serverMessages = response.body()?.data ?: return

            withContext(Dispatchers.IO) {
                for (serverMessage in serverMessages) {

                    // Only import messages belonging to this conversation.
                    val belongsToChat =
                        (serverMessage.senderUsername == currentUsername &&
                                serverMessage.receiverUsername == chatUsername) ||
                                (serverMessage.senderUsername == chatUsername &&
                                        serverMessage.receiverUsername == currentUsername)

                    if (!belongsToChat) continue

                    // Prevent importing the same server message twice.
                    val existingMessage = database.messageDao()
                        .getMessageByServerId(serverMessage.id)

                    if (existingMessage != null) continue

                    val localMessage = Message(
                        chatUsername = chatUsername,
                        senderUsername = serverMessage.senderUsername,
                        content = serverMessage.content,
                        timestamp = Instant.parse(
                            serverMessage.timestamp
                        ).toEpochMilli(),
                        serverMessageId = serverMessage.id
                    )

                    database.messageDao().insertMessage(localMessage)
                }
            }

        } catch (e: Exception) {
            Log.e(
                "PrivacyMessengerAPI",
                "Message synchronization failed: ${e.message}",
                e
            )
        }
    }
}