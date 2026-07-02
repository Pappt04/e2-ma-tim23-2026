package uns.ac.rs.team23.slagalica.repository

import kotlinx.coroutines.flow.Flow
import uns.ac.rs.team23.slagalica.network.dto.GameResultDto
import uns.ac.rs.team23.slagalica.network.dto.GameStateDto
import uns.ac.rs.team23.slagalica.network.dto.MatchInviteResponseDto
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto

interface MatchRepository {
    /** Uid of the currently authenticated user, or null if signed out. */
    fun currentUserId(): String?

    suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto>

    /**
     * Create a friendly, single-player match the caller hosts (no opponent ever joins). Used by
     * challenge attempts: the games resolve each round on their deadlines, so the host plays all
     * 6 games solo with zero star/token/stats side-effects (friendly matches skip those).
     */
    suspend fun startSoloMatch(): Result<MatchResponseDto>
    suspend fun tryJoinWaiting(username: String, friendly: Boolean): Result<MatchResponseDto?>
    suspend fun sendFriendInvite(friendId: String, friendly: Boolean): Result<MatchResponseDto>
    suspend fun getCurrentMatch(): Result<MatchResponseDto?>
    suspend fun submitScore(matchId: String, score: Int): Result<MatchResponseDto>
    suspend fun abandonMatch(matchId: String): Result<MatchResponseDto>
    /** Leave the ready lobby before any game starts — refunds ranked entry fees for both players. */
    suspend fun leavePreGameLobby(matchId: String): Result<Unit>
    suspend fun cancelQueue(): Result<Unit>
    suspend fun getPendingInvites(): Result<List<MatchInviteResponseDto>>
    suspend fun respondToInvite(inviteId: String, accept: Boolean): Result<MatchResponseDto>
    suspend fun cancelInvite(inviteId: String): Result<Unit>

    /** Mark the current user as ready in the match lobby. */
    suspend fun markReady(matchId: String): Result<Unit>

    /** Flag whether the current user is currently in a match (used by friends' "Play" gating). */
    suspend fun setInMatch(inMatch: Boolean): Result<Unit>

    // --- Real-time match & game synchronization ---

    /** Live updates of the match document (status, scores, current game). */
    fun observeMatch(matchId: String): Flow<MatchResponseDto>

    /** Per-game score breakdown written by [advanceMatch]. */
    fun observeGameResults(matchId: String): Flow<List<GameResultDto>>

    /** Live updates of the shared live-state for one game, or null until the host creates it. */
    fun observeGameState(matchId: String, gameType: String): Flow<GameStateDto?>

    /** Host only: create/overwrite the shared game-state document. */
    suspend fun setGameState(matchId: String, gameType: String, data: Map<String, Any?>): Result<Unit>

    /** Merge-update fields on the shared game-state document (host transitions or a player's own input). */
    suspend fun patchGameState(matchId: String, gameType: String, fields: Map<String, Any?>): Result<Unit>

    /**
     * Host only: record both players' final scores for [gameType] and advance the match to the next
     * game, or to COMPLETED with a winner if [gameType] is the last game. Authoritative on
     * [gameType] (not the doc's current index), so it stays correct alongside the not-yet-converted
     * games. Replaces the dual-submit [submitScore] path for converted games.
     */
    suspend fun advanceMatch(
        matchId: String,
        gameType: String,
        p1GameScore: Int,
        p2GameScore: Int,
    ): Result<MatchResponseDto>
}
