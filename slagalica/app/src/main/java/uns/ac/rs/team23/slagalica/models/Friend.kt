package uns.ac.rs.team23.slagalica.models

/** A friend entry hydrated with the friend's live profile data. */
data class Friend(
    val uid: String = "",
    val username: String = "",
    val avatarIndex: Int = 0,
    val region: String = "",
    val stars: Int = 0,
    val leagueLevel: Int = 0,
    /** Current monthly-cycle rank (1-based); 0 means unranked this cycle. */
    val monthlyRank: Int = 0,
    /** Epoch millis of last presence heartbeat (0 = unknown). */
    val onlineAt: Long = 0L,
    val inMatch: Boolean = false,
) {
    /** Online if a presence heartbeat landed within the last two minutes. */
    val isOnline: Boolean get() = onlineAt > 0L && System.currentTimeMillis() - onlineAt < 120_000L

    /** Can be invited to a match: online and not currently playing. */
    val isPlayable: Boolean get() = isOnline && !inMatch
}
