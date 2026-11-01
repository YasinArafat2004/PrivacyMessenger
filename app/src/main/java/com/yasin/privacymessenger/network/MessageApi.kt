package com.yasin.privacymessenger.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class ServerMessage(
    val id: Int,
    val senderUsername: String,
    val receiverUsername: String,
    val content: String,
    val timestamp: String
)

interface MessageApi {

    @GET("api/health")
    suspend fun checkHealth(): Response<ApiResponse<Nothing>>

    @POST("api/messages")
    suspend fun sendMessage(
        @Body request: MessageRequest
    ): Response<ApiResponse<ServerMessage>>

    @GET("api/messages")
    suspend fun getMessages(
        @Query("username") username: String
    ): Response<ApiResponse<List<ServerMessage>>>
}
