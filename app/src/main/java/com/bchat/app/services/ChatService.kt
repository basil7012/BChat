package com.bchat.app.services

import android.util.Log
import com.microsoft.signalr.*
import com.microsoft.signalr.messagepack.MessagePackHubProtocol
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus {
    Disconnected, Connecting, Connected, Reconnecting
}

class ChatMessageResult {
    @com.google.gson.annotations.SerializedName("messageId", alternate = ["MessageId"])
    var messageId: String = ""
    
    @com.google.gson.annotations.SerializedName("timestamp", alternate = ["Timestamp"])
    var timestamp: Long = 0
}

class ChatService {
    private var hubConnection: HubConnection? = null
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    var onMessageReceived: ((String, String, String, Long, String, String) -> Unit)? = null
    var onUserTyping: ((String, Boolean) -> Unit)? = null
    var onMessageRead: ((String, String) -> Unit)? = null
    var onMessageDeleted: ((String) -> Unit)? = null
    var onUserPresence: ((String, Boolean, String) -> Unit)? = null

    fun startConnection(url: String, token: String?) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            Log.d("ChatService", "Already connected")
            return
        }

        Log.d("ChatService", "Starting connection to $url")
        _connectionStatus.value = ConnectionStatus.Connecting

        // Using high-efficiency binary MessagePack protocol for sub-second serialization
        val builder = HubConnectionBuilder.create(url)
            .withHubProtocol(MessagePackHubProtocol())
            
        if (!token.isNullOrBlank()) {
            Log.d("ChatService", "Using token for authentication")
            builder.withAccessTokenProvider(Single.just(token))
        }

        hubConnection = builder.build()

        // Configure aggressive maintainers to prevent packet queue buffering on shared networks
        hubConnection?.setKeepAliveInterval(5000L) // 5 seconds
        hubConnection?.setServerTimeout(10000L)    // 10 seconds

        hubConnection?.on("ReceiveMessage", Action6 { messageId: String, user: String, message: String, timestamp: Long, messageType: String, otherPersonId: String ->
            Log.d("ChatService", "Message received from $user")
            onMessageReceived?.invoke(messageId, user, message, timestamp, messageType, otherPersonId)
        }, String::class.java, String::class.java, String::class.java, Long::class.javaObjectType, String::class.java, String::class.java)

        hubConnection?.on("UserTyping", Action2 { userId: String, isTyping: Boolean ->
            onUserTyping?.invoke(userId, isTyping)
        }, String::class.java, Boolean::class.javaObjectType)

        hubConnection?.on("MessageRead", Action2 { messageId: String, readerId: String ->
            onMessageRead?.invoke(messageId, readerId)
        }, String::class.java, String::class.java)

        hubConnection?.on("MessageDeleted", Action1 { messageId: String ->
            onMessageDeleted?.invoke(messageId)
        }, String::class.java)

        hubConnection?.on("UserPresence", Action3 { userId: String, isOnline: Boolean, lastSeen: String ->
            Log.d("ChatService", "User Presence: $userId, online: $isOnline")
            onUserPresence?.invoke(userId, isOnline, lastSeen)
        }, String::class.java, Boolean::class.javaObjectType, String::class.java)

        hubConnection?.onClosed { 
            Log.d("ChatService", "Connection closed")
            _connectionStatus.value = ConnectionStatus.Disconnected 
        }

        connect()
    }

    private fun connect() {
        try {
            hubConnection?.start()?.blockingAwait()
            Log.d("ChatService", "Connection established successfully")
            
            // If start() completes without exception, the configured MessagePack protocol negotiated successfully
            Log.d("ChatService", "SUCCESS: High-performance binary MessagePack Hub Protocol negotiated and active!")
            
            _connectionStatus.value = ConnectionStatus.Connected
        } catch (e: Exception) {
            Log.e("ChatService", "Error starting connection", e)
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    fun sendMessage(receiverId: String, message: String, messageType: String, onResult: (String, Long) -> Unit, onError: () -> Unit) {
        invokeHub("SendMessage", arrayOf(receiverId, message, messageType), onResult, onError)
    }

    fun sendTypingIndicator(receiverId: String, isTyping: Boolean) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.send("SendTypingIndicator", receiverId, isTyping)
        } else {
            Log.w("ChatService", "Cannot send typing indicator: SignalR connection is not active.")
        }
    }

    fun markAsRead(messageId: String, senderId: String) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.send("MarkAsRead", messageId, senderId)
        } else {
            Log.w("ChatService", "Cannot mark message as read: SignalR connection is not active.")
        }
    }

    fun deleteMessage(messageId: String, receiverId: String) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.send("DeleteMessage", messageId, receiverId)
        } else {
            Log.w("ChatService", "Cannot delete message: SignalR connection is not active.")
        }
    }

    private fun invokeHub(method: String, args: Array<Any>, onResult: (String, Long) -> Unit, onError: () -> Unit) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.invoke(ChatMessageResult::class.java, method, *args)
                ?.subscribe({ result ->
                    onResult(result.messageId, result.timestamp)
                }, { error ->
                    Log.e("ChatService", "Error invoking $method", error)
                    onError()
                })
        } else {
            Log.w("ChatService", "Cannot invoke $method: Not connected")
            onError()
        }
    }

    fun stopConnection() {
        hubConnection?.stop()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
