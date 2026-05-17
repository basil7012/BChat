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
import androidx.compose.ui.graphics.Brush
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
    val userPresence by viewModel.userPresence.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.fetchUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Messages", 
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color(0xFF1E293B),
                        fontSize = 24.sp
                    ) 
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color(0xFF1E293B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(users) { user ->
                    val isOnline = userPresence[user.id] ?: false
                    
                    ListItem(
                        headlineContent = { 
                            Text(
                                user.userName, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                user.email, 
                                color = Color(0xFF64748B), 
                                fontSize = 13.sp
                            ) 
                        },
                        leadingContent = {
                            Box(modifier = Modifier.size(52.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        user.userName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 20.sp
                                    )
                                }
                                if (isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { onContactClick(user) }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 76.dp),
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.3f)
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
