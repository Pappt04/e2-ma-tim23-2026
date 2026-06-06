package uns.ac.rs.team23.slagalica.repository

import com.google.gson.Gson
import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.StompClient
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto
import uns.ac.rs.team23.slagalica.network.dto.SendMessageRequest
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteChatRepository(
    private val api: ApiService,
    private val stomp: StompClient,
) : ChatRepository {

    private val gson = Gson()

    override suspend fun getHistory(region: String): Result<List<ChatMessageDto>> = runCatching {
        val response = api.getChatHistory(region)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun sendMessage(region: String, content: String): Result<ChatMessageDto> = runCatching {
        if (stomp.isConnected) {
            val json = gson.toJson(mapOf("content" to content))
            stomp.send("/app/chat/$region", json)
            // Optimistic: return a placeholder; the server will broadcast back via STOMP
            ChatMessageDto(id = -1, senderUsername = "", region = region, content = content, sentAt = "")
        } else {
            val response = api.sendChatMessage(region, SendMessageRequest(content))
            if (response.isSuccessful) response.body()!!
            else throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override fun connectStomp(region: String, wsBaseUrl: String, onMessage: (ChatMessageDto) -> Unit) {
        stomp.connect(wsBaseUrl) {
            stomp.subscribe("/topic/chat/$region") { json ->
                runCatching {
                    val dto = gson.fromJson(json, ChatMessageDto::class.java)
                    onMessage(dto)
                }
            }
        }
    }

    override fun disconnectStomp() = stomp.disconnect()
}
