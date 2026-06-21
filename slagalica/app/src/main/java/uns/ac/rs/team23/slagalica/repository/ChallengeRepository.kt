package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto

interface ChallengeRepository {
    suspend fun getChallenges(region: String): Result<List<ChallengeResponseDto>>
    suspend fun createChallenge(region: String, stakedStars: Int, stakedTokens: Int): Result<ChallengeResponseDto>
    suspend fun joinChallenge(challengeId: String): Result<ChallengeResponseDto>
    suspend fun submitScore(challengeId: String, gameType: String, score: Int): Result<ChallengeResponseDto>
    suspend fun getChallenge(challengeId: String): Result<ChallengeResponseDto>

    /**
     * Copy the per-game scores from a finished solo challenge match (a friendly single-player
     * match) into the caller's participant entry, mark the attempt complete, and finalize the
     * challenge (payouts) if everyone has now played.
     */
    suspend fun submitChallengeAttempt(challengeId: String, matchId: String): Result<ChallengeResponseDto>
}
