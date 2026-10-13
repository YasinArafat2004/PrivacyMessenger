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

            loadMessages()
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

        val content =
            etMessage.text.toString().trim()

        if (content.isEmpty()) {
            return
        }

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

                val message = Message(
                    chatUsername = chatUsername,
                    senderUsername = currentUsername,
                    content = content
                )

                withContext(Dispatchers.IO) {
                    database.messageDao()
                        .insertMessage(message)
                }

                etMessage.text.clear()

                loadMessages()

            } catch (e: Exception) {

                Toast.makeText(
                    this@ChatActivity,
                    "Could not save message.",
                    Toast.LENGTH_SHORT
                ).show()

            } finally {

                btnSend.isEnabled = true
            }
        }
    }
}