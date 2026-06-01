package io.github.gonbei774.calisthenicsmemory.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gonbei774.calisthenicsmemory.data.AiMessage
import io.github.gonbei774.calisthenicsmemory.data.AiThread
import io.github.gonbei774.calisthenicsmemory.data.AppDatabase
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.service.AiService
import kotlinx.serialization.json.Json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val aiDao = database.aiDao()
    private val workoutPreferences = WorkoutPreferences(application)
    private val aiService = AiService(workoutPreferences)

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
    val isLoading = _isLoading.asStateFlow()

    fun sendMessage(text: String, contextData: String) {
        viewModelScope.launch {
            var threadId = _currentThreadId.value
            if (threadId == null) {
                // Create a new thread if none exists
                val title = if (text.length > 30) text.take(27) + "..." else text
                threadId = aiDao.insertThread(AiThread(title = title))
                _currentThreadId.value = threadId
            }

            val userMessage = AiMessage(threadId = threadId, text = text, isUser = true)
            aiDao.insertMessage(userMessage)

            _isLoading.value = true
            val history = aiDao.getMessagesForThreadSync(threadId)
            val response = aiService.generateResponse(text, contextData, history)

            val aiMessageText = response ?: "Sorry, I couldn't process that."
            val aiMessage = AiMessage(
                threadId = threadId,
                text = aiMessageText,
                isUser = false
            )
            aiDao.insertMessage(aiMessage)

            // Extract memory update
            extractMemoryUpdate(aiMessageText)?.let { memoryJson ->
                try {
                    val update = Json { ignoreUnknownKeys = true }.decodeFromString<MemoryUpdate>(memoryJson)
                    workoutPreferences.setAiMemory(update.newMemory)
                } catch (e: Exception) {
                    // Silently fail for memory updates
                }
            }

            _isLoading.value = false
        }
    }

    fun startNewThread(initialText: String? = null, contextData: String? = null) {
        viewModelScope.launch {
            _currentThreadId.value = null
            if (initialText != null && contextData != null) {
                sendMessage(initialText, contextData)
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
}

// Keeping ChatMessage data class if it is used elsewhere for compatibility,
// though AiMessage is now the primary model.
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
