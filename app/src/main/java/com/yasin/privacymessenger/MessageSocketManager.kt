
package com.yasin.privacymessenger

import android.content.Context
import android.util.Log
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

object MessageSocketManager {

    private const val TAG = "MessageSocketManager"

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    fun start(context: Context) {

        if (started) return

        synchronized(this) {

            if (started) return

            started = true
        }

        val database =
            AppDatabase.getDatabase(context.applicationContext)

        val messageDao = database.messageDao()

        // Listen for incoming messages.
        scope.launch {

            CallManager.incomingMessages.collect { data ->

                try {

                    saveIncomingMessage(
                        data,
                        database,
                        messageDao
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Incoming message processing failed",
                        e
                    )
                }
            }
        }

        // Listen for delivery acknowledgements.
        scope.launch {

            CallManager.messageDelivered.collect { data ->

                try {

                    val clientMessageId =
                        data.optString("clientMessageId")

                    if (clientMessageId.isBlank()) {
                        return@collect
                    }

                    messageDao.markDeliveredByClientId(
                        clientMessageId,
                        System.currentTimeMillis()
                    )

                    Log.d(
                        TAG,
                        "Message acknowledged: $clientMessageId"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Delivery acknowledgement processing failed",
                        e
                    )
                }
            }
        }

        // Listen for failed message deliveries.
        scope.launch {

            CallManager.messageUndelivered.collect { data ->

                try {

                    val clientMessageId =
                        data.optString("clientMessageId")

                    if (clientMessageId.isBlank()) {
                        return@collect
                    }

                    val reason = data.optString("reason")

                    val status = when (reason) {

                        "USER_OFFLINE",
                        "SERVER_ERROR",
                        "NOT_REGISTERED" -> {
                            Message.STATUS_PENDING
                        }

                        else -> {
                            Message.STATUS_FAILED
                        }
                    }

                    messageDao.setStatusByClientId(
                        clientMessageId,
                        status
                    )

                    Log.w(
                        TAG,
                        "Message not delivered: $clientMessageId, reason=$reason"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Undelivered message processing failed",
                        e
                    )
                }
            }
        }

        Log.d(TAG, "Message event listeners started")
    }

    private suspend fun saveIncomingMessage(
        data: JSONObject,
        database: AppDatabase,
        messageDao: com.yasin.privacymessenger.data.database.MessageDao
    ) {

        val clientMessageId =
            data.optString("clientMessageId")

        val senderUsername =
            data.optString("senderUsername")

        val receiverUsername =
            data.optString("receiverUsername")

        val content =
            data.optString("content")

        if (
            clientMessageId.isBlank() ||
            senderUsername.isBlank() ||
            receiverUsername.isBlank() ||
            content.isBlank()
        ) {

            Log.w(TAG, "Ignoring invalid incoming message")
            return
        }

        // Confirm that the message belongs to the local account.
        val account = database.userAccountDao().getAccount()
            ?: return

        if (account.username != receiverUsername) {

            Log.w(TAG, "Message belongs to another account")
            return
        }

        val timestamp = parseTimestamp(
            data.optString("timestamp")
        )

        // Insert locally. The unique clientMessageId index
        // prevents duplicate copies of the same message.
        messageDao.insertMessage(
            Message(
                chatUsername = senderUsername,
                senderUsername = senderUsername,
                content = content,
                timestamp = timestamp,
                clientMessageId = clientMessageId,
                status = Message.STATUS_SENT
            )
        )

        // Acknowledge only after confirming local storage.
        val savedMessage =
            messageDao.getMessageByClientId(clientMessageId)

        if (savedMessage != null) {

            CallManager.acknowledgeMessage(
                clientMessageId,
                senderUsername
            )

            Log.d(
                TAG,
                "Incoming message saved: $clientMessageId"
            )
        }
    }

    private fun parseTimestamp(raw: String): Long {

        return try {

            Instant.parse(raw).toEpochMilli()

        } catch (_: Exception) {

            try {

                OffsetDateTime.parse(raw)
                    .toInstant()
                    .toEpochMilli()

            } catch (_: Exception) {

                System.currentTimeMillis()
            }
        }
    }
}
