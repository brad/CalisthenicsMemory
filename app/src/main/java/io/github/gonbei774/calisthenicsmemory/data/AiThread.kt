package io.github.gonbei774.calisthenicsmemory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_threads")
data class AiThread(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
