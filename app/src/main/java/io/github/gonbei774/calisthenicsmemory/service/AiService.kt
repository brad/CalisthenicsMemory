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

    suspend fun generateResponse(
        prompt: String,
        contextData: String,
        history: List<io.github.gonbei774.calisthenicsmemory.data.AiMessage> = emptyList()
    ): String? {
        return withContext(Dispatchers.IO) {
            val model = getModel() ?: return@withContext "Please set your Gemini API Key in Settings first."

            val historyPrompt = if (history.isNotEmpty()) {
                "\nPrevious Conversation History:\n" + history.joinToString("\n") {
                    (if (it.isUser) "User: " else "Coach: ") + it.text
                } + "\n"
            } else ""

            val aiMemory = workoutPreferences.getAiMemory()
            val aiMemoryPrompt = if (aiMemory.isNotBlank()) {
                "\nCoach Memory (Your knowledge about the user):\n$aiMemory\n"
            } else ""

            val fullPrompt = """
                You are a professional calisthenics coach assistant for the "Calisthenics Memory" app.
                The app uses a specific JSON format for exercises, programs, and records.
                $aiMemoryPrompt
                Current Context (JSON):
                $contextData
                $historyPrompt
                User Request:
                $prompt

                Guidelines:
                1. Provide helpful, encouraging, and science-based calisthenics advice.
                2. Be aware of popular calisthenics programs like Convict Conditioning, Start Bodyweight, the Reddit Recommended Routine (RR), and concepts like Grease the Groove (GtG).
                3. If the user wants to log a workout in natural language, respond with a JSON block that matches the app's 'TrainingRecord' or 'BackupData' format, followed by a human-readable summary.
                4. If the user asks for a workout plan or if you suggest starting a workout, generate a JSON block that matches the 'CommunityShareData' format.
                Prefer suggesting exactly ONE program (either a new "ephemeral" one tailored to the request, or a relevant existing one from the context).
                This JSON MUST be a complete object including formatVersion (currently 1), exportType ("share"), and the 'data' field containing groups, exercises, and programs.
                Even if suggesting an existing program, you MUST include its full definition and all required exercises in the JSON.
                Ensure all exercises used in the program are also defined in the 'exercises' list of the JSON.
                Example structure:
                {
                  "formatVersion": 1,
                  "exportType": "share",
                  "exportDate": "2024-01-01T00:00:00",
                  "exportId": "ai_suggestion",
                  "appVersion": "1.0.0",
                  "data": {
                    "groups": [{"name": "Chest"}],
                    "exercises": [{"name": "Push-ups", "type": "Dynamic", "group": "Chest"}],
                    "programs": [{"name": "Morning Push", "exercises": [{"exerciseName": "Push-ups", "exerciseType": "Dynamic", "sortOrder": 1, "sets": 3, "targetValue": 10}]}]
                  }
                }
                5. Always prioritize safety and progressive overload.
                6. Keep responses concise and focused on calisthenics.
                7. If analyzing history, look for plateaus (3+ weeks without improvement) and suggest deloads or intensity adjustments.
                8. Refer to the previous conversation history if it's provided to maintain context.
                9. You can proactively update your 'Coach Memory' by including a JSON block: {"type": "memory_update", "newMemory": "updated memory here"}. Do this when you learn something new about the user (e.g., goals, injuries, equipment) that should be remembered for future sessions. The 'newMemory' should be a concise summary of EVERYTHING you know about the user, as it replaces the current memory. Memory updates are handled automatically by the app; do NOT tell the user to manually copy/paste or use this JSON to update their memory.
                10. If the user asks to modify, delete, or reorganize existing data (like removing an exercise, changing a program, or updating the Todo list), you MUST perform these changes on the provided "Current Context" JSON and return the ENTIRE updated context in a JSON block: {"type": "auto_update", "updatedContext": <Updated BackupData JSON>}.
                This allows you to "automatically" manage the users database. Only use this for destructive or structural changes that the user explicitly requested.
                The "updatedContext" MUST follow the "BackupData" format provided in the Current Context.
                Always follow the "auto_update" JSON with a brief human-readable confirmation of what you changed.            """.trimIndent()

            try {
                val response = model.generateContent(fullPrompt)
                response.text
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
