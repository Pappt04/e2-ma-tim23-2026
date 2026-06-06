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
import uns.ac.rs.team23.slagalica.BuildConfig
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto
import uns.ac.rs.team23.slagalica.repository.ChatRepository

class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val messages: StateFlow<List<ChatMessageDto>> = _messages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    var inputText by mutableStateOf("")
        private set

    private var region: String = ""
    private var myUsername: String = ""

    private val wsUrl = BuildConfig.BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .removeSuffix("/") + "/ws-native"

    fun init(region: String, username: String) {
        this.region = region
        this.myUsername = username
        loadHistory()
        chatRepository.connectStomp(region, wsUrl) { incoming ->
            _messages.update { existing ->
                // Avoid duplicate if we already added it optimistically
                if (existing.any { it.id == incoming.id && incoming.id > 0 }) existing
                else existing + incoming
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
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() { _error.value = null }

    fun isMyMessage(msg: ChatMessageDto): Boolean = msg.senderUsername == myUsername

    override fun onCleared() {
        chatRepository.disconnectStomp()
        super.onCleared()
    }
}
