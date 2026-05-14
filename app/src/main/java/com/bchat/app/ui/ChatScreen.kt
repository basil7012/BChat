package com.bchat.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bchat.app.data.MessageEntity
import com.bchat.app.services.ConnectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val contact by viewModel.selectedContact.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val isContactTyping by viewModel.isContactTyping.collectAsStateWithLifecycle()
    val isContactOnline by viewModel.isContactOnline.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                val file = uriToFile(context, it)
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                viewModel.sendImage(body)
            }
        }
    )

    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            viewModel.sendTypingIndicator(true)
            delay(3000)
            viewModel.sendTypingIndicator(false)
        } else {
            viewModel.sendTypingIndicator(false)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                contact?.userName?.take(1)?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(contact?.userName ?: "Chat", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val presenceText = if (isContactOnline) "Online" else "Offline"
                                val presenceColor = if (isContactOnline) Color(0xFF4CAF50) else Color.Gray
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(presenceColor))
                                Spacer(Modifier.width(4.dp))
                                Text(text = presenceText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB))
                )
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == currentUserId
                        MessageBubble(msg, isMe) {
                            if (isMe && msg.messageId != null) messageToDelete = msg
                        }
                    }
                }

                // Bottom bar area
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        AnimatedVisibility(visible = isContactTyping) {
                            Text(
                                text = "${contact?.userName} is typing...",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isUploading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = connectionStatus == ConnectionStatus.Connected && !isUploading
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Gallery", tint = MaterialTheme.colorScheme.primary)
                            }
                            
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...", fontSize = 14.sp) },
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                enabled = connectionStatus == ConnectionStatus.Connected
                            )
                            
                            FloatingActionButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                containerColor = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message?") },
            text = { Text("Do you want to delete this message for everyone?") },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete?.messageId?.let { viewModel.deleteMessage(it) }
                    messageToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(msg: MessageEntity, isMe: Boolean, onLongClick: () -> Unit) {
    val bubbleShape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primary else Color.White,
            shape = bubbleShape,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { },
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.messageType == "image") {
                    AsyncImage(
                        model = msg.content,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMe) Color.White else Color.Black,
                        fontSize = 15.sp
                    )
                }
                
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (msg.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (msg.isRead) Color(0xFF81D4FA) else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}

private fun uriToFile(context: android.content.Context, uri: Uri): File {
    val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    return file
}
