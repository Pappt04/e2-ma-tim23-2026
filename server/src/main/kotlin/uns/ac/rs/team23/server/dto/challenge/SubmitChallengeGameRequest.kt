package uns.ac.rs.team23.server.dto.challenge

data class SubmitChallengeGameRequest(
    val gameType: String,
    val score: Int,
)
