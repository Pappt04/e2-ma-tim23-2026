package uns.ac.rs.team23.slagalica.data

/**
 * Holds the active tournament a player is in while they play the bracket's matches.
 *
 * A tournament semifinal/final reuses the normal [MatchStore]-driven game flow as a (friendly)
 * match. This object lets the Game route know that the finished match belongs to a tournament, so
 * it routes back to the Tournament screen (to advance the bracket / apply tournament rewards)
 * instead of the usual MatchResults screen. It also lets [viewmodels.TournamentViewModel] resume
 * an in-progress tournament after the VM is recreated on return from a match.
 *
 * Mirrors [ChallengeStore].
 */
object TournamentStore {
    var tournamentId: String = ""

    /** Round of the match currently being played: "SEMIFINAL" or "FINAL". */
    var currentRound: String = ""

    /** Id of the tournament match currently being played (semifinal or final). */
    var currentMatchId: String = ""

    val isActive: Boolean get() = tournamentId.isNotBlank()

    val isFinalRound: Boolean get() = currentRound == ROUND_FINAL

    fun set(tournamentId: String) {
        this.tournamentId = tournamentId
    }

    /** Record which match the player is about to enter (set right before navigating to the game). */
    fun setCurrentMatch(matchId: String, round: String) {
        currentMatchId = matchId
        currentRound = round
    }

    fun clear() {
        tournamentId = ""
        currentRound = ""
        currentMatchId = ""
    }

    const val ROUND_SEMIFINAL = "SEMIFINAL"
    const val ROUND_FINAL = "FINAL"
}
