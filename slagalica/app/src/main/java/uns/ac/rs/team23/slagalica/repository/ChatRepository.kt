package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto

interface ChatRepository {
    suspend fun getHistory(region: String): Result<List<ChatMessageDto>>
    suspend fun sendMessage(region: String, content: String): Result<ChatMessageDto>
    fun connectStomp(region: String, wsBaseUrl: String, onMessage: (ChatMessageDto) -> Unit)
    fun disconnectStomp()
}
