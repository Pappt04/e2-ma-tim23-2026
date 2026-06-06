package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto

interface ChallengeRepository {
    suspend fun getChallenges(region: String): Result<List<ChallengeResponseDto>>
    suspend fun createChallenge(region: String, stakedStars: Int, stakedTokens: Int): Result<ChallengeResponseDto>
    suspend fun joinChallenge(challengeId: Long): Result<ChallengeResponseDto>
    suspend fun submitScore(challengeId: Long, gameType: String, score: Int): Result<ChallengeResponseDto>
    suspend fun getChallenge(challengeId: Long): Result<ChallengeResponseDto>
}
