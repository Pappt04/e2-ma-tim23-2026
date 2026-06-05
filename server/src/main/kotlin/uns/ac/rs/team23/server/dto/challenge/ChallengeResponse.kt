package uns.ac.rs.team23.server.dto.challenge

import uns.ac.rs.team23.server.model.Challenge
import uns.ac.rs.team23.server.model.ChallengeGameResult
import uns.ac.rs.team23.server.model.ChallengeParticipant

data class ParticipantResponse(
    val userId: Long,
    val username: String,
    val totalScore: Int,
    val gamesCompleted: Int,
    val gameScores: Map<String, Int>,
)

data class ChallengeResponse(
    val id: Long,
    val creatorId: Long,
    val creatorUsername: String,
    val region: String,
    val stakedStars: Int,
    val stakedTokens: Int,
    val status: String,
    val participantCount: Int,
    val participants: List<ParticipantResponse>,
    val createdAt: String,
    val completedAt: String?,
) {
    companion object {
        fun from(
            challenge: Challenge,
            participants: List<ChallengeParticipant>,
            gameResults: Map<Long, List<ChallengeGameResult>>,
        ) = ChallengeResponse(
            id = challenge.id,
            creatorId = challenge.creator.id,
            creatorUsername = challenge.creator.username,
            region = challenge.region,
            stakedStars = challenge.stakedStars,
            stakedTokens = challenge.stakedTokens,
            status = challenge.status.name,
            participantCount = participants.size,
            participants = participants.map { p ->
                val scores = gameResults[p.id]?.associate { it.gameType.name to it.score } ?: emptyMap()
                ParticipantResponse(
                    userId = p.user.id,
                    username = p.user.username,
                    totalScore = p.totalScore,
                    gamesCompleted = p.gamesCompleted,
                    gameScores = scores,
                )
            },
            createdAt = challenge.createdAt.toString(),
            completedAt = challenge.completedAt?.toString(),
        )
    }
}
