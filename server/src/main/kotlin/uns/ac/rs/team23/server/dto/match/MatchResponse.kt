package uns.ac.rs.team23.server.dto.match

import uns.ac.rs.team23.server.model.Match

data class MatchResponse(
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
    val gameResults: List<GameResultResponse>,
) {
    companion object {
        fun from(match: Match, gameResults: List<GameResultResponse> = emptyList()) = MatchResponse(
            id = match.id,
            player1Id = match.player1.id,
            player1Username = match.player1.username,
            player2Id = match.player2?.id,
            player2Username = match.player2?.username,
            status = match.status.name,
            isFriendly = match.isFriendly,
            currentGameIndex = match.currentGameIndex,
            currentGameType = if (match.currentGameIndex < 6) uns.ac.rs.team23.server.model.GameType.MATCH_ORDER[match.currentGameIndex].name else null,
            player1TotalScore = match.player1TotalScore,
            player2TotalScore = match.player2TotalScore,
            winnerId = match.winner?.id,
            gameResults = gameResults,
        )
    }
}
