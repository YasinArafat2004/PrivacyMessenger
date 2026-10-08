package com.yasin.privacymessenger.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: UserAccount)

    @Query("SELECT * FROM user_account WHERE id = 1 LIMIT 1")
    suspend fun getAccount(): UserAccount?

    @Query("DELETE FROM user_account")
    suspend fun deleteAccount()
}