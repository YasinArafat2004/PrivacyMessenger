package com.yasin.privacymessenger.network

data class MessageRequest(
    val senderUsername: String,
    val receiverUsername: String,
    val content: String
)
