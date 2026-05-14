package com.bchat.app.repository

import com.bchat.app.data.AuthRepository
import com.bchat.app.data.MessageDao
import com.bchat.app.data.MessageEntity
import com.bchat.app.security.EncryptionService
import com.bchat.app.services.ChatService
import com.bchat.app.services.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ChatRepository(
    private val messageDao: MessageDao,
    private val chatService: ChatService,
    private val authRepository: AuthRepository,
    private val coroutineScope: CoroutineScope
) {
    val messages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val connectionStatus: Flow<ConnectionStatus> = chatService.connectionStatus

    private val _isTyping = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isTyping: StateFlow<Map<String, Boolean>> = _isTyping.asStateFlow()

    private val _userPresence = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userPresence: StateFlow<Map<String, Boolean>> = _userPresence.asStateFlow()

    private var _currentUserId: String = ""

    init {
        chatService.onMessageReceived = { messageId, user, encryptedMessage, timestamp, messageType, otherPersonId ->
            coroutineScope.launch {
                val decryptedContent = if (messageType == "text") EncryptionService.decrypt(encryptedMessage) else encryptedMessage
                val existingMessage = messageDao.getMessageByServerId(messageId)
                if (existingMessage == null) {
                    val entity = MessageEntity(
                        messageId = messageId,
                        senderId = otherPersonId, // In an incoming message, otherPersonId is the sender GUID
                        content = decryptedContent,
                        timestamp = timestamp,
                        deliveryStatus = "Delivered",
                        messageType = messageType,
                        receiverId = _currentUserId // This should be my GUID
                    )
                    messageDao.insert(entity)
                } else {
                    messageDao.updateDeliveryStatus(existingMessage.id, messageId, timestamp, "Delivered")
                }
            }
        }

        chatService.onUserTyping = { userId, typing ->
            _isTyping.value = _isTyping.value + (userId to typing)
        }

        chatService.onMessageRead = { messageId, readerId ->
            coroutineScope.launch {
                messageDao.markAsRead(messageId)
            }
        }

        chatService.onMessageDeleted = { messageId ->
            coroutineScope.launch {
                messageDao.deleteByServerId(messageId)
            }
        }

        chatService.onUserPresence = { userId, online, lastSeen ->
            _userPresence.value = _userPresence.value + (userId to online)
        }
    }

    fun startConnection(url: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val token = authRepository.token.firstOrNull()
            _currentUserId = authRepository.userId.firstOrNull() ?: ""
            chatService.startConnection(url, token)
        }
    }

    fun getMessagesForConversation(currentUser: String, contactId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByConversation(currentUser, contactId)
    }

    fun sendMessage(receiverId: String, content: String, messageType: String = "text") {
        coroutineScope.launch {
            val userId = authRepository.userId.firstOrNull() ?: ""
            val encryptedContent = if (messageType == "text") EncryptionService.encrypt(content) else content
            
            val entity = MessageEntity(
                messageId = null,
                senderId = userId,
                content = content,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = "Pending",
                messageType = messageType,
                receiverId = receiverId
            )
            val localId = messageDao.insert(entity).toInt()

            chatService.sendMessage(
                receiverId = receiverId,
                message = encryptedContent,
                messageType = messageType,
                onResult = { messageId, serverTimestamp ->
                    coroutineScope.launch {
                        messageDao.updateDeliveryStatus(localId, messageId, serverTimestamp, "Sent")
                    }
                },
                onError = { /* Handle error */ }
            )
        }
    }

    fun sendTypingIndicator(receiverId: String, isTyping: Boolean) {
        chatService.sendTypingIndicator(receiverId, isTyping)
    }

    fun markAsRead(messageId: String, senderId: String) {
        chatService.markAsRead(messageId, senderId)
    }

    fun deleteMessage(messageId: String, receiverId: String) {
        chatService.deleteMessage(messageId, receiverId)
    }

    suspend fun uploadImage(file: MultipartBody.Part) = authRepository.uploadImage(file)
    suspend fun getUsers() = authRepository.getUsers()
    fun stopConnection() = chatService.stopConnection()
}
