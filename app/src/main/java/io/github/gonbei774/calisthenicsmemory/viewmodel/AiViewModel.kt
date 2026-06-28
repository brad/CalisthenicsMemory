package io.github.gonbei774.calisthenicsmemory.viewmodel

import io.github.gonbei774.calisthenicsmemory.R
import kotlinx.serialization.encodeToString
import io.github.gonbei774.calisthenicsmemory.data.AiMessage
import io.github.gonbei774.calisthenicsmemory.service.GenerateResponseResult
import kotlinx.coroutines.delay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gonbei774.calisthenicsmemory.data.AiThread
import io.github.gonbei774.calisthenicsmemory.data.AppDatabase
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.service.AiService
import kotlinx.serialization.json.Json
import io.github.gonbei774.calisthenicsmemory.viewmodel.BackupData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import io.github.gonbei774.calisthenicsmemory.data.TrainingRecord
import io.github.gonbei774.calisthenicsmemory.data.Program
import io.github.gonbei774.calisthenicsmemory.data.Exercise

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val aiDao = database.aiDao()
    private val workoutPreferences = WorkoutPreferences(application)
    private val aiService = AiService(workoutPreferences)
    private val json = Json { ignoreUnknownKeys = true }

    private val _currentThreadId = MutableStateFlow<Long?>(null)
    val currentThreadId = _currentThreadId.asStateFlow()

    val allThreads: StateFlow<List<AiThread>> = aiDao.getAllThreads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<AiMessage>> = _currentThreadId.flatMapLatest { threadId ->
        if (threadId != null) {
            aiDao.getMessagesForThread(threadId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    private val _retryCountdown = MutableStateFlow(0)
    val retryCountdown = _retryCountdown.asStateFlow()

    val isLoading = _isLoading.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    fun fetchModels() {
        viewModelScope.launch {
            val models = aiService.fetchAvailableModels()
            _availableModels.value = models
        }
    }

    fun sendMessage(text: String, contextData: String, trainingViewModel: TrainingViewModel? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            var threadId = _currentThreadId.value
            if (threadId == null) {
                threadId = aiDao.insertThread(AiThread(title = text.take(50)))
                _currentThreadId.value = threadId
            }

            // Snapshot data for undo if tools might be called
            val snapshotJson = trainingViewModel?.getAllDataAsJson()

            aiDao.insertMessage(AiMessage(threadId = threadId, text = text, isUser = true))

            // Exclude the message we just inserted from history because it is passed as 'prompt'
            val history = aiDao.getMessagesForThreadSync(threadId).filter { it.text != text }
            var workoutJsonToAppend: String? = null

            var anyModificationMade = false
            val toolHandler: suspend (String, Map<String, String?>) -> JSONObject = { funcName, args ->
                if (isModificationTool(funcName)) anyModificationMade = true
                val result = JSONObject()
                try {
                    when (funcName) {
                        "add_exercise" -> {
                            val id = trainingViewModel?.addExerciseSuspend(
                                name = args["name"] ?: "",
                                type = args["type"] ?: "DYNAMIC",
                                group = args["group"] ?: "",
                                targetSets = args["targetSets"]?.toIntOrNull() ?: 3,
                                targetValue = args["targetValue"]?.toIntOrNull() ?: 10,
                                laterality = args["laterality"] ?: "BILATERAL",
                                description = args["description"] ?: ""
                            )
                            result.put("success", id != null)
                            if (id != null) result.put("id", id)
                        }
                        "update_exercise" -> {
                            val id = args["id"]?.toLongOrNull()
                            val exercise = trainingViewModel?.exercises?.value?.find { it.id == id }
                            if (exercise != null) {
                                val updated = exercise.copy(
                                    name = args["name"] ?: exercise.name,
                                    type = args["type"] ?: exercise.type,
                                    group = args["group"] ?: exercise.group,
                                    targetSets = args["targetSets"]?.toIntOrNull() ?: exercise.targetSets,
                                    targetValue = args["targetValue"]?.toIntOrNull() ?: exercise.targetValue,
                                    laterality = args["laterality"] ?: exercise.laterality,
                                    description = args["description"] ?: exercise.description
                                )
                                val success = trainingViewModel.updateExerciseSuspend(updated)
                                result.put("success", success)
                            } else {
                                result.put("success", false)
                                result.put("error", "Exercise not found")
                            }
                        }
                        "delete_exercise" -> {
                            val id = args["id"]?.toLongOrNull()
                            val exercise = trainingViewModel?.exercises?.value?.find { it.id == id }
                            if (exercise != null) {
                                val success = trainingViewModel.deleteExerciseSuspend(exercise)
                                result.put("success", success)
                            } else {
                                result.put("success", false)
                                result.put("error", "Exercise not found")
                            }
                        }
                        "create_program" -> {
                            val id = trainingViewModel?.createProgramAndGetId(args["name"] ?: "New Program")
                            result.put("success", id != null)
                            if (id != null) result.put("id", id)
                        }
                        "update_program" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                val success = trainingViewModel?.updateProgram(io.github.gonbei774.calisthenicsmemory.data.Program(id, args["name"] ?: "")) ?: false
                                result.put("success", success)
                            } else result.put("success", false)
                        }
                        "delete_program" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                val success = trainingViewModel?.deleteProgramSuspend(id) ?: false
                                result.put("success", success)
                            } else result.put("success", false)
                        }
                        "add_program_exercise" -> {
                            val pid = args["programId"]?.toLongOrNull()
                            val eid = args["exerciseId"]?.toLongOrNull()
                            if (pid != null && eid != null) {
                                val id = trainingViewModel?.addProgramExerciseSync(
                                    programId = pid,
                                    exerciseId = eid,
                                    sets = args["sets"]?.toIntOrNull() ?: 1,
                                    targetValue = args["targetValue"]?.toIntOrNull() ?: 0,
                                    intervalSeconds = args["intervalSeconds"]?.toIntOrNull() ?: 60,
                                    loopId = args["loopId"]?.toLongOrNull()
                                )
                                result.put("success", id != null)
                            } else result.put("success", false)
                        }
                        "update_program_exercise" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                result.put("success", false)
                                result.put("error", "Not implemented yet")
                            } else result.put("success", false)
                        }
                        "delete_program_exercise" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                val success = trainingViewModel?.deleteProgramExercise(io.github.gonbei774.calisthenicsmemory.data.ProgramExercise(id, 0, 0, 0, 0, 0, 0)) ?: false
                                result.put("success", success)
                            } else result.put("success", false)
                        }
                        "add_program_loop" -> {
                            val pid = args["programId"]?.toLongOrNull()
                            if (pid != null) {
                                val id = trainingViewModel?.addProgramLoop(
                                    pid,
                                    args["rounds"]?.toIntOrNull() ?: 3,
                                    args["restBetweenRounds"]?.toIntOrNull() ?: 60
                                )
                                result.put("success", id != null)
                                if (id != null) result.put("id", id)
                            } else result.put("success", false)
                        }
                        "update_program_loop" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                val success = trainingViewModel?.updateProgramLoop(io.github.gonbei774.calisthenicsmemory.data.ProgramLoop(id, 0, 0, args["rounds"]?.toIntOrNull() ?: 3, args["restBetweenRounds"]?.toIntOrNull() ?: 60)) ?: false
                                result.put("success", success)
                            } else result.put("success", false)
                        }
                        "delete_program_loop" -> {
                            val id = args["id"]?.toLongOrNull()
                            if (id != null) {
                                val success = trainingViewModel?.deleteProgramLoop(io.github.gonbei774.calisthenicsmemory.data.ProgramLoop(id, 0, 0, 0, 0)) ?: false
                                result.put("success", success)
                            } else result.put("success", false)
                        }
                        "create_group" -> {
                            val id = trainingViewModel?.createGroupSuspend(args["name"] ?: "")
                            result.put("success", id != null)
                        }
                        "rename_group" -> {
                            val success = trainingViewModel?.renameGroupSuspend(
                                args["oldName"] ?: "",
                                args["newName"] ?: ""
                            ) ?: false
                            result.put("success", success)
                        }
                        "delete_group" -> {
                            val success = trainingViewModel?.deleteGroupSuspend(args["name"] ?: "") ?: false
                            result.put("success", success)
                        }
                        "add_todo_task" -> {
                            val id = trainingViewModel?.addTodoTaskSuspend(
                                args["type"] ?: "EXERCISE",
                                args["referenceId"]?.toLongOrNull() ?: 0L
                            )
                            result.put("success", id != null)
                        }
                        "delete_todo_task" -> {
                            val success = trainingViewModel?.deleteTodoTaskSuspend(args["id"]?.toLongOrNull() ?: 0L) ?: false
                            result.put("success", success)
                        }
                        "complete_todo_task" -> {
                            val success = trainingViewModel?.completeTodoTaskSuspend(
                                args["type"] ?: "",
                                args["referenceId"]?.toLongOrNull() ?: 0L
                            ) ?: false
                            result.put("success", success)
                        }
                        "update_ai_memory" -> {
                            workoutPreferences.setAiMemory(args["newMemory"] ?: "")
                            result.put("success", true)
                        }
                        "suggest_workout" -> {
                            workoutJsonToAppend = args["communityShareJson"]
                            result.put("success", true)
                        }
                        "get_exercises" -> {
                            val data = trainingViewModel?.exercises?.value ?: emptyList()
                            result.put("exercises", Json.encodeToString(data))
                            result.put("success", true)
                        }
                        "get_programs" -> {
                            val data = trainingViewModel?.programs?.value ?: emptyList()
                            result.put("programs", Json.encodeToString(data))
                            result.put("success", true)
                        }
                        "get_training_records" -> {
                            val exerciseId = args["exerciseId"]?.toLongOrNull()
                            val allRecords = trainingViewModel?.records?.value ?: emptyList()
                            val filtered = if (exerciseId != null) {
                                allRecords.filter { it.exerciseId == exerciseId }
                            } else allRecords
                            result.put("records", Json.encodeToString(filtered))
                            result.put("success", true)
                        }
                        "get_groups" -> {
                            val data = trainingViewModel?.groups?.value ?: emptyList()
                            result.put("groups", Json.encodeToString(data))
                            result.put("success", true)
                        }
                        "get_todo_tasks" -> {
                            val data = trainingViewModel?.todoTasks?.value ?: emptyList()
                            result.put("todoTasks", Json.encodeToString(data))
                            result.put("success", true)
                        }
                        else -> result.put("error", "Unknown tool")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    result.put("error", e.message)
                }
                result
            }

            var responseResult: GenerateResponseResult
            var retries = 0
            val maxRetries = 3

            while (true) {
                responseResult = aiService.generateResponse(text, contextData, history, toolHandler)
                if (responseResult is GenerateResponseResult.RateLimit && retries < maxRetries) {
                    retries++
                    var secondsLeft = responseResult.retryAfterSeconds
                    while (secondsLeft > 0) {
                        _retryCountdown.value = secondsLeft
                        delay(1000)
                        secondsLeft--
                    }
                    _retryCountdown.value = 0
                    continue
                }
                break
            }

            val aiMessageText = when (responseResult) {
                is GenerateResponseResult.Success -> {
                    var finalChatText = responseResult.text
                    if (workoutJsonToAppend != null) {
                        finalChatText += "\n\n$workoutJsonToAppend"
                    }
                    finalChatText
                }
                is GenerateResponseResult.RateLimit -> {
                    getApplication<Application>().getString(R.string.ai_coach_error_quota_exceeded)
                }
                is GenerateResponseResult.Error -> {
                    responseResult.message
                }
            }

            val aiMessage = AiMessage(
                threadId = threadId,
                text = aiMessageText,
                isUser = false,
                backupDataJson = if (anyModificationMade) snapshotJson else null
            )
            aiDao.insertMessage(aiMessage)

            _isLoading.value = false
        }
    }

    private fun isModificationTool(name: String): Boolean {
        return name in setOf(
            "add_exercise", "update_exercise", "delete_exercise",
            "create_program", "update_program", "delete_program",
            "add_program_exercise", "update_program_exercise", "delete_program_exercise",
            "add_program_loop", "update_program_loop", "delete_program_loop",
            "create_group", "rename_group", "delete_group",
            "add_todo_task", "delete_todo_task", "complete_todo_task"
        )
    }

    fun startNewThread(initialText: String? = null, contextData: String? = null, trainingViewModel: TrainingViewModel? = null) {
        viewModelScope.launch {
            _currentThreadId.value = null
            if (initialText != null && contextData != null) {
                sendMessage(initialText, contextData, trainingViewModel)
            }
        }
    }

    fun selectThread(threadId: Long) {
        _currentThreadId.value = threadId
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            val thread = aiDao.getThreadById(threadId)
            if (thread != null) {
                aiDao.deleteThread(thread)
                if (_currentThreadId.value == threadId) {
                    _currentThreadId.value = null
                }
            }
        }
    }

    fun clearChat() {
        // For backward compatibility or "Clear current thread"
        _currentThreadId.value?.let { threadId ->
            viewModelScope.launch {
                aiDao.deleteMessagesForThread(threadId)
            }
        }
    }

    fun getAiMemory(): String {
        return workoutPreferences.getAiMemory()
    }

    fun updateAiMemory(memory: String) {
        workoutPreferences.setAiMemory(memory)
    }

    fun undoAutoUpdate(message: AiMessage, trainingViewModel: TrainingViewModel) {
        val backupJson = message.backupDataJson ?: return
        viewModelScope.launch {
            try {
                val backupData = json.decodeFromString<BackupData>(backupJson)
                trainingViewModel.applyAutoUpdate(backupData)
                // Optionally add a system message or update the message to indicate it was undone
                val undoMessage = AiMessage(
                    threadId = message.threadId,
                    text = getApplication<Application>().getString(R.string.ai_coach_auto_updated_undone),
                    isUser = false
                )
                aiDao.insertMessage(undoMessage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}

// Keeping ChatMessage data class if it is used elsewhere for compatibility,
// though AiMessage is now the primary model.
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
