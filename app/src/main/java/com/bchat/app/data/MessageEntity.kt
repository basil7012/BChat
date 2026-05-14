package com.bchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageId: String?, // ID from the server
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val deliveryStatus: String, // "Pending", "Sent", "Delivered"
    val messageType: String = "text", // "text", "image"
    val receiverId: String? = null, // The person I am talking to
    val isRead: Boolean = false,
    val isDeleted: Boolean = false
)
