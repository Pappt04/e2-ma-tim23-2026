package uns.ac.rs.team23.server.config

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class WebSocketAuthInterceptor : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            val userId = accessor.sessionAttributes?.get("userId") as? Long
            if (userId != null) {
                accessor.user = UserPrincipal(userId)
            }
        }
        return message
    }
}

class UserPrincipal(val userId: Long) : Principal {
    override fun getName(): String = userId.toString()
}
