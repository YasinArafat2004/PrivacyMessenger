package com.yasin.privacymessenger.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val username: String,

    val displayName: String,

    val addedAt: Long = System.currentTimeMillis()
)