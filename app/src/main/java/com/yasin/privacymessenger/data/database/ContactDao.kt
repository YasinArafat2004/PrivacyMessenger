package com.yasin.privacymessenger.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Query("""
        SELECT * FROM contacts
        ORDER BY displayName ASC
    """)
    suspend fun getAllContacts(): List<Contact>

    @Query("""
        SELECT * FROM contacts
        WHERE username = :username
        LIMIT 1
    """)
    suspend fun getContact(
        username: String
    ): Contact?

    @Query("""
        DELETE FROM contacts
        WHERE username = :username
    """)
    suspend fun deleteContact(
        username: String
    )
}