package uns.ac.rs.team23.slagalica.repository

import kotlinx.coroutines.flow.Flow
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto

interface ChatRepository {
    suspend fun getHistory(region: String): Result<List<ChatMessageDto>>
    suspend fun sendMessage(region: String, content: String): Result<ChatMessageDto>
    fun observeMessages(region: String): Flow<ChatMessageDto>
}
