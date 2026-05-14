package com.bchat.app.services

import android.util.Log
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.messagepack.MessagePackHubProtocol
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus {
    Disconnected, Connecting, Connected, Reconnecting
}

class ChatMessageResult {
    var messageId: String = ""
    var timestamp: String = ""
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
            return
        }

        _connectionStatus.value = ConnectionStatus.Connecting

        val builder = HubConnectionBuilder()
            .withUrl(url)
            .withHubProtocol(MessagePackHubProtocol()) // Use MessagePack
            .withAutomaticReconnect()
            
        if (!token.isNullOrBlank()) {
            builder.withAccessTokenProvider(Single.just(token))
        }

        hubConnection = builder.build()

        hubConnection?.on("ReceiveMessage", { messageId: String, user: String, message: String, timestampStr: String, messageType: String, otherPersonId: String ->
            val timestamp = System.currentTimeMillis()
            onMessageReceived?.invoke(messageId, user, message, timestamp, messageType, otherPersonId)
        }, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)

        hubConnection?.on("UserTyping", { userId: String, isTyping: Boolean ->
            onUserTyping?.invoke(userId, isTyping)
        }, String::class.java, Boolean::class.java)

        hubConnection?.on("MessageRead", { messageId: String, readerId: String ->
            onMessageRead?.invoke(messageId, readerId)
        }, String::class.java, String::class.java)

        hubConnection?.on("MessageDeleted", { messageId: String ->
            onMessageDeleted?.invoke(messageId)
        }, String::class.java)

        hubConnection?.on("UserPresence", { userId: String, isOnline: Boolean, lastSeen: String ->
            onUserPresence?.invoke(userId, isOnline, lastSeen)
        }, String::class.java, Boolean::class.java, String::class.java)

        hubConnection?.onClosed { _connectionStatus.value = ConnectionStatus.Disconnected }
        hubConnection?.onReconnecting { _connectionStatus.value = ConnectionStatus.Reconnecting }
        hubConnection?.onReconnected { _connectionStatus.value = ConnectionStatus.Connected }

        connect()
    }

    private fun connect() {
        try {
            hubConnection?.start()?.blockingAwait()
            _connectionStatus.value = ConnectionStatus.Connected
        } catch (e: Exception) {
            Log.e("ChatService", "Error starting connection", e)
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    fun sendMessage(receiverId: String, message: String, messageType: String, onResult: (String, Long) -> Unit, onError: () -> Unit) {
        invokeHub("SendMessage", receiverId, message, messageType, onResult, onError)
    }

    fun sendTypingIndicator(receiverId: String, isTyping: Boolean) {
        hubConnection?.send("SendTypingIndicator", receiverId, isTyping)
    }

    fun markAsRead(messageId: String, senderId: String) {
        hubConnection?.send("MarkAsRead", messageId, senderId)
    }

    fun deleteMessage(messageId: String, receiverId: String) {
        hubConnection?.send("DeleteMessage", messageId, receiverId)
    }

    private fun invokeHub(method: String, vararg args: Any, onResult: (String, Long) -> Unit, onError: () -> Unit) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.invoke(ChatMessageResult::class.java, method, *args)
                ?.subscribe({ result ->
                    onResult(result.messageId, System.currentTimeMillis())
                }, { error ->
                    Log.e("ChatService", "Error invoking $method", error)
                    onError()
                })
        } else {
            onError()
        }
    }

    fun stopConnection() {
        hubConnection?.stop()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
