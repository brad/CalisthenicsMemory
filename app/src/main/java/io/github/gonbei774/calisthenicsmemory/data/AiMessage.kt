package io.github.gonbei774.calisthenicsmemory.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiThread::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId")]
)
data class AiMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val backupDataJson: String? = null
)
