package io.github.gonbei774.calisthenicsmemory.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService(private val workoutPreferences: WorkoutPreferences) {

    private fun getModel(): GenerativeModel? {
        val apiKey = workoutPreferences.getGeminiApiKey()
        if (apiKey.isBlank()) return null
        val modelName = workoutPreferences.getGeminiModel()
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )
    }

    suspend fun generateResponse(prompt: String, contextData: String): String? {
        return withContext(Dispatchers.IO) {
            val model = getModel() ?: return@withContext "Please set your Gemini API Key in Settings first."

            val fullPrompt = """
                You are a professional calisthenics coach assistant for the "Calisthenics Memory" app.
                The app uses a specific JSON format for exercises, programs, and records.

                Current Context (JSON):
                $contextData

                User Request:
                $prompt

                Guidelines:
                1. Provide helpful, encouraging, and science-based calisthenics advice.
                2. Be aware of popular calisthenics programs like Convict Conditioning, Start Bodyweight, the Reddit Recommended Routine (RR), and concepts like Grease the Groove (GtG).
                3. If the user wants to log a workout in natural language, respond with a JSON block that matches the app's 'TrainingRecord' or 'BackupData' format, followed by a human-readable summary.
                4. If the user asks for a workout plan, generate a JSON block that matches the 'ShareProgram' format.
                5. Always prioritize safety and progressive overload.
                6. Keep responses concise and focused on calisthenics.
                7. If analyzing history, look for plateaus (3+ weeks without improvement) and suggest deloads or intensity adjustments.
            """.trimIndent()

            try {
                val response = model.generateContent(fullPrompt)
                response.text
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
