package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.DailyMissionType
import uns.ac.rs.team23.slagalica.models.DailyMissionsState

interface DailyMissionRepository {
    fun currentUserId(): String?

    /** Today's mission state (lazily reset when the stored day is not today). */
    suspend fun getMissions(): Result<DailyMissionsState>

    /**
     * Idempotently mark [type] complete for today and grant its reward (+3 stars; completing all
     * four also grants +2 tokens and +3 bonus stars). No-op if already completed today.
     */
    suspend fun completeMission(type: DailyMissionType): Result<Unit>

    /**
     * Credit match-based missions from a finished match: WIN_MATCH for a ranked win, FRIENDLY_MATCH
     * for finishing a friendly PvP match. Ignores tournament matches and solo challenge attempts.
     */
    suspend fun onMatchFinished(matchId: String): Result<Unit>
}
