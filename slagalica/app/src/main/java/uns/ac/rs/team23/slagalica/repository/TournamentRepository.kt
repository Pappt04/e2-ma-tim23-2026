package uns.ac.rs.team23.slagalica.repository

import kotlinx.coroutines.flow.Flow
import uns.ac.rs.team23.slagalica.network.dto.TournamentDto

/** Reward applied to the current player when one of their tournament matches finishes. */
data class TournamentMatchResult(
    val iWon: Boolean,
    val isFinal: Boolean,
    /** Tournament-specific tokens awarded (semifinal win +2, final win +3, otherwise 0). */
    val tokensAwarded: Int,
    /** Net star delta applied (can be negative on a final loss; semifinal loss is 0). */
    val starsAwarded: Int,
)

/** Authoritative player/host info for a tournament match, read from the match doc. */
data class TournamentMatchSetup(
    val player1Id: String,
    val player1Name: String,
    val player2Id: String,
    val player2Name: String,
    val status: String,
    val winnerId: String?,
) {
    val isCompleted: Boolean get() = status == "COMPLETED"
}

interface TournamentRepository {
    /** Uid of the currently authenticated user, or null if signed out. */
    fun currentUserId(): String?

    /** Read a match's player ids/names from the match doc (authoritative, same for both players). */
    suspend fun matchSetup(matchId: String): TournamentMatchSetup?

    /**
     * Join an open tournament lobby (or create one). Deducts the 3-token entry fee. Returns the
     * tournament id, which the caller stores in [data.TournamentStore] to drive the bracket.
     */
    suspend fun joinTournament(): Result<String>

    /** Live updates of the tournament document. */
    fun observeTournament(tournamentId: String): Flow<TournamentDto>

    /** Mark the current user ready in the initial 4-player check. */
    suspend fun markReady(tournamentId: String): Result<Unit>

    /** Mark the current user ready for the final (one of the two semifinal winners). */
    suspend fun markFinalReady(tournamentId: String): Result<Unit>

    /**
     * Host only (playerUids[0]): once all four are ready, randomly pair the players and create the
     * two semifinal matches. Idempotent — no-op if the bracket already exists or the caller is not
     * the host.
     */
    suspend fun createBracketIfHost(tournamentId: String): Result<Unit>

    /**
     * Final creator only (semi1Winner): once both finalists are ready, create the final match.
     * Idempotent.
     */
    suspend fun createFinalIfCreator(tournamentId: String): Result<Unit>

    /**
     * Called when one of the current player's tournament matches has completed. Records the match
     * winner into the tournament bracket and applies the player's reward (idempotently). Returns the
     * outcome for the result animation.
     */
    suspend fun finishTournamentMatch(
        tournamentId: String,
        matchId: String,
        isFinal: Boolean,
    ): Result<TournamentMatchResult>

    /**
     * Leave a tournament that has not started yet (WAITING / READY_CHECK) and refund the entry fee.
     * No-op once the bracket exists (spec: abandoning a started tournament forfeits the fee).
     */
    suspend fun cancel(tournamentId: String): Result<Unit>
}
