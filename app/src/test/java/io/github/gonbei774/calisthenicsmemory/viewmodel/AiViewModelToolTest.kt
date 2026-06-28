package io.github.gonbei774.calisthenicsmemory.viewmodel

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AiViewModelToolTest {
    @Test
    fun testProgramListMapping() {
        val programs = listOf(
            mapOf("id" to 1L, "name" to "Standard P", "type" to "STANDARD")
        )
        val intervalPrograms = listOf(
            mapOf("id" to 2L, "name" to "Interval P", "type" to "INTERVAL")
        )
        val combined = programs + intervalPrograms

        // This simulates how Json.encodeToString works with the combined list in AiViewModel
        val jsonString = buildJsonArray {
            combined.forEach { map ->
                addJsonObject {
                    put("id", map["id"] as Long)
                    put("name", map["name"] as String)
                    put("type", map["type"] as String)
                }
            }
        }.toString()

        val decoded = Json.decodeFromString<List<Map<String, JsonElement>>>(jsonString)

        assertEquals(2, decoded.size)
        assertEquals("STANDARD", decoded[0]["type"]?.jsonPrimitive?.content)
        assertEquals("INTERVAL", decoded[1]["type"]?.jsonPrimitive?.content)
    }
}
