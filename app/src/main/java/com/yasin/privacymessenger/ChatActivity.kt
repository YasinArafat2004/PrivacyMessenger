
package com.yasin.privacymessenger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PrivacyMessenger"
        private const val MAX_MESSAGE_LENGTH = 4000
        private const val RETRY_CHECK_INTERVAL = 5000L
        private const val RETRY_INTERVAL = 15000L
    }

    private lateinit var tvChatTitle: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnScrollDown: ImageButton
    private lateinit var scrollDownContainer: View
    private lateinit var tvUnreadBadge: TextView

    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var messageAdapter: MessageAdapter

    private var chatUsername: String = ""
    private var currentUsername: String? = null

    // Prevents simultaneous attempts for the same chat.
    private val sendMutex = Mutex()

    // Tracks recent attempts during this Activity's lifetime.
    // The actual messages remain safely stored in Room.
    private val lastAttemptAt = mutableMapOf<Int, Long>()

    private var initialLoadDone = false
    private var lastMessageId = -1
    private var unreadCount = 0
    private var pendingScrollToEnd = false

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

            val ime =
                insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, ime.bottom)
            )

            insets
        }

        tvChatTitle = findViewById(R.id.tvChatTitle)
        tvAvatar = findViewById(R.id.tvAvatar)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)
        btnMenu = findViewById(R.id.btnMenu)
        btnScrollDown = findViewById(R.id.btnScrollDown)
        scrollDownContainer = findViewById(R.id.scrollDownContainer)
        tvUnreadBadge = findViewById(R.id.tvUnreadBadge)

        chatUsername = intent.getStringExtra("username") ?: ""

        val displayName =
            intent.getStringExtra("display_name") ?: chatUsername

        tvChatTitle.text = displayName
        tvAvatar.text =
            displayName.firstOrNull()?.uppercase() ?: "?"

        btnBack.setOnClickListener {
            finish()
        }

        btnMenu.setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, "Clear chat")

                setOnMenuItemClickListener {
                    showClearChatConfirmation()
                    true
                }

                show()
            }
        }

        layoutManager =
            LinearLayoutManager(this).apply {
                stackFromEnd = true
            }

        rvMessages.layoutManager = layoutManager

        (rvMessages.itemAnimator as? SimpleItemAnimator)
            ?.supportsChangeAnimations = false

        rvMessages.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int
                ) {
                    updateScrollDownButton()
                }
            }
        )

        btnScrollDown.setOnClickListener {
            unreadCount = 0
            scrollToEnd(smooth = false)
            updateScrollDownButton()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        loadCurrentUsername()

        // Call button
        val btnCall = findViewById<ImageButton>(R.id.btnCall)

        btnCall.setOnClickListener {
            val callIntent =
                Intent(this, CallActivity::class.java)

            callIntent.putExtra("isIncoming", false)
            callIntent.putExtra("username", chatUsername)

            startActivity(callIntent)
        }
    }

    // ==========================================
    // LOAD CURRENT ACCOUNT
    // ==========================================

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

            messageAdapter = MessageAdapter(
                currentUsername = account.username,
                onDeleteMessage = { message ->
                    deleteMessage(message)
                },
                onRetryMessage = { message ->
                    retryMessage(message)
                }
            )

            rvMessages.adapter = messageAdapter

            startObservingMessages()
            startSocketRetry()
        }
    }

    // ==========================================
    // OBSERVE LOCAL ROOM MESSAGES
    // ==========================================

    private fun startObservingMessages() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                database.messageDao()
                    .observeMessages(chatUsername)
                    .collect { messages ->

                        render(messages)
                    }
            }
        }
    }

    private fun render(messages: List<Message>) {

        val me = currentUsername ?: return

        val wasNearBottom = isNearBottom()
        val previousLastId = lastMessageId
        val isFirstRender = !initialLoadDone

        val newMessages =
            messages.filter { it.id > previousLastId }

        val hasNewMessage = newMessages.isNotEmpty()

        val newOwn =
            newMessages.any { it.senderUsername == me }

        val newIncoming =
            newMessages.count { it.senderUsername != me }

        lastMessageId =
            messages.lastOrNull()?.id ?: -1

        initialLoadDone = true

        when {

            isFirstRender -> {
                pendingScrollToEnd = true
            }

            hasNewMessage && (newOwn || wasNearBottom) -> {
                pendingScrollToEnd = true
            }

            newIncoming > 0 -> {
                unreadCount += newIncoming
            }
        }

        val smooth =
            !isFirstRender &&
                    wasNearBottom &&
                    previousLastId >= 0

        messageAdapter.submitList(buildItems(messages)) {

            if (pendingScrollToEnd) {

                pendingScrollToEnd = false
                scrollToEnd(smooth)
            }

            updateScrollDownButton()
        }
    }

    // ==========================================
    // BUILD CHAT ITEMS
    // ==========================================

    private fun buildItems(
        messages: List<Message>
    ): List<ChatItem> {

        val items =
            ArrayList<ChatItem>(messages.size + 8)

        var lastDay = Long.MIN_VALUE
        var runningTimestamp = 0L

        for (message in messages) {

            runningTimestamp =
                maxOf(runningTimestamp, message.timestamp)

            val day = startOfDay(runningTimestamp)

            if (day != lastDay) {

                items.add(
                    ChatItem.DateHeader(
                        day,
                        dayLabel(day)
                    )
                )

                lastDay = day
            }

            items.add(ChatItem.Bubble(message))
        }

        return items
    }

    private fun startOfDay(timestamp: Long): Long {

        return Calendar.getInstance().apply {

            timeInMillis = timestamp

            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

        }.timeInMillis
    }

    private fun dayLabel(dayStart: Long): String {

        return when {

            DateUtils.isToday(
                dayStart + DateUtils.HOUR_IN_MILLIS * 12
            ) -> "Today"

            DateUtils.isToday(
                dayStart + DateUtils.HOUR_IN_MILLIS * 36
            ) -> "Yesterday"

            else -> DateUtils.formatDateTime(
                this,
                dayStart,
                DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_ALL
            )
        }
    }

    // ==========================================
    // SCROLL MANAGEMENT
    // ==========================================

    private fun isNearBottom(): Boolean {

        val count =
            rvMessages.adapter?.itemCount ?: 0

        if (count == 0) return true

        return layoutManager.findLastVisibleItemPosition() >= count - 1
    }

    private fun scrollToEnd(smooth: Boolean) {

        val last =
            (rvMessages.adapter?.itemCount ?: 0) - 1

        if (last < 0) return

        if (smooth) {
            rvMessages.smoothScrollToPosition(last)
        } else {
            rvMessages.scrollToPosition(last)
        }
    }

    private fun updateScrollDownButton() {

        if (rvMessages.adapter == null) return

        if (isNearBottom()) {

            unreadCount = 0

            scrollDownContainer.visibility = View.GONE
            tvUnreadBadge.visibility = View.GONE

            return
        }

        scrollDownContainer.visibility = View.VISIBLE

        if (unreadCount > 0) {

            tvUnreadBadge.text = unreadCount.toString()
            tvUnreadBadge.visibility = View.VISIBLE

        } else {

            tvUnreadBadge.visibility = View.GONE
        }
    }

    // ==========================================
    // SEND MESSAGE - SAVE LOCALLY FIRST
    // ==========================================

    private fun sendMessage() {

        val content =
            etMessage.text.toString().trim()

        if (content.isEmpty()) return

        if (chatUsername.isBlank()) {

            Toast.makeText(
                this,
                "Chat recipient is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (content.length > MAX_MESSAGE_LENGTH) {

            Toast.makeText(
                this,
                "Message is too long.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val me = currentUsername

        if (me == null) {

            Toast.makeText(
                this,
                "Please wait for your account to load.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Generate the ID once.
        // The same ID is reused for every retry.
        val clientMessageId =
            UUID.randomUUID().toString()

        lifecycleScope.launch {

            try {

                val localId = withContext(Dispatchers.IO) {

                    database.messageDao().insertMessage(
                        Message(
                            chatUsername = chatUsername,
                            senderUsername = me,
                            content = content,
                            clientMessageId = clientMessageId,
                            status = Message.STATUS_PENDING
                        )
                    )
                }

                if (localId <= 0L) {

                    Toast.makeText(
                        this@ChatActivity,
                        "Could not save message on this device.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                // Clear the input only after Room has saved it.
                etMessage.text.clear()

                // Try Socket.IO delivery.
                sendPendingMessage(localId.toInt())

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(TAG, "Could not save local message", e)

                Toast.makeText(
                    this@ChatActivity,
                    "Could not save message on this device.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ==========================================
    // SOCKET.IO MESSAGE SENDING
    // ==========================================

    private suspend fun sendPendingMessage(
        localId: Int,
        force: Boolean = false
    ) {

        sendMutex.withLock {

            val me = currentUsername ?: return@withLock

            val dao = database.messageDao()

            var message = withContext(Dispatchers.IO) {
                dao.getMessageById(localId)
            } ?: return@withLock

            // Never send deleted or already delivered messages.
            if (
                message.isDeleted ||
                message.senderUsername != me ||
                message.chatUsername != chatUsername ||
                message.status == Message.STATUS_SENT
            ) {
                return@withLock
            }

            val now = System.currentTimeMillis()

            val previousAttempt =
                lastAttemptAt[localId] ?: 0L

            if (
                !force &&
                now - previousAttempt < RETRY_INTERVAL
            ) {
                return@withLock
            }

            // Older pending messages may not have a client ID.
            // Assign one and save it before sending.
            if (message.clientMessageId.isNullOrBlank()) {

                val newClientMessageId =
                    UUID.randomUUID().toString()

                val updated = withContext(Dispatchers.IO) {

                    dao.assignClientMessageId(
                        id = localId,
                        clientMessageId = newClientMessageId
                    )
                }

                if (updated <= 0) {
                    return@withLock
                }

                message = withContext(Dispatchers.IO) {
                    dao.getMessageById(localId)
                } ?: return@withLock
            }

            val clientMessageId =
                message.clientMessageId ?: return@withLock

            val acceptedBySocket = CallManager.sendMessage(
                clientMessageId = clientMessageId,
                receiverUsername = message.chatUsername,
                content = message.content
            )

            if (acceptedBySocket) {

                // This means Socket.IO accepted the emit.
                // It does NOT mean the recipient received the message.
                // Keep the message PENDING until delivery acknowledgement.
                lastAttemptAt[localId] = now

                Log.d(
                    TAG,
                    "Message submitted through Socket.IO: $clientMessageId"
                )

            } else {

                Log.d(
                    TAG,
                    "Socket not ready. Message remains pending: $clientMessageId"
                )
            }
        }
    }

    // ==========================================
    // RETRY PENDING MESSAGES
    // ==========================================

    private fun startSocketRetry() {

        // Retry immediately after successful socket registration.
        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                CallManager.registered.collect { isRegistered ->

                    if (isRegistered) {
                        retryPendingMessages()
                    }
                }
            }
        }

        // Also retry periodically while the chat is visible.
        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.RESUMED) {

                while (true) {

                    retryPendingMessages()

                    delay(RETRY_CHECK_INTERVAL)
                }
            }
        }
    }

    private suspend fun retryPendingMessages() {

        val me = currentUsername ?: return

        if (!CallManager.registered.value) return

        val pending = withContext(Dispatchers.IO) {

            database.messageDao().getPendingMessages(
                chatUsername = chatUsername,
                sender = me
            )
        }

        for (message in pending) {

            sendPendingMessage(message.id)
        }
    }

    // ==========================================
    // MANUAL RETRY FROM MESSAGE ADAPTER
    // ==========================================

    private fun retryMessage(message: Message) {

        lifecycleScope.launch {

            try {

                withContext(Dispatchers.IO) {

                    database.messageDao().setStatus(
                        message.id,
                        Message.STATUS_PENDING
                    )
                }

                // Allow an immediate retry.
                lastAttemptAt.remove(message.id)

                sendPendingMessage(
                    localId = message.id,
                    force = true
                )

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(TAG, "Could not retry message", e)
            }
        }
    }

    // ==========================================
    // DELETE MESSAGE
    // ==========================================

    private fun deleteMessage(message: Message) {

        lifecycleScope.launch {

            try {

                withContext(Dispatchers.IO) {

                    database.messageDao().deleteMessage(
                        messageId = message.id,
                        chatUsername = chatUsername
                    )
                }

                lastAttemptAt.remove(message.id)

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(TAG, "Could not delete message", e)

                Toast.makeText(
                    this@ChatActivity,
                    "Could not delete message.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ==========================================
    // CLEAR CHAT
    // ==========================================

    private fun showClearChatConfirmation() {

        AlertDialog.Builder(this)
            .setTitle("Clear this chat?")
            .setMessage(
                "All messages in this chat will be removed from this device."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear chat") { _, _ ->
                clearCurrentChat()
            }
            .show()
    }

    private fun clearCurrentChat() {

        lifecycleScope.launch {

            try {

                withContext(Dispatchers.IO) {

                    database.messageDao().clearChat(chatUsername)
                }

                lastAttemptAt.clear()

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Log.e(TAG, "Could not clear chat", e)

                Toast.makeText(
                    this@ChatActivity,
                    "Could not clear chat.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
