package uns.ac.rs.team23.server.model

import jakarta.persistence.*

@Entity
@Table(name = "challenge_game_results")
class ChallengeGameResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    val participant: ChallengeParticipant,

    @Enumerated(EnumType.STRING)
    val gameType: GameType,

    var score: Int = 0,
)
