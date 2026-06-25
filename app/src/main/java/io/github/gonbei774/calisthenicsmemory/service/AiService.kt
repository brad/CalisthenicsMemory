package io.github.gonbei774.calisthenicsmemory.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GeminiModelList(
    val models: List<GeminiModel>
)

@Serializable
data class GeminiModel(
    val name: String,
    val version: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String>
)

class AiService(private val workoutPreferences: WorkoutPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun getModel(tools: List<Tool>? = null): GenerativeModel? {
        val apiKey = workoutPreferences.getGeminiApiKey()
        if (apiKey.isBlank()) return null
        val modelName = workoutPreferences.getGeminiModel()
        return GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            tools = tools
        )
    }

    private val tools = listOf(
        Tool(
            functionDeclarations = listOf(
                defineFunction(
                    name = "add_exercise",
                    description = "Add a new exercise to the database.",
                    parameters = listOf(
                        Schema.str("name", "Name of the exercise"),
                        Schema.str("type", "Type of exercise ('Dynamic' or 'Isometric')"),
                        Schema.str("group", "Optional group name"),
                        Schema.int("targetSets", "Optional target sets"),
                        Schema.int("targetValue", "Optional target reps/seconds"),
                        Schema.str("laterality", "Optional laterality ('Bilateral' or 'Unilateral')"),
                        Schema.str("description", "Optional description")
                    )
                ),
                defineFunction(
                    name = "update_exercise",
                    description = "Update an existing exercise.",
                    parameters = listOf(
                        Schema.int("id", "ID of the exercise to update"),
                        Schema.str("name", "New name"),
                        Schema.str("type", "New type"),
                        Schema.str("group", "New group name"),
                        Schema.int("targetSets", "New target sets"),
                        Schema.int("targetValue", "New target reps/seconds"),
                        Schema.str("laterality", "New laterality"),
                        Schema.str("description", "New description")
                    )
                ),
                defineFunction(
                    name = "delete_exercise",
                    description = "Delete an exercise and its associated Todo tasks.",
                    parameters = listOf(
                        Schema.int("id", "ID of the exercise to delete")
                    )
                ),
                defineFunction(
                    name = "create_program",
                    description = "Create a new workout program.",
                    parameters = listOf(
                        Schema.str("name", "Name of the program")
                    )
                ),
                defineFunction(
                    name = "update_program",
                    description = "Rename a workout program.",
                    parameters = listOf(
                        Schema.int("id", "ID of the program"),
                        Schema.str("name", "New name")
                    )
                ),
                defineFunction(
                    name = "delete_program",
                    description = "Delete a program.",
                    parameters = listOf(
                        Schema.int("id", "ID of the program to delete")
                    )
                ),
                defineFunction(
                    name = "add_program_exercise",
                    description = "Add an exercise to a program.",
                    parameters = listOf(
                        Schema.int("programId", "ID of the program"),
                        Schema.int("exerciseId", "ID of the exercise"),
                        Schema.int("sets", "Number of sets"),
                        Schema.int("targetValue", "Target reps/seconds"),
                        Schema.int("intervalSeconds", "Rest interval in seconds"),
                        Schema.int("loopId", "Optional loop ID")
                    )
                ),
                defineFunction(
                    name = "update_program_exercise",
                    description = "Update an exercise entry within a program.",
                    parameters = listOf(
                        Schema.int("id", "ID of the program exercise entry"),
                        Schema.int("sets", "New number of sets"),
                        Schema.int("targetValue", "New target reps/seconds"),
                        Schema.int("intervalSeconds", "New rest interval"),
                        Schema.int("loopId", "New loop ID (null to remove from loop)")
                    )
                ),
                defineFunction(
                    name = "delete_program_exercise",
                    description = "Remove an exercise from a program.",
                    parameters = listOf(
                        Schema.int("id", "ID of the program exercise entry to delete")
                    )
                ),
                defineFunction(
                    name = "add_program_loop",
                    description = "Add a repetition loop to a program.",
                    parameters = listOf(
                        Schema.int("programId", "ID of the program"),
                        Schema.int("rounds", "Number of rounds"),
                        Schema.int("restBetweenRounds", "Rest between rounds in seconds")
                    )
                ),
                defineFunction(
                    name = "update_program_loop",
                    description = "Update a program loop's settings.",
                    parameters = listOf(
                        Schema.int("id", "ID of the loop"),
                        Schema.int("rounds", "New number of rounds"),
                        Schema.int("restBetweenRounds", "New rest between rounds")
                    )
                ),
                defineFunction(
                    name = "delete_program_loop",
                    description = "Delete a loop from a program. Exercises in the loop will remain but become unlooped.",
                    parameters = listOf(
                        Schema.int("id", "ID of the loop to delete")
                    )
                ),
                defineFunction(
                    name = "create_group",
                    description = "Create a new exercise group.",
                    parameters = listOf(
                        Schema.str("name", "Name of the group")
                    )
                ),
                defineFunction(
                    name = "rename_group",
                    description = "Rename an exercise group.",
                    parameters = listOf(
                        Schema.str("oldName", "Current name of the group"),
                        Schema.str("newName", "New name for the group")
                    )
                ),
                defineFunction(
                    name = "delete_group",
                    description = "Delete an exercise group.",
                    parameters = listOf(
                        Schema.str("name", "Name of the group to delete")
                    )
                ),
                defineFunction(
                    name = "add_todo_task",
                    description = "Add an exercise or program to the Todo list.",
                    parameters = listOf(
                        Schema.str("type", "Task type ('EXERCISE', 'PROGRAM', 'GROUP', or 'INTERVAL')"),
                        Schema.int("referenceId", "ID of the exercise, program, or group")
                    )
                ),
                defineFunction(
                    name = "delete_todo_task",
                    description = "Delete a Todo task.",
                    parameters = listOf(
                        Schema.int("id", "ID of the task to delete")
                    )
                ),
                defineFunction(
                    name = "complete_todo_task",
                    description = "Mark a Todo task as completed.",
                    parameters = listOf(
                        Schema.str("type", "Task type"),
                        Schema.int("referenceId", "Reference ID of the item")
                    )
                ),
                defineFunction(
                    name = "update_ai_memory",
                    description = "Update the persistent memory about the user.",
                    parameters = listOf(
                        Schema.str("newMemory", "Concise summary of everything known about the user (replaces existing memory)")
                    )
                ),
                defineFunction(
                    name = "suggest_workout",
                    description = "Suggest a workout program. This will trigger a specialized UI dialog.",
                    parameters = listOf(
                        Schema.str("communityShareJson", "Complete CommunityShareData JSON string")
                    )
                )
            )
        )
    )

    suspend fun fetchAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        val apiKey = workoutPreferences.getGeminiApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val modelList = json.decodeFromString<GeminiModelList>(responseText)

                modelList.models
                    .filter { it.supportedGenerationMethods.contains("generateContent") }
                    .map { it.name.removePrefix("models/") }
                    .filter { !it.contains("tts", ignoreCase = true) } // Filter out TTS etc as requested
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun generateResponse(
        prompt: String,
        contextData: String,
        history: List<io.github.gonbei774.calisthenicsmemory.data.AiMessage> = emptyList(),
        toolHandler: suspend (String, Map<String, String?>) -> JSONObject = { _, _ -> JSONObject() }
    ): String? {
        return withContext(Dispatchers.IO) {
            val model = getModel(tools) ?: return@withContext "Please set your Gemini API Key in Settings first."

            val aiMemory = workoutPreferences.getAiMemory()
            val aiMemoryPrompt = if (aiMemory.isNotBlank()) {
                "\nCoach Memory (Your knowledge about the user):\n$aiMemory\n"
            } else ""

            val systemInstruction = """
                You are a professional calisthenics coach assistant for the "Calisthenics Memory" app.
                The app uses a specific JSON format for exercises, programs, and records.
                $aiMemoryPrompt
                Current Context (JSON):
                $contextData

                Guidelines:
                1. Provide helpful, encouraging, and science-based calisthenics advice.
                2. Be aware of popular calisthenics programs like Convict Conditioning, Start Bodyweight, the Reddit Recommended Routine (RR), and concepts like Grease the Groove (GtG).
                3. Use the provided tools to manage the user's database (exercises, programs, todo list).
                4. If the user asks to modify, delete, or reorganize data, use the appropriate tool.
                5. If suggesting a workout, use the 'suggest_workout' tool with a complete CommunityShareData JSON.
                6. You can proactively update your 'Coach Memory' using the 'update_ai_memory' tool when you learn something new about the user.
                7. Always prioritize safety and progressive overload.
                8. Keep responses concise and focused on calisthenics.
                9. If analyzing history, look for plateaus (3+ weeks without improvement) and suggest deloads or intensity adjustments.
                10. Refer to the conversation history to maintain context.
                11. Do NOT return JSON blocks for 'auto_update' or 'memory_update'. Use the tools instead.
            """.trimIndent()

            val chatHistory = history.map {
                content(if (it.isUser) "user" else "model") { text(it.text) }
            }

            val chat = model.startChat(chatHistory)

            try {
                // Initial message with system instruction as part of the context if possible,
                // but GenerativeModel doesn't have a direct system instruction field in this version.
                // We'll prepended it to the user prompt.
                val initialPromptWithInstructions = if (chatHistory.isEmpty()) {
                    "$systemInstruction\n\nUser Request: $prompt"
                } else {
                    prompt
                }

                var response = chat.sendMessage(initialPromptWithInstructions)

                // Tool call loop
                for (i in 1..5) { // Limit iterations
                    val toolCalls = response.candidates.first().content.parts.filterIsInstance<FunctionCallPart>()
                    if (toolCalls.isEmpty()) break

                    val toolResponses = toolCalls.map { call ->
                        val result = toolHandler(call.name, call.args)
                        FunctionResponsePart(call.name, result)
                    }

                    val responseContent = Content(role = "function", parts = toolResponses)
                    response = chat.sendMessage(responseContent)
                }

                response.text
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
