package io.github.gonbei774.calisthenicsmemory.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: AiThread): Long

    @Update
    suspend fun updateThread(thread: AiThread)

    @Delete
    suspend fun deleteThread(thread: AiThread)

    @Query("SELECT * FROM ai_threads ORDER BY createdAt DESC")
    fun getAllThreads(): Flow<List<AiThread>>

    @Query("SELECT * FROM ai_threads WHERE id = :threadId")
    suspend fun getThreadById(threadId: Long): AiThread?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessage): Long

    @Query("SELECT * FROM ai_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun getMessagesForThread(threadId: Long): Flow<List<AiMessage>>

    @Query("SELECT * FROM ai_messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getMessagesForThreadSync(threadId: Long): List<AiMessage>

    @Query("DELETE FROM ai_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Long)
}
