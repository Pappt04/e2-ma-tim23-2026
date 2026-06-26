package uns.ac.rs.team23.slagalica.models

/** The four daily missions (spec 12.a). [key] is the field name stored on the user doc. */
enum class DailyMissionType(val key: String) {
    WIN_MATCH("winMatch"),
    SEND_CHAT("sendChat"),
    FRIENDLY_MATCH("friendlyMatch"),
    WIN_TOURNAMENT("winTournament"),
}

/** A player's daily-mission completion for the current day. */
data class DailyMissionsState(
    val winMatch: Boolean = false,
    val sendChat: Boolean = false,
    val friendlyMatch: Boolean = false,
    val winTournament: Boolean = false,
    /** Whether the all-four bonus (+2 tokens, +3 stars) has been granted today. */
    val allBonusClaimed: Boolean = false,
) {
    fun isComplete(type: DailyMissionType): Boolean = when (type) {
        DailyMissionType.WIN_MATCH -> winMatch
        DailyMissionType.SEND_CHAT -> sendChat
        DailyMissionType.FRIENDLY_MATCH -> friendlyMatch
        DailyMissionType.WIN_TOURNAMENT -> winTournament
    }

    val completedCount: Int
        get() = listOf(winMatch, sendChat, friendlyMatch, winTournament).count { it }

    val allComplete: Boolean get() = completedCount == DailyMissionType.entries.size
}
