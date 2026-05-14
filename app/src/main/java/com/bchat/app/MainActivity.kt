package com.bchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bchat.app.data.AppDatabase
import com.bchat.app.data.AuthRepository
import com.bchat.app.network.ApiService
import com.bchat.app.repository.ChatRepository
import com.bchat.app.services.ChatService
import com.bchat.app.ui.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

enum class Screen {
    Login, Contacts, Chat
}

class MainActivity : ComponentActivity() {

    private val apiService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        
        Retrofit.Builder()
            .baseUrl("http://bchat.runasp.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }

    private val authRepository by lazy { AuthRepository(this, apiService) }
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val chatService by lazy { ChatService() }
    private val repository by lazy { ChatRepository(database.messageDao(), chatService, authRepository, lifecycleScope) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val token by authRepository.token.collectAsState(initial = null)
                    val email by authRepository.email.collectAsState(initial = null)
                    val userId by authRepository.userId.collectAsState(initial = null)
                    
                    var currentScreen by remember { mutableStateOf(Screen.Login) }

                    LaunchedEffect(token, email, userId) {
                        if (!token.isNullOrBlank() && !email.isNullOrBlank() && !userId.isNullOrBlank()) {
                            viewModel.setCurrentUserEmail(email!!)
                            viewModel.setCurrentUserId(userId!!)
                            viewModel.startConnection("http://bchat.runasp.net/chathub")
                            if (currentScreen == Screen.Login) {
                                currentScreen = Screen.Contacts
                            }
                        } else {
                            currentScreen = Screen.Login
                        }
                    }

                    when (currentScreen) {
                        Screen.Login -> {
                            LoginScreen(authRepository = authRepository, onLoginSuccess = {
                                // Handled by LaunchedEffect
                            })
                        }
                        Screen.Contacts -> {
                            ContactScreen(
                                viewModel = viewModel, 
                                onContactClick = { contact ->
                                    viewModel.selectContact(contact)
                                    currentScreen = Screen.Chat
                                },
                                onLogout = {
                                    lifecycleScope.launch {
                                        authRepository.clearAuthData()
                                        currentScreen = Screen.Login
                                    }
                                }
                            )
                        }
                        Screen.Chat -> {
                            ChatScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.Contacts }
                            )
                        }
                    }
                }
            }
        }
    }
}
