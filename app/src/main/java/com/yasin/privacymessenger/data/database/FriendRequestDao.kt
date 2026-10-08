package com.yasin.privacymessenger.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FriendRequestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequest)

    @Query("""
        SELECT * FROM friend_requests
        WHERE receiverUsername = :username
        AND status = 'PENDING'
        ORDER BY createdAt DESC
    """)
    suspend fun getReceivedRequests(
        username: String
    ): List<FriendRequest>

    @Query("""
        SELECT * FROM friend_requests
        WHERE senderUsername = :username
        ORDER BY createdAt DESC
    """)
    suspend fun getSentRequests(
        username: String
    ): List<FriendRequest>

    @Query("""
        UPDATE friend_requests
        SET status = :newStatus
        WHERE id = :requestId
    """)
    suspend fun updateRequestStatus(
        requestId: Int,
        newStatus: String
    )

    @Query("""
        SELECT * FROM friend_requests
        WHERE senderUsername = :sender
        AND receiverUsername = :receiver
        LIMIT 1
    """)
    suspend fun findRequest(
        sender: String,
        receiver: String
    ): FriendRequest?

    @Query("DELETE FROM friend_requests WHERE id = :requestId")
    suspend fun deleteRequest(requestId: Int)
}