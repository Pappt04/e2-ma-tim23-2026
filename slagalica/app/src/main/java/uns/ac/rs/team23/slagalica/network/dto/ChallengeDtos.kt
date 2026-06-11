package uns.ac.rs.team23.slagalica.network.dto

data class CreateChallengeRequest(
    val region: String,
    val stakedStars: Int,
    val stakedTokens: Int,
)

data class SubmitChallengeScoreRequest(
    val gameType: String,
    val score: Int,
)

data class ChallengeParticipantDto(
    val id: String,
    val username: String,
    val totalScore: Int,
    val gamesCompleted: Int,
)

data class ChallengeResponseDto(
    val id: String,
    val creatorUsername: String,
    val region: String,
    val stakedStars: Int,
    val stakedTokens: Int,
    val status: String,
    val participants: List<ChallengeParticipantDto>,
)
