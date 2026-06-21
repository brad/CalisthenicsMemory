package io.github.gonbei774.calisthenicsmemory.service

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {
    val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            null
        }
    }

    fun isHealthConnectAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val permissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    fun getPermissions(): Set<String> {
        return setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }

    suspend fun writeWorkoutSession(
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        exerciseName: String,
        notes: String? = null
    ) {
        val client = healthConnectClient ?: return
        val record = ExerciseSessionRecord(
            startTime = startTime.toInstant(),
            startZoneOffset = startTime.offset,
            endTime = endTime.toInstant(),
            endZoneOffset = endTime.offset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = exerciseName,
            notes = notes,
            metadata = Metadata()
        )
        client.insertRecords(listOf(record))
    }
}
