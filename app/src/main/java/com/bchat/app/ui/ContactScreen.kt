package com.bchat.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bchat.app.network.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(viewModel: ChatViewModel, onContactClick: (User) -> Unit) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.fetchUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contacts") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(users) { user ->
                ListItem(
                    headlineContent = { Text(user.userName) },
                    supportingContent = { Text(user.email) },
                    leadingContent = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onContactClick(user) }
                )
                HorizontalDivider()
            }
            
            if (users.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No other users found.")
                    }
                }
            }
        }
    }
}
