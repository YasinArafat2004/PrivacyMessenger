
package com.yasin.privacymessenger.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message): Long

    @Query("""
        SELECT * FROM messages
        WHERE chatUsername = :chatUsername
        AND isDeleted = 0
        ORDER BY id ASC
    """)
    fun observeMessages(chatUsername: String): Flow<List<Message>>

    @Query("""
        SELECT * FROM messages
        WHERE id IN (
            SELECT MAX(id)
            FROM messages
            WHERE isDeleted = 0
            GROUP BY chatUsername
        )
        ORDER BY id DESC
    """)
    fun observeLatestMessages(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Int): Message?

    @Query("""
        SELECT * FROM messages
        WHERE serverMessageId = :serverMessageId
        LIMIT 1
    """)
    suspend fun getMessageByServerId(serverMessageId: Int): Message?

    @Query("""
        SELECT * FROM messages
        WHERE clientMessageId = :clientMessageId
        LIMIT 1
    """)
    suspend fun getMessageByClientId(clientMessageId: String): Message?

    @Query("""
        SELECT * FROM messages
        WHERE chatUsername = :chatUsername
        AND senderUsername = :sender
        AND status = 0
        AND isDeleted = 0
        ORDER BY id ASC
    """)
    suspend fun getPendingMessages(
        chatUsername: String,
        sender: String
    ): List<Message>

    // Retained for compatibility with the previous HTTP implementation.
    @Query("""
        SELECT * FROM messages
        WHERE chatUsername = :chatUsername
        AND senderUsername = :sender
        AND content = :content
        AND serverMessageId IS NULL
        ORDER BY id ASC
        LIMIT 1
    """)
    suspend fun findUnlinkedOwnMessage(
        chatUsername: String,
        sender: String,
        content: String
    ): Message?

    @Query("""
        UPDATE messages
        SET serverMessageId = :serverMessageId,
            timestamp = :timestamp,
            status = 1
        WHERE id = :id
    """)
    suspend fun markSent(
        id: Int,
        serverMessageId: Int?,
        timestamp: Long
    )

    // Assign a stable ID to an older message that has no clientMessageId.
    @Query("""
        UPDATE messages
        SET clientMessageId = :clientMessageId
        WHERE id = :id
        AND (clientMessageId IS NULL OR clientMessageId = '')
        AND isDeleted = 0
    """)
    suspend fun assignClientMessageId(
        id: Int,
        clientMessageId: String
    ): Int

    // Mark an outgoing Socket.IO message as delivered.
    @Query("""
        UPDATE messages
        SET status = 1,
            timestamp = :timestamp
        WHERE clientMessageId = :clientMessageId
        AND isDeleted = 0
    """)
    suspend fun markDeliveredByClientId(
        clientMessageId: String,
        timestamp: Long
    )

    @Query("""
        UPDATE messages
        SET status = :status
        WHERE id = :id
    """)
    suspend fun setStatus(
        id: Int,
        status: Int
    )

    @Query("""
        UPDATE messages
        SET status = :status
        WHERE clientMessageId = :clientMessageId
        AND isDeleted = 0
    """)
    suspend fun setStatusByClientId(
        clientMessageId: String,
        status: Int
    )

    @Query("""
        UPDATE messages
        SET isDeleted = 1
        WHERE id = :messageId
        AND chatUsername = :chatUsername
    """)
    suspend fun deleteMessage(
        messageId: Int,
        chatUsername: String
    )

    @Query("""
        UPDATE messages
        SET isDeleted = 1
        WHERE chatUsername = :chatUsername
    """)
    suspend fun clearChat(chatUsername: String)
}
