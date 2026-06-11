package uns.ac.rs.team23.server.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uns.ac.rs.team23.server.dto.challenge.ChallengeResponse
import uns.ac.rs.team23.server.model.*
import uns.ac.rs.team23.server.repository.*

private val LEAGUE_THRESHOLDS = listOf(0, 100, 200, 400, 800, 1600)

@Service
class ChallengeService(
    private val challengeRepository: ChallengeRepository,
    private val participantRepository: ChallengeParticipantRepository,
    private val gameResultRepository: ChallengeGameResultRepository,
    private val userRepository: UserRepository,
    private val messaging: SimpMessagingTemplate,
    private val ntfy: NtfyService,
) {

    @Transactional
    fun createChallenge(userId: Long, region: String, stakedStars: Int, stakedTokens: Int): ChallengeResponse {
        require(stakedStars in 0..10) { "Max 10 stars can be staked" }
        require(stakedTokens in 0..2) { "Max 2 tokens can be staked" }

        val creator = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        require(creator.stars >= stakedStars) { "Not enough stars" }
        require(creator.tokens >= stakedTokens) { "Not enough tokens" }

        creator.stars -= stakedStars
        creator.tokens -= stakedTokens
        userRepository.save(creator)

        val challenge = challengeRepository.save(
            Challenge(creator = creator, region = region, stakedStars = stakedStars, stakedTokens = stakedTokens)
        )
        participantRepository.save(ChallengeParticipant(challenge = challenge, user = creator))

        val response = buildResponse(challenge)
        messaging.convertAndSend("/topic/challenges/$region", response)
        return response
    }

    @Transactional
    fun joinChallenge(userId: Long, challengeId: Long): ChallengeResponse {
        val challenge = challengeRepository.findById(challengeId).orElseThrow { IllegalArgumentException("Challenge not found") }
        require(challenge.status == ChallengeStatus.OPEN) { "Challenge is not open" }

        val count = participantRepository.countByChallengeId(challengeId)
        require(count < 4) { "Challenge is full (max 4 participants)" }
        require(!participantRepository.existsByChallengeIdAndUserId(challengeId, userId)) { "Already joined" }

        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        require(user.stars >= challenge.stakedStars) { "Not enough stars" }
        require(user.tokens >= challenge.stakedTokens) { "Not enough tokens" }

        user.stars -= challenge.stakedStars
        user.tokens -= challenge.stakedTokens
        userRepository.save(user)

        participantRepository.save(ChallengeParticipant(challenge = challenge, user = user))

        val response = buildResponse(challenge)
        messaging.convertAndSend("/topic/challenges/${challenge.region}", response)
        ntfy.notify(challenge.creator.id, "Someone joined your challenge!", "${user.username} joined your challenge", tags = "tada")
        return response
    }

    @Transactional
    fun submitGameScore(userId: Long, challengeId: Long, gameTypeName: String, score: Int): ChallengeResponse {
        val gameType = GameType.valueOf(gameTypeName)
        val challenge = challengeRepository.findById(challengeId).orElseThrow { IllegalArgumentException("Challenge not found") }
        val participant = participantRepository.findByChallengeIdAndUserId(challengeId, userId)
            ?: throw IllegalArgumentException("Not a participant")

        require(!gameResultRepository.existsByParticipantIdAndGameType(participant.id, gameType)) {
            "Already submitted score for $gameTypeName"
        }

        gameResultRepository.save(ChallengeGameResult(participant = participant, gameType = gameType, score = score))
        participant.totalScore += score
        participant.gamesCompleted += 1
        participantRepository.save(participant)

        // Check if all participants have completed all 6 games
        val allParticipants = participantRepository.findByChallengeId(challengeId)
        if (allParticipants.all { it.gamesCompleted == 6 }) {
            finalizeChallenge(challenge, allParticipants)
        }

        val response = buildResponse(challenge)
        messaging.convertAndSend("/topic/challenges/${challenge.region}", response)
        return response
    }

    fun getChallengesInRegion(region: String): List<ChallengeResponse> =
        challengeRepository.findByRegionOrderByCreatedAtDesc(region).map { buildResponse(it) }

    fun getChallenge(challengeId: Long): ChallengeResponse =
        buildResponse(challengeRepository.findById(challengeId).orElseThrow { IllegalArgumentException("Challenge not found") })

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun finalizeChallenge(challenge: Challenge, participants: List<ChallengeParticipant>) {
        val ranked = participants.sortedByDescending { it.totalScore }
        val totalStaked = challenge.stakedStars * participants.size
        val totalTokensStaked = challenge.stakedTokens * participants.size

        // 1st place: 75% of total staked
        ranked.getOrNull(0)?.user?.let { winner ->
            val starsReward = (totalStaked * 0.75).toInt()
            val tokensReward = (totalTokensStaked * 0.75).toInt()
            winner.stars += starsReward
            winner.tokens += tokensReward
            winner.totalStarsEarned += starsReward
            updateLeague(winner)
            userRepository.save(winner)
        }

        // 2nd place: gets their stake back
        ranked.getOrNull(1)?.user?.let { second ->
            second.stars += challenge.stakedStars
            second.tokens += challenge.stakedTokens
            userRepository.save(second)
        }

        // Others: stake already deducted on join, nothing more

        challenge.status = ChallengeStatus.COMPLETED
        challenge.completedAt = java.time.LocalDateTime.now()
        challengeRepository.save(challenge)
    }

    private fun updateLeague(user: User) {
        user.leagueLevel = LEAGUE_THRESHOLDS.indexOfLast { user.stars >= it }
    }

    private fun buildResponse(challenge: Challenge): ChallengeResponse {
        val participants = participantRepository.findByChallengeId(challenge.id)
        val gameResults = participants.associate { p ->
            p.id to gameResultRepository.findByParticipantId(p.id)
        }
        return ChallengeResponse.from(challenge, participants, gameResults)
    }
}
