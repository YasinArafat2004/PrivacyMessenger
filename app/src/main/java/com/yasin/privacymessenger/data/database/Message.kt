
package com.yasin.privacymessenger.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val chatUsername: String,

    val senderUsername: String,

    val content: String,

    val timestamp: Long = System.currentTimeMillis()
)