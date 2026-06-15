package io.github.gonbei774.calisthenicsmemory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "training_records",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class TrainingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseId: Long,
    val valueRight: Int,
    val valueLeft: Int? = null,
    val setNumber: Int,
    val date: String,
    val time: String,
    val comment: String = "",
    val distanceCm: Int? = null,
    val weightG: Int? = null,
    val assistanceG: Int? = null,
    val rpe: Int? = null // Rate of Perceived Exertion (1-10) for AI analysis
)
