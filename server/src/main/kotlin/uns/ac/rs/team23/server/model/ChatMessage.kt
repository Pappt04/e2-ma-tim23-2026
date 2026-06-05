package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @Column(nullable = false)
    val region: String,

    @Column(nullable = false, length = 1000)
    val content: String,

    val sentAt: LocalDateTime = LocalDateTime.now(),
)
