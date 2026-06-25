package io.github.gonbei774.calisthenicsmemory

import io.github.gonbei774.calisthenicsmemory.service.GeminiModel
import io.github.gonbei774.calisthenicsmemory.service.GeminiModelList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelListingTest {

    @Test
    fun testGeminiModelListDeserialization() {
        val jsonString = """
            {
              "models": [
                {
                  "name": "models/gemini-1.5-flash",
                  "version": "001",
                  "displayName": "Gemini 1.5 Flash",
                  "description": "Fast and versatile",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-1.5-pro",
                  "version": "001",
                  "displayName": "Gemini 1.5 Pro",
                  "description": "Most capable",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/text-embedding-004",
                  "version": "004",
                  "displayName": "Embedding 004",
                  "description": "Embedding model",
                  "supportedGenerationMethods": ["embedContent"]
                }
              ]
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val modelList = json.decodeFromString<GeminiModelList>(jsonString)

        assertEquals(3, modelList.models.size)

        val flash = modelList.models.find { it.name == "models/gemini-1.5-flash" }
        assertTrue(flash != null)
        assertTrue(flash?.supportedGenerationMethods?.contains("generateContent") == true)

        val filtered = modelList.models
            .filter { it.supportedGenerationMethods.contains("generateContent") }
            .map { it.name.removePrefix("models/") }

        assertEquals(2, filtered.size)
        assertTrue(filtered.contains("gemini-1.5-flash"))
        assertTrue(filtered.contains("gemini-1.5-pro"))
        assertTrue(!filtered.contains("text-embedding-004"))
    }
}
