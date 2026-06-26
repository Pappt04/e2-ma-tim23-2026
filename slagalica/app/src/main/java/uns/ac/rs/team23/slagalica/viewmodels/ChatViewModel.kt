package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.DailyMissionType
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto
import uns.ac.rs.team23.slagalica.repository.ChatRepository
import uns.ac.rs.team23.slagalica.repository.DailyMissionRepository

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val dailyMissionRepository: DailyMissionRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val messages: StateFlow<List<ChatMessageDto>> = _messages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    var inputText by mutableStateOf("")
        private set

    private var region: String = ""
    private var myUsername: String = ""

    fun init(region: String, username: String) {
        this.region = region
        this.myUsername = username
        loadHistory()
        viewModelScope.launch {
            chatRepository.observeMessages(region).collect { incoming ->
                _messages.update { existing ->
                    if (existing.any { it.id == incoming.id && incoming.id.isNotBlank() }) existing
                    else existing + incoming
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            chatRepository.getHistory(region)
                .onSuccess { _messages.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun onInputChange(text: String) { inputText = text }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank()) return
        inputText = ""
        viewModelScope.launch {
            chatRepository.sendMessage(region, text)
                .onSuccess { dailyMissionRepository.completeMission(DailyMissionType.SEND_CHAT) }
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() { _error.value = null }

    fun isMyMessage(msg: ChatMessageDto): Boolean = msg.senderUsername == myUsername
}
