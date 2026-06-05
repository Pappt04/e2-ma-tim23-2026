package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "match_game_results")
class MatchGameResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    val match: Match,

    @Enumerated(EnumType.STRING)
    val gameType: GameType,

    val gameIndex: Int,

    var player1Score: Int = 0,
    var player2Score: Int = 0,

    var player1Completed: Boolean = false,
    var player2Completed: Boolean = false,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
)
