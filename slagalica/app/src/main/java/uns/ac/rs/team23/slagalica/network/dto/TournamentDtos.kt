package uns.ac.rs.team23.slagalica.network.dto

/** One participant of a tournament, as stored under the tournament doc's `players` map. */
data class TournamentPlayerDto(
    val uid: String,
    val username: String,
    val avatarIndex: Int,
    val leagueLevel: Int,
)

/**
 * Live snapshot of a `tournaments/{id}` document.
 *
 * Statuses: WAITING (matchmaking, <4 players) → READY_CHECK (4 players, awaiting all ready) →
 * SEMIFINALS (two matches in progress) → FINAL (final match in progress) → COMPLETED. CANCELLED
 * when the lobby was abandoned before it started.
 */
data class TournamentDto(
    val id: String,
    val status: String,
    /** Join order; index 0 is the host that creates the bracket. */
    val playerUids: List<String>,
    val players: Map<String, TournamentPlayerDto>,
    val readyUids: List<String>,
    val semi1MatchId: String?,
    val semi2MatchId: String?,
    val semi1Uids: List<String>,
    val semi2Uids: List<String>,
    val semi1Winner: String?,
    val semi2Winner: String?,
    val finalMatchId: String?,
    val finalReadyUids: List<String>,
    val finalWinner: String?,
) {
    val hostUid: String? get() = playerUids.firstOrNull()

    fun player(uid: String): TournamentPlayerDto? = players[uid]

    /** The other player in this player's semifinal pairing, or null if not yet paired. */
    fun semifinalOpponent(uid: String): String? = when (uid) {
        in semi1Uids -> semi1Uids.firstOrNull { it != uid }
        in semi2Uids -> semi2Uids.firstOrNull { it != uid }
        else -> null
    }

    /** Match id of the semifinal this player belongs to, or null. */
    fun semifinalMatchId(uid: String): String? = when (uid) {
        in semi1Uids -> semi1MatchId
        in semi2Uids -> semi2MatchId
        else -> null
    }

    val bothSemifinalsDecided: Boolean
        get() = !semi1Winner.isNullOrBlank() && !semi2Winner.isNullOrBlank()

    val finalists: List<String>
        get() = listOfNotNull(semi1Winner?.takeIf { it.isNotBlank() }, semi2Winner?.takeIf { it.isNotBlank() })
}
