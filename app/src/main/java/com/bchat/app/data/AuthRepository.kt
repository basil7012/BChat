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
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val email: Flow<String?> = context.dataStore.data.map { it[EMAIL_KEY] }

    suspend fun register(request: AuthRequest): Response<Unit> = apiService.register(request)

    suspend fun login(request: AuthRequest): Response<AuthResponse> {
        val response = apiService.login(request)
        if (response.isSuccessful) {
            response.body()?.let {
                saveAuthData(it.token, it.email)
            }
        }
        return response
    }

    suspend fun getUsers() = apiService.getUsers()

    suspend fun uploadImage(file: MultipartBody.Part): Response<com.bchat.app.network.UploadResponse> = 
        apiService.uploadImage(file)

    private suspend fun saveAuthData(token: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[EMAIL_KEY] = email
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { it.clear() }
    }
}
