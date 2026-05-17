package com.bchat.app.services

import android.util.Log
import com.microsoft.signalr.*
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

        // Use standard JSON protocol — MonsterASP.NET backend has not been redeployed
        // with AddMessagePackProtocol() yet. JSON is the universal fallback that always works.
        val builder = HubConnectionBuilder.create(url)
            // Force Long Polling transport — MonsterASP.NET shared IIS hosting
            // blocks WebSocket upgrades. Long Polling works on any HTTP host.
            .withTransport(HttpTransportType.LONG_POLLING)

        if (!token.isNullOrBlank()) {
            Log.d("ChatService", "Attaching JWT to SignalR handshake")
            builder.withAccessTokenProvider(Single.just(token))
            builder.withHeader("Authorization", "Bearer $token")
        }

        hubConnection = builder.build()

        // Keep-alive tuned for Long Polling over shared hosting latency
        hubConnection?.setKeepAliveInterval(15000L)  // 15 seconds (relaxed for LP)
        hubConnection?.setServerTimeout(30000L)       // 30 seconds

        hubConnection?.on("ReceiveMessage", Action6 { messageId: String, user: String, message: String, timestamp: Long, messageType: String, otherPersonId: String ->
            Log.d("ChatService", "✅ Message received from $user (id=$messageId)")
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

        hubConnection?.onClosed { error ->
            Log.w("ChatService", "Connection closed: ${error?.message}")
            _connectionStatus.value = ConnectionStatus.Disconnected
        }

        connect()
    }

    private fun connect() {
        try {
            hubConnection?.start()?.blockingAwait()
            Log.d("ChatService", "✅ SignalR connected successfully (Long Polling / JSON)")
            _connectionStatus.value = ConnectionStatus.Connected
        } catch (e: Exception) {
            Log.e("ChatService", "❌ SignalR connection failed: ${e.message}", e)
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
            Log.w("ChatService", "Cannot send typing indicator: SignalR not connected")
        }
    }

    fun markAsRead(messageId: String, senderId: String) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.send("MarkAsRead", messageId, senderId)
        } else {
            Log.w("ChatService", "Cannot mark as read: SignalR not connected")
        }
    }

    fun deleteMessage(messageId: String, receiverId: String) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.send("DeleteMessage", messageId, receiverId)
        } else {
            Log.w("ChatService", "Cannot delete message: SignalR not connected")
        }
    }

    private fun invokeHub(method: String, args: Array<Any>, onResult: (String, Long) -> Unit, onError: () -> Unit) {
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            hubConnection?.invoke(ChatMessageResult::class.java, method, *args)
                ?.subscribe({ result ->
                    Log.d("ChatService", "✅ $method success: messageId=${result.messageId}")
                    onResult(result.messageId, result.timestamp)
                }, { error ->
                    Log.e("ChatService", "❌ Error invoking $method: ${error.message}", error)
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
