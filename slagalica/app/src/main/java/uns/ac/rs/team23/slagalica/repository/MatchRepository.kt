package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.MatchInviteResponseDto
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto

interface MatchRepository {
    suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto>
    suspend fun sendFriendInvite(friendId: String, friendly: Boolean): Result<MatchResponseDto>
    suspend fun getCurrentMatch(): Result<MatchResponseDto?>
    suspend fun submitScore(matchId: String, score: Int): Result<MatchResponseDto>
    suspend fun abandonMatch(matchId: String): Result<MatchResponseDto>
    suspend fun cancelQueue(): Result<Unit>
    suspend fun getPendingInvites(): Result<List<MatchInviteResponseDto>>
    suspend fun respondToInvite(inviteId: String, accept: Boolean): Result<MatchResponseDto>
    suspend fun cancelInvite(inviteId: String): Result<Unit>
}
