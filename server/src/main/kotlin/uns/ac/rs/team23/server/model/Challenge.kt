package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

enum class ChallengeStatus { OPEN, COMPLETED }

@Entity
@Table(name = "challenges")
class Challenge(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    val creator: User,

    @Column(nullable = false)
    val region: String,

    val stakedStars: Int,
    val stakedTokens: Int,

    @Enumerated(EnumType.STRING)
    var status: ChallengeStatus = ChallengeStatus.OPEN,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)
