package uns.ac.rs.team23.slagalica.network.dto

data class SendMessageRequest(
    val content: String,
)

data class ChatMessageDto(
    val id: String,
    val senderUsername: String,
    val region: String,
    val content: String,
    val sentAt: String,
)
