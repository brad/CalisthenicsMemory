package io.github.gonbei774.calisthenicsmemory.data

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Serializable
@Entity(tableName = "interval_programs")
data class IntervalProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val roundRestSeconds: Int
)
