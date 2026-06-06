package uns.ac.rs.team23.slagalica.network.dto

data class StartMatchRequest(
    val type: String,
    val friendly: Boolean = false,
    val friendId: Long? = null,
)

data class GameResultRequest(
    val score: Int,
)

data class MatchResponseDto(
    val id: Long,
    val player1Id: Long,
    val player1Username: String,
    val player2Id: Long?,
    val player2Username: String?,
    val status: String,
    val isFriendly: Boolean,
    val currentGameIndex: Int,
    val currentGameType: String?,
    val player1TotalScore: Int,
    val player2TotalScore: Int,
    val winnerId: Long?,
)

data class MatchInviteResponseDto(
    val id: Long,
    val inviterUsername: String,
    val isFriendly: Boolean,
)

data class RespondInviteRequest(
    val accept: Boolean,
)
