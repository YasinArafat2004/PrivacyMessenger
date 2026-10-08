
package com.yasin.privacymessenger.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert
    suspend fun insertMessage(message: Message)

    @Query("""
        SELECT * FROM messages
        WHERE chatUsername = :chatUsername
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesForChat(
        chatUsername: String
    ): List<Message>
}