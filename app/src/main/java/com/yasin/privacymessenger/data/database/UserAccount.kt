package com.yasin.privacymessenger.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserAccount(

    @PrimaryKey
    val id: Int = 1,

    val phoneNumber: String,

    val name: String,

    val username: String,

    val profilePicturePath: String? = null
)