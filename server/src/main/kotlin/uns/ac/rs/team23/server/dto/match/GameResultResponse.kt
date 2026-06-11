package uns.ac.rs.team23.server.dto.match

import uns.ac.rs.team23.server.model.MatchGameResult

data class GameResultResponse(
    val gameType: String,
    val gameIndex: Int,
    val player1Score: Int,
    val player2Score: Int,
    val player1Completed: Boolean,
    val player2Completed: Boolean,
) {
    companion object {
        fun from(r: MatchGameResult) = GameResultResponse(
            gameType = r.gameType.name,
            gameIndex = r.gameIndex,
            player1Score = r.player1Score,
            player2Score = r.player2Score,
            player1Completed = r.player1Completed,
            player2Completed = r.player2Completed,
        )
    }
}
