package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto

interface MatchRepository {
    suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto>
    suspend fun getCurrentMatch(): Result<MatchResponseDto?>
    suspend fun submitScore(matchId: Long, score: Int): Result<MatchResponseDto>
    suspend fun abandonMatch(matchId: Long): Result<MatchResponseDto>
    suspend fun cancelQueue(): Result<Unit>
}
