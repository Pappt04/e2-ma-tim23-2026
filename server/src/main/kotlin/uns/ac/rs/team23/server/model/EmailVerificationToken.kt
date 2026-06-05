package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val token: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = User(),

    val expiresAt: LocalDateTime = LocalDateTime.now().plusHours(24),
) {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
}
