package com.bchat.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bchat.app.network.ApiService
import com.bchat.app.network.AuthRequest
import com.bchat.app.network.AuthResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import retrofit2.Response

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class AuthRepository(private val context: Context, private val apiService: ApiService) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val EMAIL_KEY = stringPreferencesKey("user_email")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val email: Flow<String?> = context.dataStore.data.map { it[EMAIL_KEY] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }

    suspend fun register(request: AuthRequest): Response<Unit> = apiService.register(request)

    suspend fun login(request: AuthRequest): Response<AuthResponse> {
        val response = apiService.login(request)
        if (response.isSuccessful) {
            response.body()?.let { body ->
                // If the server didn't send the userId in the body, 
                // we extract it from the JWT token claims.
                val userId = body.userId ?: extractUserIdFromToken(body.token) ?: ""
                saveAuthData(body.token, body.email, userId)
            }
        }
        return response
    }

    private fun extractUserIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT))
            val json = org.json.JSONObject(payload)
            // SignalR uses the NameIdentifier claim, which is "nameid" or "sub" in JWT
            json.optString("nameid") ?: json.optString("sub")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUsers() = apiService.getUsers()

    suspend fun uploadImage(file: MultipartBody.Part): Response<com.bchat.app.network.UploadResponse> = 
        apiService.uploadImage(file)

    private suspend fun saveAuthData(token: String, email: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[EMAIL_KEY] = email
            prefs[USER_ID_KEY] = userId
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { it.clear() }
    }
}
