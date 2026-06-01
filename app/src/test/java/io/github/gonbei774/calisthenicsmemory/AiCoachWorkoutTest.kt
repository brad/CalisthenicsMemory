package io.github.gonbei774.calisthenicsmemory

import io.github.gonbei774.calisthenicsmemory.viewmodel.CommunityShareContent
import io.github.gonbei774.calisthenicsmemory.viewmodel.CommunityShareData
import io.github.gonbei774.calisthenicsmemory.viewmodel.ShareExercise
import io.github.gonbei774.calisthenicsmemory.viewmodel.ShareProgram
import io.github.gonbei774.calisthenicsmemory.viewmodel.ShareProgramExercise
import io.github.gonbei774.calisthenicsmemory.viewmodel.extractWorkoutJson
import io.github.gonbei774.calisthenicsmemory.viewmodel.extractMemoryUpdate
import io.github.gonbei774.calisthenicsmemory.viewmodel.validateCommunityShareContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachWorkoutTest {

    @Test
    fun `extractWorkoutJson extracts pretty printed JSON`() {
        val aiResponse = """
            Sure! Here is a workout for you:
            {
              "formatVersion": 1,
              "exportType": "share",
              "exportDate": "2024-01-01T00:00:00",
              "exportId": "ai_suggestion",
              "appVersion": "1.0.0",
              "data": {
                "groups": [],
                "exercises": [],
                "programs": []
              }
            }
            I hope you enjoy it!
        """.trimIndent()

        val extracted = extractWorkoutJson(aiResponse)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("\"formatVersion\": 1"))
        assertTrue(extracted.endsWith("}"))
    }

    @Test
    fun `extractWorkoutJson extracts minified JSON`() {
        val aiResponse = "Try this: {\"formatVersion\": 1,\"exportType\":\"share\",\"data\":{\"programs\":[]}} It is good."
        val extracted = extractWorkoutJson(aiResponse)
        assertNotNull(extracted)
        assertEquals("{\"formatVersion\": 1,\"exportType\":\"share\",\"data\":{\"programs\":[]}}", extracted)
    }

    @Test
    fun `extractWorkoutJson returns null if no valid start found`() {
        val aiResponse = "I don't have a workout for you yet."
        val extracted = extractWorkoutJson(aiResponse)
        assertNull(extracted)
    }

    @Test
    fun `validateCommunityShareContent detects invalid exportType`() {
        val data = CommunityShareData(
            formatVersion = 1,
            exportType = "backup",
            exportDate = "",
            exportId = "",
            appVersion = "",
            data = CommunityShareContent()
        )
        val errors = validateCommunityShareContent(data)
        assertTrue(errors.any { it.contains("Invalid exportType") })
    }

    @Test
    fun `validateCommunityShareContent detects unknown exercise in program`() {
        val data = CommunityShareData(
            formatVersion = 1,
            exportType = "share",
            exportDate = "",
            exportId = "",
            appVersion = "",
            data = CommunityShareContent(
                exercises = listOf(ShareExercise("Push-up", "Dynamic")),
                programs = listOf(
                    ShareProgram("Test", exercises = listOf(
                        ShareProgramExercise("Pull-up", "Dynamic", 0)
                    ))
                )
            )
        )
        val errors = validateCommunityShareContent(data)
        assertTrue(errors.any { it.contains("references unknown exercise 'Pull-up'") })
    }

    @Test
    fun `validateCommunityShareContent accepts valid data`() {
        val data = CommunityShareData(
            formatVersion = 1,
            exportType = "share",
            exportDate = "",
            exportId = "",
            appVersion = "",
            data = CommunityShareContent(
                exercises = listOf(ShareExercise("Push-up", "Dynamic")),
                programs = listOf(
                    ShareProgram("Test", exercises = listOf(
                        ShareProgramExercise("Push-up", "Dynamic", 0)
                    ))
                )
            )
        )
        val errors = validateCommunityShareContent(data)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `extractMemoryUpdate extracts memory update JSON`() {
        val aiResponse = """
            I've updated my memory about you.
            {"type": "memory_update", "newMemory": "User has a shoulder injury."}
            I will keep that in mind.
        """.trimIndent()
        val extracted = extractMemoryUpdate(aiResponse)
        assertEquals("{\"type\": \"memory_update\", \"newMemory\": \"User has a shoulder injury.\"}", extracted)
    }

    @Test
    fun `extractMemoryUpdate extracts minified memory update JSON`() {
        val aiResponse = "Update: {\"type\":\"memory_update\",\"newMemory\":\"Goals: 10 pullups\"} Done."
        val extracted = extractMemoryUpdate(aiResponse)
        assertEquals("{\"type\":\"memory_update\",\"newMemory\":\"Goals: 10 pullups\"}", extracted)
    }

    @Test
    fun `extractMemoryUpdate extracts from markdown code block`() {
        val aiResponse = """
            I've updated my memory.
            ```json
            {
              "type": "memory_update",
              "newMemory": "User prefers high volume."
            }
            ```
        """.trimIndent()
        val extracted = extractMemoryUpdate(aiResponse)
        assertNotNull("Should extract JSON even if inside markdown block", extracted)
        assertTrue(extracted!!.contains("User prefers high volume."))
    }

    @Test
    fun `extractMemoryUpdate handles extra whitespace`() {
        val aiResponse = "Update: { \"type\" : \"memory_update\" , \"newMemory\" : \"test\" }"
        val extracted = extractMemoryUpdate(aiResponse)
        assertNotNull("Should handle extra whitespace", extracted)
    }

    @Test
    fun `extractWorkoutJson extracts from markdown code block`() {
        val aiResponse = """
            Here's your workout:
            ```json
            {
              "formatVersion": 1,
              "exportType": "share",
              "data": { "programs": [] }
            }
            ```
        """.trimIndent()
        val extracted = extractWorkoutJson(aiResponse)
        assertNotNull("Should extract JSON even if inside markdown block", extracted)
        assertTrue(extracted!!.contains("\"formatVersion\": 1"))
    }
}
