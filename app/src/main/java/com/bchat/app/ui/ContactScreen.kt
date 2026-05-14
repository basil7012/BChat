package com.bchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bchat.app.network.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(viewModel: ChatViewModel, onContactClick: (User) -> Unit, onLogout: () -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val isContactOnline by viewModel.isContactOnline.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.fetchUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyColumn {
                items(users) { user ->
                    ListItem(
                        headlineContent = { 
                            Text(user.userName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) 
                        },
                        supportingContent = { 
                            Text(user.email, color = Color.Gray, fontSize = 13.sp) 
                        },
                        leadingContent = {
                            Box(modifier = Modifier.size(50.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        user.userName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 20.sp
                                    )
                                }
                                // Small online indicator would go here if we had it for all users
                            }
                        },
                        modifier = Modifier
                            .clickable { onContactClick(user) }
                            .padding(vertical = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 72.dp),
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                }
                
                if (users.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Person, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.LightGray
                                )
                                Spacer(Modifier.height(16.dp))
                                Text("No other users found.", color = Color.Gray)
                                Text("Tell your friends to join BChat!", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
