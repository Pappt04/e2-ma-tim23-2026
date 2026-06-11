package uns.ac.rs.team23.server.service

import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uns.ac.rs.team23.server.dto.chat.ChatMessageResponse
import uns.ac.rs.team23.server.model.ChatMessage
import uns.ac.rs.team23.server.repository.ChatMessageRepository
import uns.ac.rs.team23.server.repository.UserRepository

@Service
class ChatService(
    private val messageRepository: ChatMessageRepository,
    private val userRepository: UserRepository,
    private val messaging: SimpMessagingTemplate,
    private val ntfy: NtfyService,
) {

    @Transactional
    fun sendMessage(senderId: Long, region: String, content: String): ChatMessageResponse {
        require(content.isNotBlank()) { "Message cannot be empty" }
        require(content.length <= 1000) { "Message too long" }

        val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("User not found") }
        require(sender.region == region) { "You can only chat in your own region" }

        val msg = messageRepository.save(ChatMessage(sender = sender, region = region, content = content))
        val response = ChatMessageResponse.from(msg)

        // Broadcast to everyone subscribed to this region's chat
        messaging.convertAndSend("/topic/chat/$region", response)

        // Push to all other users in region so they get notified even when offline
        val preview = content.take(80)
        userRepository.findAllByRegion(region)
            .filter { it.id != senderId && !it.isGuest }
            .forEach { ntfy.notify(it.id, "New message in $region", "${sender.username}: $preview", tags = "speech_balloon") }

        return response
    }

    fun getRecentMessages(region: String, limit: Int = 50): List<ChatMessageResponse> =
        messageRepository.findByRegionOrderBySentAtDesc(region, PageRequest.of(0, limit))
            .reversed()
            .map { ChatMessageResponse.from(it) }
}
