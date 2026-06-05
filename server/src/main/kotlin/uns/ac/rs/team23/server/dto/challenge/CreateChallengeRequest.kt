package uns.ac.rs.team23.server.dto.challenge

data class CreateChallengeRequest(
    val region: String,
    val stakedStars: Int,
    val stakedTokens: Int,
)
