package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto

interface ChallengeRepository {
    suspend fun getChallenges(region: String): Result<List<ChallengeResponseDto>>
    suspend fun createChallenge(region: String, stakedStars: Int, stakedTokens: Int): Result<ChallengeResponseDto>
    suspend fun joinChallenge(challengeId: String): Result<ChallengeResponseDto>
    suspend fun submitScore(challengeId: String, gameType: String, score: Int): Result<ChallengeResponseDto>
    suspend fun getChallenge(challengeId: String): Result<ChallengeResponseDto>
}
