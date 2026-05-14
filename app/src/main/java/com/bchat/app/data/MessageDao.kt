package com.bchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (senderId = :currentUserId AND receiverId = :contactId) OR (senderId = :contactId AND receiverId = :currentUserId) ORDER BY timestamp ASC")
    fun getMessagesByConversation(currentUserId: String, contactId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET deliveryStatus = :status, messageId = :messageId, timestamp = :serverTimestamp WHERE id = :localId")
    suspend fun updateDeliveryStatus(localId: Int, messageId: String, serverTimestamp: Long, status: String)
    
    @Query("UPDATE messages SET isRead = 1 WHERE messageId = :messageId")
    suspend fun markAsRead(messageId: String)

    @Query("UPDATE messages SET isDeleted = 1, content = 'This message was deleted' WHERE messageId = :messageId")
    suspend fun deleteByServerId(messageId: String)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun hardDeleteByServerId(messageId: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageByServerId(messageId: String): MessageEntity?
}
