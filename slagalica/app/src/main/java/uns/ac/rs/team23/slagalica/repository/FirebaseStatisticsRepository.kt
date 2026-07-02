package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.models.GameDetailStats
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
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")

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
        var playedMatches = 0
        val perGame = linkedMapOf<String, GameAccumulator>()

        for (match in matches) {
            val results = match.reference.collection("gameResults").get().await()
            // Skip lobby cancellations / phantom matches that never recorded a round.
            if (results.isEmpty()) continue

            playedMatches++
            val isPlayer1 = match.getString("player1Id") == uid
            val myScore = scoreFor(match, isPlayer1)
            totalPoints += myScore
            if (myScore > bestMatchScore) bestMatchScore = myScore

            when (match.getString("winnerId")) {
                uid -> wins++
                null -> draws++
                else -> losses++
            }

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
            totalMatches = playedMatches,
            wins = wins,
            losses = losses,
            draws = draws,
            totalPoints = totalPoints,
            bestMatchScore = bestMatchScore,
            perGame = perGameStats,
            detail = readGameDetail(uid),
        )
    }

    /** Reads the per-game detail counters from the `users/{uid}/stats` subcollection. */
    private suspend fun readGameDetail(uid: String): GameDetailStats {
        val snap = firestore.collection("users").document(uid).collection("stats").get().await()
        val byType = snap.documents.associateBy { it.id }
        fun n(doc: DocumentSnapshot?, key: String) = (doc?.getLong(key) ?: 0L).toInt()
        val ko = byType["KO_ZNA_ZNA"]
        val mb = byType["MOJ_BROJ"]
        val kpk = byType["KORAK_PO_KORAK"]
        val aso = byType["ASOCIJACIJE"]
        val sko = byType["SKOCKO"]
        val spo = byType["SPOJNICE"]
        return GameDetailStats(
            koCorrect = n(ko, "correct"),
            koIncorrect = n(ko, "incorrect"),
            mbFound = n(mb, "found"),
            mbRounds = n(mb, "rounds"),
            korakSteps = (1..7).map { n(kpk, "step$it") },
            korakSolved = n(kpk, "solved"),
            asoSolved = n(aso, "solved"),
            asoUnsolved = n(aso, "unsolved"),
            skockoAttempts = (1..6).map { n(sko, "attempt$it") },
            spojniceConnected = n(spo, "connected"),
            spojniceTotal = n(spo, "total"),
        )
    }

    override suspend fun recordGameStats(
        gameType: String,
        increments: Map<String, Long>,
    ): Result<Unit> = runCatching {
        if (increments.isEmpty()) return@runCatching
        val uid = auth.currentUser?.uid ?: return@runCatching
        val data = increments.mapValues { FieldValue.increment(it.value) }
        firestore.collection("users").document(uid)
            .collection("stats").document(gameType)
            .set(data, SetOptions.merge())
            .await()
    }

    private fun scoreFor(match: DocumentSnapshot, isPlayer1: Boolean): Int {
        val field = if (isPlayer1) "player1TotalScore" else "player2TotalScore"
        return (match.getLong(field) ?: 0L).toInt()
    }
}
