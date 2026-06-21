package uns.ac.rs.team23.slagalica.data

/**
 * Holds the challenge a solo attempt belongs to while the user plays the 6 games.
 *
 * A challenge attempt reuses the normal [MatchStore]-driven game flow as a friendly,
 * single-player match. This object lets the Game route know the finished match's scores
 * should be routed back into a challenge (and where to navigate afterwards) instead of the
 * usual MatchResults screen.
 */
object ChallengeStore {
    var activeChallengeId: String = ""
    var region: String = ""

    val isActive: Boolean get() = activeChallengeId.isNotBlank()

    fun set(challengeId: String, region: String) {
        activeChallengeId = challengeId
        this.region = region
    }

    fun clear() {
        activeChallengeId = ""
        region = ""
    }
}
