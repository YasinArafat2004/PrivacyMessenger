
package com.yasin.privacymessenger.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatUsername"]),
        Index(value = ["serverMessageId"], unique = true),
        Index(value = ["clientMessageId"], unique = true)
    ]
)
data class Message(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val chatUsername: String,

    val senderUsername: String,

    val content: String,

    val timestamp: Long = System.currentTimeMillis(),

    // Kept for compatibility with messages from the old HTTP system.
    val serverMessageId: Int? = null,

    // UUID generated locally and reused when retrying a message.
    val clientMessageId: String? = null,

    @ColumnInfo(defaultValue = "1")
    val status: Int = STATUS_SENT,

    // Deleted messages remain as tombstones to prevent restoration.
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false

) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SENT = 1
        const val STATUS_FAILED = 2
    }
}
