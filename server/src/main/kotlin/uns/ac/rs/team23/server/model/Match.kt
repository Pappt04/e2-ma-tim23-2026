package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

enum class MatchStatus { WAITING_FOR_OPPONENT, IN_PROGRESS, COMPLETED, ABANDONED }

@Entity
@Table(name = "matches")
class Match(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id", nullable = false)
    val player1: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    var player2: User? = null,

    @Enumerated(EnumType.STRING)
    var status: MatchStatus = MatchStatus.WAITING_FOR_OPPONENT,

    val isFriendly: Boolean = false,

    var player1TotalScore: Int = 0,
    var player2TotalScore: Int = 0,

    // Index into GameType.MATCH_ORDER (0-5)
    var currentGameIndex: Int = 0,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    var winner: User? = null,
)
