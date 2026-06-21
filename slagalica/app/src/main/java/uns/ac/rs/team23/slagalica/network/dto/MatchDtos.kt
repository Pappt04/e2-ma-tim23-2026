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
    val id: String,
    val player1Id: String,
    val player1Username: String,
    val player2Id: String?,
    val player2Username: String?,
    val status: String,
    val isFriendly: Boolean,
    val currentGameIndex: Int,
    val currentGameType: String?,
    val player1TotalScore: Int,
    val player2TotalScore: Int,
    val winnerId: String?,
    val player1Ready: Boolean = false,
    val player2Ready: Boolean = false,
    val abandonedById: String? = null,
)

data class GameResultDto(
    val gameType: String,
    val gameIndex: Int,
    val player1Score: Int,
    val player2Score: Int,
)

data class MatchInviteResponseDto(
    val id: String,
    val inviterUsername: String,
    val isFriendly: Boolean,
)

data class RespondInviteRequest(
    val accept: Boolean,
)
