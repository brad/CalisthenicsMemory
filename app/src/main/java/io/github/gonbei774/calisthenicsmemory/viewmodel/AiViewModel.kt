package io.github.gonbei774.calisthenicsmemory.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gonbei774.calisthenicsmemory.data.WorkoutPreferences
import io.github.gonbei774.calisthenicsmemory.service.AiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val workoutPreferences = WorkoutPreferences(application)
    private val aiService = AiService(workoutPreferences)

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun sendMessage(text: String, contextData: String) {
        val userMessage = ChatMessage(text, isUser = true)
        _chatMessages.value = _chatMessages.value + userMessage

        _isLoading.value = true
        viewModelScope.launch {
            val response = aiService.generateResponse(text, contextData)
            val aiMessage = ChatMessage(response ?: "Sorry, I couldn't process that.", isUser = false)
            _chatMessages.value = _chatMessages.value + aiMessage
            _isLoading.value = false
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
