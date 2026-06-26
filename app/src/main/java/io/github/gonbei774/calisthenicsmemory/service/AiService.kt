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
                "Add a new exercise to the database.",
                mapOf(
                    "name" to mapOf("type" to "STRING", "description" to "Name of the exercise"),
                    "type" to mapOf("type" to "STRING", "description" to "Type of exercise ('Dynamic' or 'Isometric')"),
                    "group" to mapOf("type" to "STRING", "description" to "Optional group name"),
                    "targetSets" to mapOf("type" to "INTEGER", "description" to "Optional target sets"),
                    "targetValue" to mapOf("type" to "INTEGER", "description" to "Optional target reps/seconds"),
                    "laterality" to mapOf("type" to "STRING", "description" to "Optional laterality ('Bilateral' or 'Unilateral')"),
                    "description" to mapOf("type" to "STRING", "description" to "Optional description")
                ),
                listOf("name", "type")
            ),
            defineFunction(
                "update_exercise",
                "Update an existing exercise.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the exercise to update"),
                    "name" to mapOf("type" to "STRING", "description" to "New name"),
                    "type" to mapOf("type" to "STRING", "description" to "New type"),
                    "group" to mapOf("type" to "STRING", "description" to "New group name"),
                    "targetSets" to mapOf("type" to "INTEGER", "description" to "New target sets"),
                    "targetValue" to mapOf("type" to "INTEGER", "description" to "New target reps/seconds"),
                    "laterality" to mapOf("type" to "STRING", "description" to "New laterality"),
                    "description" to mapOf("type" to "STRING", "description" to "New description")
                ),
                listOf("id")
            ),
            defineFunction(
                "delete_exercise",
                "Delete an exercise and its associated Todo tasks.",
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
                "Rename a workout program.",
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
                    "sets" to mapOf("type" to "INTEGER", "description" to "Number of sets"),
                    "targetValue" to mapOf("type" to "INTEGER", "description" to "Target reps/seconds"),
                    "intervalSeconds" to mapOf("type" to "INTEGER", "description" to "Rest interval in seconds"),
                    "loopId" to mapOf("type" to "INTEGER", "description" to "Optional loop ID")
                ),
                listOf("programId", "exerciseId", "sets", "targetValue", "intervalSeconds")
            ),
            defineFunction(
                "update_program_exercise",
                "Update an exercise entry within a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program exercise entry"),
                    "sets" to mapOf("type" to "INTEGER", "description" to "New number of sets"),
                    "targetValue" to mapOf("type" to "INTEGER", "description" to "New target reps/seconds"),
                    "intervalSeconds" to mapOf("type" to "INTEGER", "description" to "New rest interval"),
                    "loopId" to mapOf("type" to "INTEGER", "description" to "New loop ID (null to remove from loop)")
                ),
                listOf("id")
            ),
            defineFunction(
                "delete_program_exercise",
                "Remove an exercise from a program.",
                mapOf(
                    "id" to mapOf("type" to "INTEGER", "description" to "ID of the program exercise entry to delete")
                ),
                listOf("id")
            ),
            defineFunction(
                "add_program_loop",
                "Add a repetition loop to a program.",
                mapOf(
                    "programId" to mapOf("type" to "INTEGER", "description" to "ID of the program"),
                    "rounds" to mapOf("type" to "INTEGER", "description" to "Number of rounds"),
                    "restBetweenRounds" to mapOf("type" to "INTEGER", "description" to "Rest between rounds in seconds")
                ),
                listOf("programId", "rounds", "restBetweenRounds")
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
    ): String? {
        return withContext(Dispatchers.IO) {
            val client = getClient() ?: return@withContext "Please set your Gemini API Key in Settings first."
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
                    val candidate = response.candidates().get()[0]
                    val candidateContent = candidate.content().get()
                    val parts = candidateContent.parts().get()
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

                response.text()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
