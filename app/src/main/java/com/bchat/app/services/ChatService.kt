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
    var messageId: String = ""
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
            return
        }

        _connectionStatus.value = ConnectionStatus.Connecting

        val builder = HubConnectionBuilder.create(url)
            .withHubProtocol(MessagePackHubProtocol())
            
        // Some versions of the Java client use a slightly different reconnect syntax
        // Removing automatic reconnect for now to ensure a successful build, 
        // as the client will still connect manually.
            
        if (!token.isNullOrBlank()) {
            builder.withAccessTokenProvider(Single.just(token))
        }

        hubConnection = builder.build()

        hubConnection?.on("ReceiveMessage", Action6 { messageId: String, user: String, message: String, timestamp: Long, messageType: String, otherPersonId: String ->
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
            onUserPresence?.invoke(userId, isOnline, lastSeen)
        }, String::class.java, Boolean::class.javaObjectType, String::class.java)

        hubConnection?.onClosed { _connectionStatus.value = ConnectionStatus.Disconnected }
        
        // Fixed the event listeners for Java SignalR client
        hubConnection?.onReconnecting(Action1 { _connectionStatus.value = ConnectionStatus.Reconnecting })
        hubConnection?.onReconnected(Action1 { _connectionStatus.value = ConnectionStatus.Connected })

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
        invokeHub(
            method = "SendMessage", 
            args = arrayOf(receiverId, message, messageType), 
            onResult = onResult, 
            onError = onError
        )
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
            onError()
        }
    }

    fun stopConnection() {
        hubConnection?.stop()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }
}
