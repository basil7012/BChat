package com.bchat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bchat.app.data.MessageEntity
import com.bchat.app.network.User
import com.bchat.app.repository.ChatRepository
import com.bchat.app.services.ConnectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _selectedContact = MutableStateFlow<User?>(null)
    val selectedContact = _selectedContact.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _currentUserEmail = MutableStateFlow("")
    val currentUserEmail = _currentUserEmail.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _selectedContact
        .filterNotNull()
        .flatMapLatest { contact ->
            repository.getMessagesForConversation(_currentUserEmail.value, contact.id)
        }
        .onEach { msgs ->
            // Mark unread messages as read when they appear in the UI
            msgs.filter { !it.isRead && it.sender != _currentUserEmail.value }.forEach {
                it.messageId?.let { mid -> markAsRead(mid, it.sender) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
        .stateIn(viewModelScope, SharingStarted.Lazily, ConnectionStatus.Disconnected)

    val isContactTyping: StateFlow<Boolean> = combine(repository.isTyping, _selectedContact) { typingMap, contact ->
        contact?.let { typingMap[it.id] ?: false } ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isContactOnline: StateFlow<Boolean> = combine(repository.userPresence, _selectedContact) { presenceMap, contact ->
        contact?.let { presenceMap[it.id] ?: false } ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    fun setCurrentUserEmail(email: String) { _currentUserEmail.value = email }

    fun selectContact(contact: User) { _selectedContact.value = contact }

    fun fetchUsers() {
        viewModelScope.launch {
            try {
                val response = repository.getUsers()
                if (response.isSuccessful) {
                    _users.value = response.body()?.filter { it.email != _currentUserEmail.value } ?: emptyList()
                }
            } catch (e: Exception) {}
        }
    }

    fun startConnection(url: String) { repository.startConnection(url) }

    fun sendMessage(content: String) {
        val contact = _selectedContact.value ?: return
        if (content.isNotBlank()) {
            repository.sendMessage(contact.id, content, "text")
        }
    }

    fun sendTypingIndicator(isTyping: Boolean) {
        val contact = _selectedContact.value ?: return
        repository.sendTypingIndicator(contact.id, isTyping)
    }

    fun markAsRead(messageId: String, senderName: String) {
        // Need sender ID to notify them. For now, assume senderName is used as ID or find it.
        // In a real app, messages would have senderId.
        // For simplicity, we'll just pass the name if that's what the hub expects or the actual ID.
        // Let's find the user by name if possible, or just pass the ID if we had it.
        val contact = _selectedContact.value ?: return
        repository.markAsRead(messageId, contact.id)
    }

    fun deleteMessage(messageId: String) {
        val contact = _selectedContact.value ?: return
        repository.deleteMessage(messageId, contact.id)
    }

    fun sendImage(file: MultipartBody.Part) {
        val contact = _selectedContact.value ?: return
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val response = repository.uploadImage(file)
                if (response.isSuccessful) {
                    response.body()?.url?.let { imageUrl ->
                        repository.sendMessage(contact.id, imageUrl, "image")
                    }
                }
            } catch (e: Exception) {} finally { _isUploading.value = false }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopConnection()
    }
}

class ChatViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
