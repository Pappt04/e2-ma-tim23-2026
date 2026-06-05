package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

enum class InviteStatus { PENDING, ACCEPTED, REJECTED, EXPIRED }

@Entity
@Table(name = "match_invites")
class MatchInvite(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    val inviter: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    val invitee: User,

    val isFriendly: Boolean = false,

    @Enumerated(EnumType.STRING)
    var status: InviteStatus = InviteStatus.PENDING,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val expiresAt: LocalDateTime = LocalDateTime.now().plusSeconds(10),
) {
    fun isExpired() = LocalDateTime.now().isAfter(expiresAt)
}
