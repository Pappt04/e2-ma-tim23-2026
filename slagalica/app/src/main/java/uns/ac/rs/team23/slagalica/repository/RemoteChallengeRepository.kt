package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto
import uns.ac.rs.team23.slagalica.network.dto.CreateChallengeRequest
import uns.ac.rs.team23.slagalica.network.dto.SubmitChallengeScoreRequest
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteChallengeRepository(private val api: ApiService) : ChallengeRepository {

    override suspend fun getChallenges(region: String): Result<List<ChallengeResponseDto>> = runCatching {
        val response = api.getChallenges(region)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun createChallenge(
        region: String,
        stakedStars: Int,
        stakedTokens: Int,
    ): Result<ChallengeResponseDto> = runCatching {
        val response = api.createChallenge(CreateChallengeRequest(region, stakedStars, stakedTokens))
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun joinChallenge(challengeId: Long): Result<ChallengeResponseDto> = runCatching {
        val response = api.joinChallenge(challengeId)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun submitScore(
        challengeId: Long,
        gameType: String,
        score: Int,
    ): Result<ChallengeResponseDto> = runCatching {
        val response = api.submitChallengeScore(challengeId, SubmitChallengeScoreRequest(gameType, score))
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun getChallenge(challengeId: Long): Result<ChallengeResponseDto> = runCatching {
        val response = api.getChallenge(challengeId)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }
}
