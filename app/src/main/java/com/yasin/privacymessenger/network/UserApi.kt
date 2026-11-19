package com.yasin.privacymessenger.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class UserInfo(val username: String, val name: String)
data class RegisterRequest(val username: String, val name: String)

data class FriendRequestSend(val fromUsername: String, val toUsername: String)
data class FriendRequestItem(
    val id: Int,
    val senderUsername: String,
    val senderName: String,
    val createdAt: String
)
data class SentRequestItem(
    val id: Int,
    val receiverUsername: String,
    val receiverName: String,
    val status: String,
    val updatedAt: String
)
data class FriendRespondRequest(val username: String, val accepted: Boolean)
data class FriendRequestResponse(val id: Int, val status: String)

interface UserApi {

    @POST("api/users/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiResponse<UserInfo>>

    @GET("api/users/search")
    suspend fun search(@Query("username") username: String): Response<ApiResponse<UserInfo?>>

    @POST("api/friend-requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestSend): Response<ApiResponse<FriendRequestResponse>>

    @GET("api/friend-requests/received")
    suspend fun getReceivedRequests(@Query("username") username: String): Response<ApiResponse<List<FriendRequestItem>>>

    @GET("api/friend-requests/sent")
    suspend fun getSentRequests(@Query("username") username: String): Response<ApiResponse<List<SentRequestItem>>>

    @POST("api/friend-requests/{id}/respond")
    suspend fun respondToRequest(
        @Path("id") id: Int,
        @Body body: FriendRespondRequest
    ): Response<ApiResponse<FriendRequestResponse>>
}