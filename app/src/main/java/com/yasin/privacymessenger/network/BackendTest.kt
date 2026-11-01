package com.yasin.privacymessenger.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackendTest {

    fun testConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.api.checkHealth()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Log.d(
                            "PrivacyMessengerAPI",
                            "Backend connected successfully!"
                        )
                    } else {
                        Log.e(
                            "PrivacyMessengerAPI",
                            "Backend error: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "PrivacyMessengerAPI",
                    "Connection failed: ${e.message}",
                    e
                )
            }
        }
    }
}
