package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.dto.GameResultRequest
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto
import uns.ac.rs.team23.slagalica.network.dto.StartMatchRequest
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteMatchRepository(private val api: ApiService) : MatchRepository {

    override suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto> = runCatching {
        val response = api.startMatch(StartMatchRequest(type = "RANDOM", friendly = friendly))
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun getCurrentMatch(): Result<MatchResponseDto?> = runCatching {
        val response = api.getCurrentMatch()
        when {
            response.code() == 204 -> null
            response.isSuccessful -> response.body()
            else -> throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override suspend fun submitScore(matchId: Long, score: Int): Result<MatchResponseDto> = runCatching {
        val response = api.submitGameScore(matchId, GameResultRequest(score))
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun abandonMatch(matchId: Long): Result<MatchResponseDto> = runCatching {
        val response = api.abandonMatch(matchId)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun cancelQueue(): Result<Unit> = runCatching {
        api.cancelQueue()
    }
}
