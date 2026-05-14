package com.bchat.app.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

import com.google.gson.annotations.SerializedName

data class AuthRequest(val email: String, val password: String)
data class AuthResponse(
    @SerializedName("token") val token: String,
    @SerializedName("email") val email: String,
    @SerializedName("userId") val userId: String
)
data class User(val id: String, val email: String, val userName: String)
data class UploadResponse(val url: String)

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<Unit>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("api/auth/users")
    suspend fun getUsers(): Response<List<User>>

    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<UploadResponse>
}
