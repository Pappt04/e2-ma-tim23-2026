package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "challenge_participants")
class ChallengeParticipant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    val challenge: Challenge,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    var totalScore: Int = 0,
    var gamesCompleted: Int = 0,

    val joinedAt: LocalDateTime = LocalDateTime.now(),
)
