package com.yasin.privacymessenger.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_requests")
data class FriendRequest(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val senderUsername: String,

    val receiverUsername: String,

    val status: String = "PENDING",

    val createdAt: Long = System.currentTimeMillis()
)