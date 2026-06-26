package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.data.MatchGameOrder
import uns.ac.rs.team23.slagalica.data.TournamentStore
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import uns.ac.rs.team23.slagalica.network.dto.TournamentDto
import uns.ac.rs.team23.slagalica.network.dto.TournamentPlayerDto

private const val TOURNAMENT_FEE = 3
private const val STALE_LOBBY_MS = 3 * 60 * 1000L
private val FIRST_GAME_TYPE = MatchGameOrder.firebaseTypes[0]

class FirebaseTournamentRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : TournamentRepository {

    private val tournaments get() = firestore.collection("tournaments")
    private val matches get() = firestore.collection("matches")
    private val users get() = firestore.collection("users")

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun matchSetup(matchId: String): TournamentMatchSetup? {
        if (matchId.isBlank()) return null
        val m = matches.document(matchId).get().await()
        if (!m.exists()) return null
        return TournamentMatchSetup(
            player1Id = m.getString("player1Id") ?: "",
            player1Name = m.getString("player1Username") ?: "",
            player2Id = m.getString("player2Id") ?: "",
            player2Name = m.getString("player2Username") ?: "",
            status = m.getString("status") ?: "IN_PROGRESS",
            winnerId = m.getString("winnerId"),
        )
    }

    // ── Matchmaking ───────────────────────────────────────────────────────────

    override suspend fun joinTournament(): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val userDoc = users.document(uid).get().await()
        val username = userDoc.getString("username") ?: ""
        val avatarIndex = (userDoc.getLong("avatarIndex") ?: 0L).toInt()
        val leagueLevel = (userDoc.getLong("leagueLevel") ?: 0L).toInt()

        // Try to join an existing open lobby (query by status to avoid composite indexes).
        joinWaitingTournament(uid, username, avatarIndex, leagueLevel)?.let { return@runCatching it }

        // None available — create a new lobby and pay the entry fee.
        val ref = tournaments.document()
        firestore.runTransaction { tx ->
            val u = tx.get(users.document(uid))
            val tokens = (u.getLong("tokens") ?: 0L).toInt()
            if (tokens < TOURNAMENT_FEE) throw Exception("Not enough tokens (need $TOURNAMENT_FEE) for a tournament")
            tx.update(users.document(uid), "tokens", tokens - TOURNAMENT_FEE)
            tx.set(
                ref,
                mapOf(
                    "status" to "WAITING",
                    "playerUids" to listOf(uid),
                    "players" to mapOf(uid to playerEntry(username, avatarIndex, leagueLevel)),
                    "readyUids" to emptyList<String>(),
                    "finalReadyUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        ref.id
    }

    private suspend fun joinWaitingTournament(
        uid: String,
        username: String,
        avatarIndex: Int,
        leagueLevel: Int,
    ): String? {
        val snap = tournaments.whereEqualTo("status", "WAITING").limit(10).get().await()
        // Ignore lobbies older than this — they're ghosts from crashed/force-quit sessions whose
        // players never finished, which would otherwise pair you against absent opponents.
        val staleCutoff = System.currentTimeMillis() - STALE_LOBBY_MS
        for (doc in snap.documents) {
            val uids = doc.stringList("playerUids")
            if (uid in uids || uids.size >= 4) continue
            val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
            if (createdAt in 1 until staleCutoff) continue
            try {
                var joinedId: String? = null
                firestore.runTransaction { tx ->
                    val t = tx.get(doc.reference)
                    val curUids = t.stringList("playerUids")
                    if (t.getString("status") != "WAITING" || curUids.size >= 4 || uid in curUids) {
                        throw Exception("lobby unavailable")
                    }
                    val u = tx.get(users.document(uid))
                    val tokens = (u.getLong("tokens") ?: 0L).toInt()
                    if (tokens < TOURNAMENT_FEE) throw Exception("Not enough tokens (need $TOURNAMENT_FEE) for a tournament")

                    val willBeFull = curUids.size + 1 >= 4
                    tx.update(users.document(uid), "tokens", tokens - TOURNAMENT_FEE)
                    // Per-field atomic updates: add only this player's entry, so simultaneous joins
                    // can't overwrite each other (the previous whole-map rewrite lost entries under
                    // contention, which showed up as blank opponent names in a semifinal).
                    tx.update(doc.reference, "playerUids", FieldValue.arrayUnion(uid))
                    tx.update(doc.reference, FieldPath.of("players", uid), playerEntry(username, avatarIndex, leagueLevel))
                    if (willBeFull) tx.update(doc.reference, "status", "READY_CHECK")
                    joinedId = doc.id
                }.await()
                if (joinedId != null) return joinedId
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    // ── Ready checks ────────────────────────────────────────────────────────────

    override suspend fun markReady(tournamentId: String): Result<Unit> =
        addToList(tournamentId, "readyUids")

    override suspend fun markFinalReady(tournamentId: String): Result<Unit> =
        addToList(tournamentId, "finalReadyUids")

    private suspend fun addToList(tournamentId: String, field: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        firestore.runTransaction { tx ->
            val t = tx.get(tournaments.document(tournamentId))
            val list = t.stringList(field)
            if (uid in list) return@runTransaction
            tx.update(tournaments.document(tournamentId), field, list + uid)
        }.await()
    }

    // ── Bracket creation ──────────────────────────────────────────────────────

    override suspend fun createBracketIfHost(tournamentId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val m1 = matches.document()
        val m2 = matches.document()
        firestore.runTransaction { tx ->
            val t = tx.get(tournaments.document(tournamentId))
            val playerUids = t.stringList("playerUids")
            val ready = t.stringList("readyUids")
            if (playerUids.firstOrNull() != uid) return@runTransaction              // host only
            if (t.getString("status") != "READY_CHECK") return@runTransaction
            if (!t.getString("semi1MatchId").isNullOrBlank()) return@runTransaction // already created
            if (playerUids.size < 4 || ready.size < 4) return@runTransaction

            // Read usernames fresh from the user docs (authoritative — avoids blank names from a
            // players map that lost entries under join contention). Reads precede writes.
            val names = playerUids.associateWith { tx.get(users.document(it)).getString("username") ?: "" }
            val s = playerUids.shuffled()
            tx.set(m1, tournamentMatchData(tournamentId, s[0], s[1], names, "SEMIFINAL"))
            tx.set(m2, tournamentMatchData(tournamentId, s[2], s[3], names, "SEMIFINAL"))
            tx.update(
                tournaments.document(tournamentId),
                mapOf(
                    "semi1MatchId" to m1.id,
                    "semi2MatchId" to m2.id,
                    "semi1Uids" to listOf(s[0], s[1]),
                    "semi2Uids" to listOf(s[2], s[3]),
                    "status" to "SEMIFINALS",
                ),
            )
        }.await()
    }

    override suspend fun createFinalIfCreator(tournamentId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val mf = matches.document()
        firestore.runTransaction { tx ->
            val t = tx.get(tournaments.document(tournamentId))
            val semi1Winner = t.getString("semi1Winner")
            val semi2Winner = t.getString("semi2Winner")
            val finalReady = t.stringList("finalReadyUids")
            if (semi1Winner.isNullOrBlank() || semi2Winner.isNullOrBlank()) return@runTransaction
            if (uid != semi1Winner) return@runTransaction                            // deterministic creator
            if (!t.getString("finalMatchId").isNullOrBlank()) return@runTransaction  // already created
            if (finalReady.size < 2) return@runTransaction

            val names = listOf(semi1Winner, semi2Winner)
                .associateWith { tx.get(users.document(it)).getString("username") ?: "" }
            tx.set(mf, tournamentMatchData(tournamentId, semi1Winner, semi2Winner, names, "FINAL"))
            tx.update(
                tournaments.document(tournamentId),
                mapOf("finalMatchId" to mf.id, "status" to "FINAL"),
            )
        }.await()
    }

    // ── Match completion: record winner + reward ──────────────────────────────

    override suspend fun finishTournamentMatch(
        tournamentId: String,
        matchId: String,
        isFinal: Boolean,
    ): Result<TournamentMatchResult> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val matchSnap = matches.document(matchId).get().await()
        val p1 = matchSnap.getString("player1Id") ?: ""
        val p2 = matchSnap.getString("player2Id") ?: ""
        val winnerId = matchSnap.getString("winnerId")
        val effectiveWinner = winnerId?.takeIf { it.isNotBlank() } ?: p1   // draw → player1 advances
        val iAmP1 = p1 == uid
        val myTotal = ((if (iAmP1) matchSnap.getLong("player1TotalScore") else matchSnap.getLong("player2TotalScore")) ?: 0L).toInt()
        val iWon = effectiveWinner == uid

        recordWinner(tournamentId, matchId, effectiveWinner)

        val starsDelta = starDeltaFor(iWon, isFinal, myTotal)
        val tournamentTokens = tokenRewardFor(iWon, isFinal)
        applyRewardIdempotent(matchId, uid, starsDelta, tournamentTokens)

        TournamentMatchResult(iWon, isFinal, tournamentTokens, starsDelta)
    }

    /** Write the match winner into the bracket; mark the tournament COMPLETED after the final. */
    private suspend fun recordWinner(tournamentId: String, matchId: String, winnerUid: String) {
        firestore.runTransaction { tx ->
            val ref = tournaments.document(tournamentId)
            val t = tx.get(ref)
            val field = when (matchId) {
                t.getString("semi1MatchId") -> "semi1Winner"
                t.getString("semi2MatchId") -> "semi2Winner"
                t.getString("finalMatchId") -> "finalWinner"
                else -> return@runTransaction
            }
            if (t.getString(field).isNullOrBlank()) tx.update(ref, field, winnerUid)
            if (field == "finalWinner") tx.update(ref, "status", "COMPLETED")
        }.await()
    }

    private suspend fun applyRewardIdempotent(
        matchId: String,
        uid: String,
        starsDelta: Int,
        tournamentTokens: Int,
    ) {
        firestore.runTransaction { tx ->
            val matchRef = matches.document(matchId)
            val match = tx.get(matchRef)
            @Suppress("UNCHECKED_CAST")
            val rewarded = (match.get("tournamentRewarded") as? Map<String, Any?>)?.get(uid) == true
            val userRef = users.document(uid)
            val user = tx.get(userRef)
            if (rewarded) return@runTransaction

            val oldStars = (user.getLong("stars") ?: 0L).toInt()
            val newStars = (oldStars + starsDelta).coerceAtLeast(0)
            val earned = starsDelta.coerceAtLeast(0)
            val oldCycle = (user.getLong("cycleStars") ?: 0L).toInt()
            val oldWeekly = (user.getLong("weeklyCycleStars") ?: 0L).toInt()
            val oldTotal = (user.getLong("totalStarsEarned") ?: 0L).toInt()
            val newTotal = oldTotal + earned
            val tokenBonus = newTotal / 50 - oldTotal / 50   // regular +1 token per 50 earned stars
            val oldTokens = (user.getLong("tokens") ?: 0L).toInt()

            tx.update(
                userRef,
                mapOf(
                    "stars" to newStars,
                    "cycleStars" to oldCycle + earned,
                    "weeklyCycleStars" to oldWeekly + earned,
                    "totalStarsEarned" to newTotal,
                    "tokens" to oldTokens + tokenBonus + tournamentTokens,
                    "leagueLevel" to leagueLevelForStars(newStars),
                ),
            )
            tx.update(matchRef, FieldPath.of("tournamentRewarded", uid), true)
        }.await()
    }

    /** Regular match star rules per outcome; semifinal loss intentionally awards nothing. */
    private fun starDeltaFor(iWon: Boolean, isFinal: Boolean, myTotal: Int): Int = when {
        !isFinal && iWon -> 10 + myTotal / 40          // semifinal win: regular win stars
        !isFinal -> 0                                  // semifinal loss: nothing (spec 10.d)
        iWon -> 10 + myTotal / 40 + 10                 // final win: regular win + 10 bonus (spec 10.e)
        else -> -10 + myTotal / 40                     // final loss: regular loss stars
    }

    private fun tokenRewardFor(iWon: Boolean, isFinal: Boolean): Int = when {
        iWon && !isFinal -> 2
        iWon && isFinal -> 3
        else -> 0
    }

    // ── Cancel / refund ───────────────────────────────────────────────────────

    override suspend fun cancel(tournamentId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        firestore.runTransaction { tx ->
            val ref = tournaments.document(tournamentId)
            val t = tx.get(ref)
            val status = t.getString("status")
            if (status != "WAITING" && status != "READY_CHECK") return@runTransaction   // already started
            val playerUids = t.stringList("playerUids")
            if (uid !in playerUids) return@runTransaction

            val user = tx.get(users.document(uid))
            val tokens = (user.getLong("tokens") ?: 0L).toInt()

            val newUids = playerUids - uid
            @Suppress("UNCHECKED_CAST")
            val players = (t.get("players") as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
            players.remove(uid)
            val ready = t.stringList("readyUids") - uid
            val newStatus = if (newUids.isEmpty()) "CANCELLED" else "WAITING"

            tx.update(users.document(uid), "tokens", tokens + TOURNAMENT_FEE)   // refund
            tx.update(
                ref,
                mapOf(
                    "playerUids" to newUids,
                    "players" to players,
                    "readyUids" to ready,
                    "status" to newStatus,
                ),
            )
        }.await()
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    override fun observeTournament(tournamentId: String): Flow<TournamentDto> = callbackFlow {
        val reg = tournaments.document(tournamentId).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            if (snap != null && snap.exists()) trySend(snap.toTournamentDto())
        }
        awaitClose { reg.remove() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun playerEntry(username: String, avatarIndex: Int, leagueLevel: Int): Map<String, Any?> =
        mapOf("username" to username, "avatarIndex" to avatarIndex.toLong(), "leagueLevel" to leagueLevel.toLong())

    private fun tournamentMatchData(
        tournamentId: String,
        p1Uid: String,
        p2Uid: String,
        names: Map<String, String>,
        round: String,
    ): Map<String, Any?> {
        return mapOf(
            "player1Id" to p1Uid,
            "player1Username" to (names[p1Uid] ?: ""),
            "player2Id" to p2Uid,
            "player2Username" to (names[p2Uid] ?: ""),
            "status" to "IN_PROGRESS",
            "isFriendly" to true,                 // suppress the regular auto token/star side-effects
            "currentGameIndex" to 0,
            "currentGameType" to FIRST_GAME_TYPE,
            "player1TotalScore" to 0,
            "player2TotalScore" to 0,
            "winnerId" to null,
            "tournamentId" to tournamentId,
            "tournamentRound" to round,
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }

    private fun DocumentSnapshot.stringList(field: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (get(field) as? List<String>) ?: emptyList()
    }

    private fun DocumentSnapshot.toTournamentDto(): TournamentDto {
        @Suppress("UNCHECKED_CAST")
        val playersRaw = (get("players") as? Map<String, Any?>) ?: emptyMap()
        val players = playersRaw.mapValues { (uid, raw) ->
            @Suppress("UNCHECKED_CAST")
            val m = (raw as? Map<String, Any?>) ?: emptyMap()
            TournamentPlayerDto(
                uid = uid,
                username = m["username"] as? String ?: "",
                avatarIndex = (m["avatarIndex"] as? Long ?: 0L).toInt(),
                leagueLevel = (m["leagueLevel"] as? Long ?: 0L).toInt(),
            )
        }
        return TournamentDto(
            id = id,
            status = getString("status") ?: "WAITING",
            playerUids = stringList("playerUids"),
            players = players,
            readyUids = stringList("readyUids"),
            semi1MatchId = getString("semi1MatchId"),
            semi2MatchId = getString("semi2MatchId"),
            semi1Uids = stringList("semi1Uids"),
            semi2Uids = stringList("semi2Uids"),
            semi1Winner = getString("semi1Winner"),
            semi2Winner = getString("semi2Winner"),
            finalMatchId = getString("finalMatchId"),
            finalReadyUids = stringList("finalReadyUids"),
            finalWinner = getString("finalWinner"),
        )
    }
}
