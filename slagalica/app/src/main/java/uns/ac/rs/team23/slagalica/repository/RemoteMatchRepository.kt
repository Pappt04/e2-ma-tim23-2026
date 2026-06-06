package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.dto.GameResultRequest
import uns.ac.rs.team23.slagalica.network.dto.MatchInviteResponseDto
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto
import uns.ac.rs.team23.slagalica.network.dto.StartMatchRequest
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteMatchRepository(private val api: ApiService) : MatchRepository {

    override suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto> = runCatching {
        val response = api.startMatch(StartMatchRequest(type = "RANDOM", friendly = friendly))
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun sendFriendInvite(friendId: Long, friendly: Boolean): Result<MatchResponseDto> = runCatching {
        val response = api.startMatch(StartMatchRequest(type = "FRIEND", friendly = friendly, friendId = friendId))
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

    override suspend fun getPendingInvites(): Result<List<MatchInviteResponseDto>> = runCatching {
        val response = api.getPendingInvites()
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun respondToInvite(inviteId: Long, accept: Boolean): Result<MatchResponseDto> = runCatching {
        val response = api.respondToInvite(inviteId, accept)
        if (response.isSuccessful) response.body()!!
        else throw Exception(parseErrorMessage(response.errorBody()?.string()))
    }

    override suspend fun cancelInvite(inviteId: Long): Result<Unit> = runCatching {
        api.cancelInvite(inviteId)
    }
}
