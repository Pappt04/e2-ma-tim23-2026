package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.models.GameTypeStatistic
import uns.ac.rs.team23.slagalica.models.PlayerStatistics

private val FINISHED_STATUSES = setOf("COMPLETED", "ABANDONED")

/** Accumulator for one game type while scanning a player's matches. */
private class GameAccumulator {
    var gamesPlayed = 0
    var totalPoints = 0
    var bestPoints = 0

    fun add(score: Int) {
        gamesPlayed++
        totalPoints += score
        if (score > bestPoints) bestPoints = score
    }
}

class FirebaseStatisticsRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : StatisticsRepository {

    override suspend fun getPlayerStatistics(): Result<PlayerStatistics> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")

        // Query each side separately (single-field filters) to avoid composite indexes, then merge.
        val asP1 = firestore.collection("matches")
            .whereEqualTo("player1Id", uid)
            .get()
            .await()
            .documents
        val asP2 = firestore.collection("matches")
            .whereEqualTo("player2Id", uid)
            .get()
            .await()
            .documents

        val matches = (asP1 + asP2)
            .distinctBy { it.id }
            .filter { it.getString("status") in FINISHED_STATUSES }

        var wins = 0
        var losses = 0
        var draws = 0
        var totalPoints = 0
        var bestMatchScore = 0
        val perGame = linkedMapOf<String, GameAccumulator>()

        for (match in matches) {
            val isPlayer1 = match.getString("player1Id") == uid
            val myScore = scoreFor(match, isPlayer1)
            totalPoints += myScore
            if (myScore > bestMatchScore) bestMatchScore = myScore

            when (match.getString("winnerId")) {
                uid -> wins++
                null -> draws++
                else -> losses++
            }

            // Per-game breakdown lives in each match's gameResults subcollection.
            val results = match.reference.collection("gameResults").get().await()
            for (gr in results.documents) {
                val gameType = gr.getString("gameType") ?: continue
                val scoreField = if (isPlayer1) "player1Score" else "player2Score"
                val score = (gr.getLong(scoreField) ?: 0L).toInt()
                perGame.getOrPut(gameType) { GameAccumulator() }.add(score)
            }
        }

        val perGameStats = MatchGameOrder.firebaseTypes.mapIndexedNotNull { index, type ->
            val acc = perGame[type] ?: return@mapIndexedNotNull null
            GameTypeStatistic(
                gameType = type,
                displayName = MatchGameOrder.displayName(index),
                gamesPlayed = acc.gamesPlayed,
                totalPoints = acc.totalPoints,
                bestPoints = acc.bestPoints,
            )
        }

        PlayerStatistics(
            totalMatches = matches.size,
            wins = wins,
            losses = losses,
            draws = draws,
            totalPoints = totalPoints,
            bestMatchScore = bestMatchScore,
            perGame = perGameStats,
        )
    }

    private fun scoreFor(match: DocumentSnapshot, isPlayer1: Boolean): Int {
        val field = if (isPlayer1) "player1TotalScore" else "player2TotalScore"
        return (match.getLong(field) ?: 0L).toInt()
    }
}
