package com.bchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bchat.app.data.AuthRepository
import com.bchat.app.network.AuthRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(authRepository: AuthRepository, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 40.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BChat",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6366F1)
                )
                Text(
                    text = if (isRegistering) "Create your account" else "Sign in to your account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                errorMessage?.let {
                    Text(
                        text = it, 
                        color = MaterialTheme.colorScheme.error, 
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val request = AuthRequest(email, password)
                                val response = if (isRegistering) {
                                    authRepository.register(request)
                                } else {
                                    authRepository.login(request)
                                }

                                if (response.isSuccessful) {
                                    if (isRegistering) {
                                        isRegistering = false
                                        errorMessage = "Success! Now please sign in."
                                    } else {
                                        onLoginSuccess()
                                    }
                                } else {
                                    errorMessage = "Error: Invalid credentials"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Connection failed"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    enabled = !isLoading && email.isNotBlank() && password.length >= 6
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isRegistering) "Get Started" else "Sign In", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = { isRegistering = !isRegistering },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = if (isRegistering) "Already have an account? Sign In" else "New to BChat? Register",
                        color = Color(0xFFA855F7),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
