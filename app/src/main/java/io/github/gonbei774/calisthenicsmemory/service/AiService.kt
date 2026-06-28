package io.github.gonbei774.calisthenicsmemory.service

import com.google.genai.Client
import com.google.genai.types.*
import io.github.gonbei774.calisthenicsmemory.data.AiMessage
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Optional

sealed class GenerateResponseResult {
    data class Success(val text: String) : GenerateResponseResult()
    data class Error(val message: String) : GenerateResponseResult()
    data class RateLimit(val retryAfterSeconds: Int) : GenerateResponseResult()
}

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

    private fun getClient(): Client? {
        val apiKey = workoutPreferences.getGeminiApiKey()
        if (apiKey.isBlank()) return null
        return Client.builder().apiKey(apiKey).build()
    }

    private fun defineFunction(funcName: String, funcDesc: String, params: Map<String, Any>, req: List<String>): FunctionDeclaration {
        val schemaMap: Map<String, Any> = mapOf(
            "type" to "OBJECT",
            "properties" to params,
            "required" to req
        )

        return FunctionDeclaration.builder()
            .name(funcName)
            .description(funcDesc)
            .parametersJsonSchema(schemaMap)
            .build()
    }

    private val toolsList = listOf(
        Tool.builder().functionDeclarations(listOf(
            defineFunction(
                "add_exercise",
                "Add a new exercise to the user's library.",
                mapOf(
                    "name" to mapOf("type" to "STRING", "description" to "Name of the exercise"),
                    "type" to mapOf("type" to "STRING", "description" to "Exercise type ('DYNAMIC' or 'ISOMETRIC')"),
                    "group" to mapOf("type" to "STRING", "description" to "Group/Category name (e.g., Push, Pull, Legs)"),
                    "targetSets" to mapOf("type" to "INTEGER", "description" to "Target number of sets"),
                    "targetValue" to mapOf("type" to "INTEGER", "description" to "Target reps or hold time in seconds"),
                    "laterality" to mapOf("type" to "STRING", "description" to "Laterality ('BILATERAL', 'UNILATERAL', or 'ALTERNATING')"),
                    "description" to mapOf("type" to "STRING", "description" to "Brief description or form cues")
                ),
                listOf("name", "type", "group")
            ),
            defineFunction(
                "update_exercise",
                "Update an existing exercise.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the exercise to update"),
                    "name" to mapOf("type" to "STRING"),
                    "type" to mapOf("type" to "STRING"),
                    "group" to mapOf("type" to "STRING"),
                    "targetSets" to mapOf("type" to "INTEGER"),
                    "targetValue" to mapOf("type" to "INTEGER"),
                    "laterality" to mapOf("type" to "STRING"),
                    "description" to mapOf("type" to "STRING")
                ),
                listOf("id")
            ),
            defineFunction(
                "delete_exercise",
                "Delete an exercise from the library.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the exercise to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "create_program",
                "Create a new workout program.",
                mapOf(
                    "name" to mapOf("type" to "STRING", "description" to "Name of the program")
                ),
                listOf("name")
            ),
            defineFunction(
                "update_program",
                "Rename a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program"),
                    "name" to mapOf("type" to "STRING", "description" to "New name")
                ),
                listOf("id", "name")
            ),
            defineFunction(
                "delete_program",
                "Delete a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "add_program_exercise",
                "Add an exercise to a program.",
                mapOf(
                    "programId" to mapOf("type" to "INTEGER", "description" to "ID of the program"),
                    "exerciseId" to mapOf("type" to "INTEGER", "description" to "ID of the exercise"),
                    "sets" to mapOf("type" to "INTEGER"),
                    "targetValue" to mapOf("type" to "INTEGER"),
                    "intervalSeconds" to mapOf("type" to "INTEGER"),
                    "loopId" to mapOf("type" to "INTEGER", "description" to "Optional ID of a loop to add this exercise to")
                ),
                listOf("programId", "exerciseId")
            ),
            defineFunction(
                "update_program_exercise",
                "Update an exercise within a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program-exercise connection"),
                    "sets" to mapOf("type" to "INTEGER"),
                    "targetValue" to mapOf("type" to "INTEGER"),
                    "intervalSeconds" to mapOf("type" to "INTEGER")
                ),
                listOf("id")
            ),
            defineFunction(
                "delete_program_exercise",
                "Remove an exercise from a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program-exercise connection to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "add_program_loop",
                "Add a loop (circuit) to a program.",
                mapOf(
                    "programId" to mapOf("type" to "INTEGER", "description" to "ID of the program"),
                    "rounds" to mapOf("type" to "INTEGER", "description" to "Number of rounds for the loop"),
                    "restBetweenRounds" to mapOf("type" to "INTEGER", "description" to "Rest in seconds between rounds")
                ),
                listOf("programId")
            ),
            defineFunction(
                "update_program_loop",
                "Update a program loop's settings.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the loop"),
                    "rounds" to mapOf("type" to "INTEGER", "description" to "New number of rounds"),
                    "restBetweenRounds" to mapOf("type" to "INTEGER", "description" to "New rest between rounds")
                ),
                listOf("id")
            ),
            defineFunction(
                "delete_program_loop",
                "Delete a loop from a program. Exercises in the loop will remain but become unlooped.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the loop to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "create_group",
                "Create a new exercise group.",
                mapOf(
                    "name" to mapOf("type" to "STRING", "description" to "Name of the group")
                ),
                listOf("name")
            ),
            defineFunction(
                "rename_group",
                "Rename an exercise group.",
                mapOf(
                    "oldName" to mapOf("type" to "STRING", "description" to "Current name of the group"),
                    "newName" to mapOf("type" to "STRING", "description" to "New name for the group")
                ),
                listOf("oldName", "newName")
            ),
            defineFunction(
                "delete_group",
                "Delete an exercise group.",
                mapOf(
                    "name" to mapOf("type" to "STRING", "description" to "Name of the group to delete")
                ),
                listOf("name")
            ),
            defineFunction(
                "add_todo_task",
                "Add an exercise or program to the Todo list.",
                mapOf(
                    "type" to mapOf("type" to "STRING", "description" to "Task type ('EXERCISE', 'PROGRAM', 'GROUP', or 'INTERVAL')"),
                    "referenceId" to mapOf("type" to "INTEGER", "description" to "ID of the exercise, program, or group")
                ),
                listOf("type", "referenceId")
            ),
            defineFunction(
                "delete_todo_task",
                "Delete a Todo task.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the task to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "complete_todo_task",
                "Mark a Todo task as completed.",
                mapOf(
                    "type" to mapOf("type" to "STRING", "description" to "Task type"),
                    "referenceId" to mapOf("type" to "INTEGER", "description" to "Reference ID of the item")
                ),
                listOf("type", "referenceId")
            ),
            defineFunction(
                "update_ai_memory",
                "Update the persistent memory about the user.",
                mapOf(
                    "newMemory" to mapOf("type" to "STRING", "description" to "Concise summary of everything known about the user (replaces existing memory)")
                ),
                listOf("newMemory")
            ),
            defineFunction(
                "suggest_workout",
                "Suggest a workout program. This will trigger a specialized UI dialog.",
                mapOf(
                    "communityShareJson" to mapOf("type" to "STRING", "description" to "Complete CommunityShareData JSON string")
                ),
                listOf("communityShareJson")
            ),
            defineFunction(
                "get_exercises",
                "Retrieve the list of all exercises in the user's library.",
                emptyMap(),
                emptyList()
            ),
            defineFunction(
                "get_programs",
                "Retrieve the list of all workout programs.",
                emptyMap(),
                emptyList()
            ),
            defineFunction(
                "get_training_records",
                "Retrieve training records. You can filter by exercise ID.",
                mapOf(
                    "exerciseId" to mapOf("type" to "INTEGER", "description" to "Optional exercise ID to filter records")
                ),
                emptyList()
            ),
            defineFunction(
                "get_groups",
                "Retrieve the list of exercise groups.",
                emptyMap(),
                emptyList()
            ),
            defineFunction(
                "get_todo_tasks",
                "Retrieve the current list of Todo tasks.",
                emptyMap(),
                emptyList()
            )
        )).build()
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
                    .filter { !it.contains("tts", ignoreCase = true) }
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
        history: List<AiMessage> = emptyList(),
        toolHandler: suspend (String, Map<String, String?>) -> JSONObject = { _, _ -> JSONObject() }
    ): GenerateResponseResult {
        return withContext(Dispatchers.IO) {
            val client = getClient() ?: return@withContext GenerateResponseResult.Error("Please set your Gemini API Key in Settings first.")
            val modelName = workoutPreferences.getGeminiModel()

            val aiMemory = workoutPreferences.getAiMemory()
            val aiMemoryPrompt = if (aiMemory.isNotBlank()) {
                "\nCoach Memory (Your knowledge about the user):\n$aiMemory\n"
            } else ""

            val systemInstructionText = """
                You are a professional calisthenics coach assistant for the "Calisthenics Memory" app.
                The app uses a specific JSON format for exercises, programs, and records.
                $aiMemoryPrompt
                Current Context (JSON):
                $contextData

                Guidelines:
                1. Provide helpful, encouraging, and science-based calisthenics advice.
                2. Be aware of popular calisthenics programs like Convict Conditioning, Start Bodyweight, the Reddit Recommended Routine (RR), and concepts like Grease the Groove (GtG).
                3. Use the provided tools to manage and retrieve the user's database (exercises, programs, records, todo list).
                4. If you need data that is not in the current context, use the 'get_...' tools.
                5. If the user asks to modify, delete, or reorganize data, use the appropriate tool.
                6. If suggesting a workout, use the 'suggest_workout' tool with a complete CommunityShareData JSON.
                7. You can proactively update your 'Coach Memory' using the 'update_ai_memory' tool when you learn something new about the user.
                8. Always prioritize safety and progressive overload.
                9. Keep responses concise and focused on calisthenics.
                10. If analyzing history, look for plateaus (3+ weeks without improvement) and suggest deloads or intensity adjustments.
                11. Refer to the conversation history to maintain context.
                12. Do NOT return JSON blocks for 'auto_update' or 'memory_update'. Use the tools instead.
            """.trimIndent()

            val chatHistory = history.map {
                Content.builder()
                    .role(if (it.isUser) "user" else "model")
                    .parts(listOf(Part.builder().text(it.text).build()))
                    .build()
            }

            val config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemInstructionText)))
                .tools(toolsList)
                .build()

            try {
                val contents = chatHistory.toMutableList()
                contents.add(Content.builder().role("user").parts(listOf(Part.builder().text(prompt).build())).build())

                var response = client.models.generateContent(modelName, contents, config)

                // Tool call loop
                for (i in 1..5) {
                    val candidate = response.candidates().orElse(emptyList()).firstOrNull() ?: break
                    val candidateContent = candidate.content().orElse(null) ?: break
                    val parts = candidateContent.parts().orElse(emptyList())
                    val toolCalls = parts.filter { it.functionCall().isPresent }

                    if (toolCalls.isEmpty()) break

                    val toolResponses = toolCalls.map { part ->
                        val call = part.functionCall().get()
                        val funcName = call.name().get()
                        val funcArgs = call.args().orElse(emptyMap()).mapValues { it.value?.toString() }
                        val result = toolHandler(funcName, funcArgs)

                        val resultMap = mutableMapOf<String, Any?>()
                        val keys = result.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            resultMap[key] = result.get(key)
                        }

                        Part.builder()
                            .functionResponse(
                                FunctionResponse.builder()
                                    .name(funcName)
                                    .response(resultMap)
                                    .build()
                            )
                            .build()
                    }

                    contents.add(candidateContent)
                    contents.add(Content.builder().role("function").parts(toolResponses).build())

                    response = client.models.generateContent(modelName, contents, config)
                }

                GenerateResponseResult.Success(response.text() ?: "")
            } catch (e: Exception) {
                val message = e.message ?: "Unknown error"
                if (message.contains("429") || message.contains("Quota exceeded", ignoreCase = true)) {
                    val retryAfter = parseRetryAfter(message)
                    GenerateResponseResult.RateLimit(retryAfter)
                } else {
                    GenerateResponseResult.Error("Error: $message")
                }
            }
        }
    }

    private fun parseRetryAfter(message: String): Int {
        val regexJson = """retryDelay":"(\d+)s"""".toRegex()
        val matchJson = regexJson.find(message)
        if (matchJson != null) {
            return matchJson.groupValues[1].toIntOrNull() ?: 60
        }

        val regexText = """Please retry in ([\d\.]+)s""".toRegex()
        val matchText = regexText.find(message)
        if (matchText != null) {
            val secondsStr = matchText.groupValues[1]
            return secondsStr.toDoubleOrNull()?.toInt() ?: 60
        }

        return 60
    }
}
