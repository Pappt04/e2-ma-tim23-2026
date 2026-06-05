package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import uns.ac.rs.team23.server.config.UserPrincipal
import uns.ac.rs.team23.server.dto.chat.SendMessageRequest
import uns.ac.rs.team23.server.service.ChatService
import java.security.Principal

@Controller
@RestController
@RequestMapping("/api/chat")
class ChatController(private val chatService: ChatService) {

    // ─── REST: history ───────────────────────────────────────────────────────

    @GetMapping("/{region}/messages")
    fun getHistory(
        @PathVariable region: String,
        @RequestParam(defaultValue = "50") limit: Int,
        session: HttpSession,
    ): ResponseEntity<Any> {
        session.getAttribute("userId") as? Long
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        return ResponseEntity.ok(chatService.getRecentMessages(region, limit.coerceIn(1, 200)))
    }

    // ─── WebSocket: send message ─────────────────────────────────────────────

    // Client sends to: /app/chat/{region}
    // Server broadcasts to: /topic/chat/{region}
    @MessageMapping("/chat/{region}")
    fun handleMessage(
        @DestinationVariable region: String,
        @Payload req: SendMessageRequest,
        principal: Principal?,
    ) {
        val userId = (principal as? UserPrincipal)?.userId ?: return
        try {
            chatService.sendMessage(userId, region, req.content)
        } catch (_: IllegalArgumentException) {
            // Silently drop invalid messages over WebSocket
        }
    }
}
