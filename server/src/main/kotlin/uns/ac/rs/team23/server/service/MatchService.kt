package uns.ac.rs.team23.server.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uns.ac.rs.team23.server.dto.match.GameResultResponse
import uns.ac.rs.team23.server.dto.match.MatchResponse
import uns.ac.rs.team23.server.model.*
import uns.ac.rs.team23.server.repository.*

private val LEAGUE_THRESHOLDS = listOf(0, 100, 200, 400, 800, 1600)

@Service
class MatchService(
    private val matchRepository: MatchRepository,
    private val gameResultRepository: MatchGameResultRepository,
    private val inviteRepository: MatchInviteRepository,
    private val userRepository: UserRepository,
    private val matchmaking: MatchmakingService,
    private val messaging: SimpMessagingTemplate,
) {

    // ─── Matchmaking ─────────────────────────────────────────────────────────

    @Transactional
    fun startRandomMatch(userId: Long, friendly: Boolean): MatchResponse {
        val player = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        if (!friendly) {
            require(player.tokens >= 1) { "Not enough tokens to start a match" }
            player.tokens -= 1
            userRepository.save(player)
        }

        val opponentId = matchmaking.findOpponent(userId)
        return if (opponentId != null) {
            matchmaking.dequeue(opponentId)
            val opponent = userRepository.findById(opponentId).orElseThrow()
            val match = matchRepository.save(
                Match(player1 = opponent, player2 = player, status = MatchStatus.IN_PROGRESS, isFriendly = friendly)
            )
            val response = buildResponse(match)
            messaging.convertAndSend("/topic/match/${match.id}", response)
            // Notify the waiting opponent they got matched
            messaging.convertAndSendToUser(opponentId.toString(), "/queue/matched", response)
            response
        } else {
            matchmaking.enqueue(userId)
            MatchResponse(
                id = -1, player1Id = userId, player1Username = player.username,
                player2Id = null, player2Username = null,
                status = "WAITING_FOR_OPPONENT", isFriendly = friendly,
                currentGameIndex = 0, currentGameType = GameType.MATCH_ORDER[0].name,
                player1TotalScore = 0, player2TotalScore = 0, winnerId = null, gameResults = emptyList(),
            )
        }
    }

    @Transactional
    fun sendInvite(inviterId: Long, inviteeId: Long, friendly: Boolean): MatchResponse {
        val inviter = userRepository.findById(inviterId).orElseThrow()
        val invitee = userRepository.findById(inviteeId).orElseThrow()

        val invite = inviteRepository.save(
            MatchInvite(inviter = inviter, invitee = invitee, isFriendly = friendly)
        )
        // Push notification to invitee
        messaging.convertAndSendToUser(
            inviteeId.toString(), "/queue/invites",
            mapOf("inviteId" to invite.id, "from" to inviter.username, "friendly" to friendly)
        )
        return MatchResponse(
            id = invite.id, player1Id = inviterId, player1Username = inviter.username,
            player2Id = inviteeId, player2Username = invitee.username,
            status = "INVITE_SENT", isFriendly = friendly,
            currentGameIndex = 0, currentGameType = GameType.MATCH_ORDER[0].name,
            player1TotalScore = 0, player2TotalScore = 0, winnerId = null, gameResults = emptyList(),
        )
    }

    @Transactional
    fun respondToInvite(inviteId: Long, userId: Long, accept: Boolean): MatchResponse {
        val invite = inviteRepository.findById(inviteId).orElseThrow { IllegalArgumentException("Invite not found") }
        require(invite.invitee.id == userId) { "Not your invite" }
        require(invite.status == InviteStatus.PENDING) { "Invite already resolved" }

        if (invite.isExpired() || !accept) {
            invite.status = if (invite.isExpired()) InviteStatus.EXPIRED else InviteStatus.REJECTED
            inviteRepository.save(invite)
            return MatchResponse(
                id = invite.id, player1Id = invite.inviter.id, player1Username = invite.inviter.username,
                player2Id = userId, player2Username = invite.invitee.username,
                status = invite.status.name, isFriendly = invite.isFriendly,
                currentGameIndex = 0, currentGameType = GameType.MATCH_ORDER[0].name,
                player1TotalScore = 0, player2TotalScore = 0, winnerId = null, gameResults = emptyList(),
            )
        }

        if (!invite.isFriendly) {
            val inviter = invite.inviter
            val invitee = invite.invitee
            require(inviter.tokens >= 1 && invitee.tokens >= 1) { "Both players need at least 1 token" }
            inviter.tokens -= 1
            invitee.tokens -= 1
            userRepository.save(inviter)
            userRepository.save(invitee)
        }

        invite.status = InviteStatus.ACCEPTED
        inviteRepository.save(invite)

        val match = matchRepository.save(
            Match(player1 = invite.inviter, player2 = invite.invitee, status = MatchStatus.IN_PROGRESS, isFriendly = invite.isFriendly)
        )
        val response = buildResponse(match)
        messaging.convertAndSend("/topic/match/${match.id}", response)
        messaging.convertAndSendToUser(invite.inviter.id.toString(), "/queue/matched", response)
        return response
    }

    // ─── Match gameplay ───────────────────────────────────────────────────────

    @Transactional
    fun submitGameResult(matchId: Long, userId: Long, score: Int): MatchResponse {
        val match = matchRepository.findById(matchId).orElseThrow { IllegalArgumentException("Match not found") }
        require(match.status == MatchStatus.IN_PROGRESS) { "Match is not in progress" }

        val gameIndex = match.currentGameIndex
        val gameType = GameType.MATCH_ORDER[gameIndex]

        val gameResult = gameResultRepository.findByMatchIdAndGameIndex(matchId, gameIndex)
            ?: MatchGameResult(match = match, gameType = gameType, gameIndex = gameIndex)

        val isPlayer1 = match.player1.id == userId
        require(isPlayer1 || match.player2?.id == userId) { "Not a participant" }

        if (isPlayer1) {
            gameResult.player1Score = score
            gameResult.player1Completed = true
        } else {
            gameResult.player2Score = score
            gameResult.player2Completed = true
        }
        gameResultRepository.save(gameResult)

        if (gameResult.player1Completed && gameResult.player2Completed) {
            gameResult.completedAt = java.time.LocalDateTime.now()
            gameResultRepository.save(gameResult)

            match.player1TotalScore += gameResult.player1Score
            match.player2TotalScore += gameResult.player2Score

            if (gameIndex == 5) {
                finalizeMatch(match)
            } else {
                match.currentGameIndex = gameIndex + 1
            }
        }

        matchRepository.save(match)
        val response = buildResponse(match)
        messaging.convertAndSend("/topic/match/${match.id}", response)
        return response
    }

    @Transactional
    fun abandonMatch(matchId: Long, userId: Long): MatchResponse {
        val match = matchRepository.findById(matchId).orElseThrow { IllegalArgumentException("Match not found") }
        val wasInProgress = match.status == MatchStatus.IN_PROGRESS
        require(wasInProgress || match.status == MatchStatus.WAITING_FOR_OPPONENT)

        match.status = MatchStatus.ABANDONED
        match.completedAt = java.time.LocalDateTime.now()

        // Opponent wins if the match was in progress and it's not a friendly
        if (!match.isFriendly && wasInProgress) {
            val opponent = if (match.player1.id == userId) match.player2 else match.player1
            if (opponent != null) {
                match.winner = opponent
            }
        }

        if (matchmaking.isWaiting(userId)) {
            matchmaking.dequeue(userId)
        }

        matchRepository.save(match)
        val response = buildResponse(match)
        messaging.convertAndSend("/topic/match/${match.id}", response)
        return response
    }

    fun getCurrentMatch(userId: Long): MatchResponse? {
        val active = matchRepository.findActiveByUserId(
            userId,
            listOf(MatchStatus.WAITING_FOR_OPPONENT, MatchStatus.IN_PROGRESS)
        )
        return active.firstOrNull()?.let { buildResponse(it) }
    }

    fun getMatch(matchId: Long): MatchResponse {
        val match = matchRepository.findById(matchId).orElseThrow { IllegalArgumentException("Match not found") }
        return buildResponse(match)
    }

    fun getPendingInvites(userId: Long) =
        inviteRepository.findByInviteeIdAndStatus(userId, InviteStatus.PENDING)
            .filter { !it.isExpired() }

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun finalizeMatch(match: Match) {
        match.status = MatchStatus.COMPLETED
        match.completedAt = java.time.LocalDateTime.now()

        if (!match.isFriendly) {
            val p1 = match.player1
            val p2 = match.player2!!

            if (match.player1TotalScore >= match.player2TotalScore) {
                match.winner = p1
                awardStars(winner = p1, loser = p2, match.player1TotalScore, match.player2TotalScore)
            } else {
                match.winner = p2
                awardStars(winner = p2, loser = p1, match.player2TotalScore, match.player1TotalScore)
            }
            userRepository.save(p1)
            userRepository.save(p2)
        }
    }

    private fun awardStars(winner: User, loser: User, winnerScore: Int, loserScore: Int) {
        val winnerGain = 10 + winnerScore / 40
        val loserGain = loserScore / 40

        addEarnedStars(winner, winnerGain)
        // Loser earns stars for performance but loses the base 10
        addEarnedStars(loser, loserGain)
        loser.stars = maxOf(0, loser.stars - 10)

        updateLeague(winner)
        updateLeague(loser)
    }

    private fun addEarnedStars(user: User, amount: Int) {
        if (amount <= 0) return
        val milestoneBefore = user.totalStarsEarned / 50
        user.totalStarsEarned += amount
        user.stars += amount
        val milestoneAfter = user.totalStarsEarned / 50
        user.tokens += (milestoneAfter - milestoneBefore)
    }

    private fun updateLeague(user: User) {
        user.leagueLevel = LEAGUE_THRESHOLDS.indexOfLast { user.stars >= it }
    }

    private fun buildResponse(match: Match): MatchResponse {
        val results = gameResultRepository.findByMatchIdOrderByGameIndex(match.id)
            .map { GameResultResponse.from(it) }
        return MatchResponse.from(match, results)
    }
}
