package com.bchat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class Screen {
    Login, Contacts, Chat
}

/**
 * Builds an OkHttpClient that trusts ALL certificates.
 * Required when the backend runs on plain HTTP or has no valid SSL certificate
 * (e.g., MonsterASP.NET shared hosting without HTTPS).
 *
 * NOTE: Do NOT use this in a Play Store production app that handles sensitive data
 * over HTTPS. Replace with proper certificate pinning once SSL is obtained.
 */
fun buildTrustAllOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }   // accept any hostname
        .build()
}

class MainActivity : ComponentActivity() {

    private val apiService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = buildTrustAllOkHttpClient()
            .newBuilder()
            .addInterceptor(logging)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
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
    private val repository by lazy { ChatRepository(database.messageDao(), chatService, authRepository, lifecycleScope, this) }

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

                    // ── Global notification banner state ───────────────────────────────
                    var bannerAlert by remember { mutableStateOf<IncomingMessageAlert?>(null) }
                    var bannerKey by remember { mutableStateOf(0) }   // increments to reset timer

                    // Collect incoming message alerts from any screen
                    LaunchedEffect(Unit) {
                        viewModel.lastReceivedMessage.collect { alert ->
                            // Only show banner when the user is NOT already on the Chat screen
                            if (currentScreen != Screen.Chat) {
                                bannerAlert = alert
                                bannerKey++          // reset the 4-second dismissal timer
                            }
                        }
                    }

                    // Auto-dismiss the banner after 4 seconds; bannerKey resets the timer
                    LaunchedEffect(bannerKey) {
                        if (bannerAlert != null) {
                            delay(4000)
                            bannerAlert = null
                        }
                    }

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

                    // ── Root layered Box: screens + floating banner overlay ────────────
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Primary navigation host
                        when (currentScreen) {
                            Screen.Login -> {
                                LoginScreen(authRepository = authRepository, onLoginSuccess = {
                                    currentScreen = Screen.Contacts
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

                        // ── Global in-app notification banner ─────────────────────────
                        AnimatedVisibility(
                            visible = bannerAlert != null,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = tween(350)
                            ) + fadeIn(animationSpec = tween(350)),
                            exit = slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 48.dp)
                        ) {
                            bannerAlert?.let { alert ->
                                Card(
                                    onClick = {
                                        bannerAlert = null
                                        currentScreen = Screen.Chat
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                                )
                                            )
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Icon bubble
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Message,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            // Text content
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = alert.senderName,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = alert.preview,
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "Tap to view",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
