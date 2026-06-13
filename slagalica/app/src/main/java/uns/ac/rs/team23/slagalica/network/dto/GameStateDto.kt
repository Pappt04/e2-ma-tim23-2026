package uns.ac.rs.team23.slagalica.network.dto

/**
 * Live, shared state for the currently active game in a match, mirrored from
 * `matches/{matchId}/gameState/{gameType}`.
 *
 * Authority model: the match host (player1) writes [payload], [phase], [round], [index],
 * [turn], [deadlineAt] and the score fields. Each player writes only their own input
 * ([p1Input] for player1/host, [p2Input] for player2/guest). The guest never advances
 * phases — it only reflects what the host has written, which keeps the two clients in sync.
 *
 * [payload] holds the shared content generated once by the host (e.g. the question set or
 * the Moj broj puzzle) so both players see identical content.
 *
 * [deadlineAt] is an absolute epoch-millis timestamp; both clients derive their countdown
 * as `deadlineAt - System.currentTimeMillis()` so the timer ticks together.
 */
data class GameStateDto(
    val gameType: String,
    val hostId: String,
    val phase: String,
    val round: Int,
    val index: Int,
    val turn: String?,
    val deadlineAt: Long,
    val payload: Map<String, Any?>,
    val p1Input: Map<String, Any?>,
    val p2Input: Map<String, Any?>,
    val p1Score: Int,
    val p2Score: Int,
)
