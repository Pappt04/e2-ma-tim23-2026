package uns.ac.rs.team23.server.dto.chat

import uns.ac.rs.team23.server.model.ChatMessage

data class ChatMessageResponse(
    val id: Long,
    val senderId: Long,
    val senderUsername: String,
    val region: String,
    val content: String,
    val sentAt: String,
) {
    companion object {
        fun from(msg: ChatMessage) = ChatMessageResponse(
            id = msg.id,
            senderId = msg.sender.id,
            senderUsername = msg.sender.username,
            region = msg.region,
            content = msg.content,
            sentAt = msg.sentAt.toString(),
        )
    }
}
