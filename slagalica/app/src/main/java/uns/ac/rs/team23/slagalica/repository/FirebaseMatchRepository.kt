package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Transaction
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import uns.ac.rs.team23.slagalica.network.dto.GameResultDto
import uns.ac.rs.team23.slagalica.network.dto.GameStateDto
import uns.ac.rs.team23.slagalica.network.dto.MatchInviteResponseDto
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto

private val GAME_ORDER = listOf(
    "KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"
)

class FirebaseMatchRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : MatchRepository {

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun startRandomMatch(friendly: Boolean): Result<MatchResponseDto> =
        runCatching {
            val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username") ?: ""

            // Clean up any stale waiting match left from a previous crash/session
            cancelQueue()

            // Try to claim an existing waiting match (query only by status to avoid composite index)
            val joined = joinWaitingMatch(uid, username, friendly)
            if (joined != null) return@runCatching joined

            // No match to join — create a new waiting match
            val matchRef = firestore.collection("matches").document()
            matchRef.set(
                mapOf(
                    "player1Id" to uid,
                    "player1Username" to username,
                    "player2Id" to null,
                    "player2Username" to null,
                    "status" to "WAITING_FOR_OPPONENT",
                    "isFriendly" to friendly,
                    "currentGameIndex" to 0,
                    "currentGameType" to null,
                    "player1TotalScore" to 0,
                    "player2TotalScore" to 0,
                    "winnerId" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            MatchResponseDto(
                id = matchRef.id,
                player1Id = uid,
                player1Username = username,
                player2Id = null,
                player2Username = null,
                status = "WAITING_FOR_OPPONENT",
                isFriendly = friendly,
                currentGameIndex = 0,
                currentGameType = null,
                player1TotalScore = 0,
                player2TotalScore = 0,
                winnerId = null,
            )
        }

    override suspend fun tryJoinWaiting(username: String, friendly: Boolean): Result<MatchResponseDto?> =
        runCatching {
            val uid = auth.currentUser?.uid ?: return@runCatching null
            joinWaitingMatch(uid, username, friendly)
        }

    private suspend fun joinWaitingMatch(uid: String, username: String, friendly: Boolean): MatchResponseDto? {
        val waitingSnap = firestore.collection("matches")
            .whereEqualTo("status", "WAITING_FOR_OPPONENT")
            .limit(10)
            .get()
            .await()

        for (doc in waitingSnap.documents) {
            if (doc.getString("player1Id") == uid) continue
            if ((doc.getBoolean("isFriendly") ?: false) != friendly) continue
            try {
                var joined: MatchResponseDto? = null
                firestore.runTransaction { tx ->
                    val snap = tx.get(doc.reference)
                    if (snap.getString("status") != "WAITING_FOR_OPPONENT" ||
                        snap.getString("player2Id") != null
                    ) throw Exception("already taken")
                    tx.update(
                        doc.reference, mapOf(
                            "player2Id" to uid,
                            "player2Username" to username,
                            "status" to "IN_PROGRESS",
                            "currentGameType" to GAME_ORDER[0],
                        )
                    )
                    joined = MatchResponseDto(
                        id = doc.id,
                        player1Id = snap.getString("player1Id") ?: "",
                        player1Username = snap.getString("player1Username") ?: "",
                        player2Id = uid,
                        player2Username = username,
                        status = "IN_PROGRESS",
                        isFriendly = friendly,
                        currentGameIndex = 0,
                        currentGameType = GAME_ORDER[0],
                        player1TotalScore = 0,
                        player2TotalScore = 0,
                        winnerId = null,
                    )
                }.await()
                if (joined != null) return joined
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    override suspend fun getCurrentMatch(): Result<MatchResponseDto?> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")

        val snap1 = firestore.collection("matches")
            .whereEqualTo("player1Id", uid)
            .whereIn("status", listOf("WAITING_FOR_OPPONENT", "IN_PROGRESS"))
            .limit(1)
            .get()
            .await()
        if (!snap1.isEmpty) return@runCatching snap1.documents[0].toMatchResponseDto()

        val snap2 = firestore.collection("matches")
            .whereEqualTo("player2Id", uid)
            .whereEqualTo("status", "IN_PROGRESS")
            .limit(1)
            .get()
            .await()
        if (!snap2.isEmpty) return@runCatching snap2.documents[0].toMatchResponseDto()

        null
    }

    override suspend fun submitScore(matchId: String, score: Int): Result<MatchResponseDto> =
        runCatching {
            val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
            val matchRef = firestore.collection("matches").document(matchId)

            firestore.runTransaction { tx ->
                val match = tx.get(matchRef)
                val currentGameType = match.getString("currentGameType")
                    ?: throw Exception("No current game type")
                val currentGameIndex = (match.getLong("currentGameIndex") ?: 0L).toInt()
                val isPlayer1 = match.getString("player1Id") == uid

                val gameResultRef = matchRef.collection("gameResults").document(currentGameType)
                val gameResult = tx.get(gameResultRef)

                // Pre-read both players (reads before writes) for end-of-match star awards.
                val isFriendly = match.getBoolean("isFriendly") ?: false
                val p1Id = match.getString("player1Id")
                val p2Id = match.getString("player2Id")
                val usersCol = firestore.collection("users")
                val p1Ref = p1Id?.let { usersCol.document(it) }
                val p2Ref = p2Id?.let { usersCol.document(it) }
                val p1Snap = if (!isFriendly && p1Ref != null) tx.get(p1Ref) else null
                val p2Snap = if (!isFriendly && p2Ref != null) tx.get(p2Ref) else null

                val scoreField = if (isPlayer1) "player1Score" else "player2Score"
                val completedField = if (isPlayer1) "player1Completed" else "player2Completed"

                tx.set(
                    gameResultRef,
                    mapOf(
                        "gameType" to currentGameType,
                        "gameIndex" to currentGameIndex,
                        scoreField to score,
                        completedField to true,
                    ),
                    SetOptions.merge(),
                )

                val otherCompleted = if (isPlayer1)
                    gameResult.getBoolean("player2Completed") == true
                else
                    gameResult.getBoolean("player1Completed") == true

                if (otherCompleted) {
                    val existingP1 = (gameResult.getLong("player1Score") ?: 0L).toInt()
                    val existingP2 = (gameResult.getLong("player2Score") ?: 0L).toInt()
                    val p1Score = if (isPlayer1) score else existingP1
                    val p2Score = if (isPlayer1) existingP2 else score

                    val newP1Total = (match.getLong("player1TotalScore") ?: 0L).toInt() + p1Score
                    val newP2Total = (match.getLong("player2TotalScore") ?: 0L).toInt() + p2Score
                    val nextGameIndex = currentGameIndex + 1

                    if (nextGameIndex >= GAME_ORDER.size) {
                        val winnerId = when {
                            newP1Total > newP2Total -> match.getString("player1Id")
                            newP2Total > newP1Total -> match.getString("player2Id")
                            else -> null
                        }
                        tx.update(
                            matchRef, mapOf(
                                "player1TotalScore" to newP1Total,
                                "player2TotalScore" to newP2Total,
                                "status" to "COMPLETED",
                                "winnerId" to winnerId,
                                "completedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        if (p1Snap != null && p2Snap != null && p1Ref != null && p2Ref != null) {
                            awardStars(tx, p1Ref, p1Snap, p2Ref, p2Snap, newP1Total, newP2Total)
                        }
                    } else {
                        tx.update(
                            matchRef, mapOf(
                                "player1TotalScore" to newP1Total,
                                "player2TotalScore" to newP2Total,
                                "currentGameIndex" to nextGameIndex,
                                "currentGameType" to GAME_ORDER[nextGameIndex],
                            )
                        )
                    }
                }
            }.await()

            matchRef.get().await().toMatchResponseDto()
        }

    override suspend fun abandonMatch(matchId: String): Result<MatchResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val matchRef = firestore.collection("matches").document(matchId)
        val match = matchRef.get().await()
        val opponentId = if (match.getString("player1Id") == uid)
            match.getString("player2Id") else match.getString("player1Id")
        matchRef.update(
            mapOf(
                "status" to "ABANDONED",
                "winnerId" to opponentId,
                "completedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        matchRef.get().await().toMatchResponseDto()
    }

    override suspend fun cancelQueue(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val snap = firestore.collection("matches")
            .whereEqualTo("player1Id", uid)
            .whereEqualTo("status", "WAITING_FOR_OPPONENT")
            .limit(1)
            .get()
            .await()
        snap.documents.firstOrNull()?.reference?.delete()?.await()
    }

    override suspend fun sendFriendInvite(
        friendId: String,
        friendly: Boolean,
    ): Result<MatchResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val userDoc = firestore.collection("users").document(uid).get().await()
        val username = userDoc.getString("username") ?: ""
        val friendDoc = firestore.collection("users").document(friendId).get().await()
        val friendUsername = friendDoc.getString("username") ?: "Prijatelj"

        val inviteRef = firestore.collection("matchInvites").document()
        inviteRef.set(
            mapOf(
                "inviterId" to uid,
                "inviterUsername" to username,
                "inviteeId" to friendId,
                "inviteeUsername" to friendUsername,
                "isFriendly" to friendly,
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        MatchResponseDto(
            id = inviteRef.id,
            player1Id = uid,
            player1Username = username,
            player2Id = friendId,
            player2Username = friendUsername,
            status = "INVITE_SENT",
            isFriendly = friendly,
            currentGameIndex = 0,
            currentGameType = null,
            player1TotalScore = 0,
            player2TotalScore = 0,
            winnerId = null,
        )
    }

    override suspend fun getPendingInvites(): Result<List<MatchInviteResponseDto>> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val snapshot = firestore.collection("matchInvites")
            .whereEqualTo("inviteeId", uid)
            .whereEqualTo("status", "PENDING")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            MatchInviteResponseDto(
                id = doc.id,
                inviterUsername = doc.getString("inviterUsername") ?: return@mapNotNull null,
                isFriendly = doc.getBoolean("isFriendly") ?: false,
            )
        }
    }

    override suspend fun respondToInvite(
        inviteId: String,
        accept: Boolean,
    ): Result<MatchResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val inviteRef = firestore.collection("matchInvites").document(inviteId)
        val invite = inviteRef.get().await()

        val inviterId = invite.getString("inviterId") ?: throw Exception("Neispravan poziv")
        val inviterUsername = invite.getString("inviterUsername") ?: ""
        val inviteeUsername = invite.getString("inviteeUsername") ?: ""
        val isFriendly = invite.getBoolean("isFriendly") ?: false

        if (!accept) {
            inviteRef.update("status", "REJECTED").await()
            return@runCatching MatchResponseDto(
                id = "",
                player1Id = inviterId,
                player1Username = inviterUsername,
                player2Id = uid,
                player2Username = inviteeUsername,
                status = "REJECTED",
                isFriendly = isFriendly,
                currentGameIndex = 0,
                currentGameType = null,
                player1TotalScore = 0,
                player2TotalScore = 0,
                winnerId = null,
            )
        }

        val matchRef = firestore.collection("matches").document()
        matchRef.set(
            mapOf(
                "player1Id" to inviterId,
                "player1Username" to inviterUsername,
                "player2Id" to uid,
                "player2Username" to inviteeUsername,
                "status" to "IN_PROGRESS",
                "isFriendly" to isFriendly,
                "currentGameIndex" to 0,
                "currentGameType" to GAME_ORDER[0],
                "player1TotalScore" to 0,
                "player2TotalScore" to 0,
                "winnerId" to null,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        inviteRef.update(mapOf("status" to "ACCEPTED", "matchId" to matchRef.id)).await()

        MatchResponseDto(
            id = matchRef.id,
            player1Id = inviterId,
            player1Username = inviterUsername,
            player2Id = uid,
            player2Username = inviteeUsername,
            status = "IN_PROGRESS",
            isFriendly = isFriendly,
            currentGameIndex = 0,
            currentGameType = GAME_ORDER[0],
            player1TotalScore = 0,
            player2TotalScore = 0,
            winnerId = null,
        )
    }

    override suspend fun cancelInvite(inviteId: String): Result<Unit> = runCatching {
        firestore.collection("matchInvites").document(inviteId)
            .update("status", "CANCELLED")
            .await()
    }

    override suspend fun markReady(matchId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val matchRef = firestore.collection("matches").document(matchId)
        val match = matchRef.get().await()
        val field = if (match.getString("player1Id") == uid) "player1Ready" else "player2Ready"
        matchRef.update(field, true).await()
    }

    override suspend fun setInMatch(inMatch: Boolean): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        firestore.collection("users").document(uid).update("inMatch", inMatch).await()
    }

    // --- Real-time match & game synchronization ---

    override fun observeMatch(matchId: String): Flow<MatchResponseDto> = callbackFlow {
        val reg = firestore.collection("matches").document(matchId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap != null && snap.exists()) trySend(snap.toMatchResponseDto())
            }
        awaitClose { reg.remove() }
    }

    override fun observeGameResults(matchId: String): Flow<List<GameResultDto>> = callbackFlow {
        val reg = firestore.collection("matches").document(matchId)
            .collection("gameResults")
            .addSnapshotListener { snaps, err ->
                if (err != null) return@addSnapshotListener
                val results = snaps?.documents?.mapNotNull { doc ->
                    val type = doc.getString("gameType") ?: return@mapNotNull null
                    GameResultDto(
                        gameType = type,
                        gameIndex = (doc.getLong("gameIndex") ?: 0L).toInt(),
                        player1Score = (doc.getLong("player1Score") ?: 0L).toInt(),
                        player2Score = (doc.getLong("player2Score") ?: 0L).toInt(),
                    )
                }?.sortedBy { it.gameIndex } ?: emptyList()
                trySend(results)
            }
        awaitClose { reg.remove() }
    }

    override fun observeGameState(matchId: String, gameType: String): Flow<GameStateDto?> = callbackFlow {
        val reg = firestore.collection("matches").document(matchId)
            .collection("gameState").document(gameType)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                trySend(if (snap != null && snap.exists()) snap.toGameStateDto(gameType) else null)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun setGameState(
        matchId: String,
        gameType: String,
        data: Map<String, Any?>,
    ): Result<Unit> = runCatching {
        firestore.collection("matches").document(matchId)
            .collection("gameState").document(gameType)
            .set(data)
            .await()
    }

    override suspend fun patchGameState(
        matchId: String,
        gameType: String,
        fields: Map<String, Any?>,
    ): Result<Unit> = runCatching {
        firestore.collection("matches").document(matchId)
            .collection("gameState").document(gameType)
            .set(fields, SetOptions.merge())
            .await()
    }

    override suspend fun advanceMatch(
        matchId: String,
        gameType: String,
        p1GameScore: Int,
        p2GameScore: Int,
    ): Result<MatchResponseDto> = runCatching {
        val matchRef = firestore.collection("matches").document(matchId)

        firestore.runTransaction { tx ->
            val match = tx.get(matchRef)
            // Ignore if the match is already finished/abandoned.
            if (match.getString("status") != "IN_PROGRESS") return@runTransaction
            val gameIndex = GAME_ORDER.indexOf(gameType)
                .takeIf { it >= 0 } ?: (match.getLong("currentGameIndex") ?: 0L).toInt()

            // Pre-read both players (reads must precede writes) so we can award
            // stars when this game completes the match (ranked matches only).
            val isFriendly = match.getBoolean("isFriendly") ?: false
            val p1Id = match.getString("player1Id")
            val p2Id = match.getString("player2Id")
            val usersCol = firestore.collection("users")
            val p1Ref = p1Id?.let { usersCol.document(it) }
            val p2Ref = p2Id?.let { usersCol.document(it) }
            val p1Snap = if (!isFriendly && p1Ref != null) tx.get(p1Ref) else null
            val p2Snap = if (!isFriendly && p2Ref != null) tx.get(p2Ref) else null

            // Persist the per-game result for this specific game (idempotent).
            val gameResultRef = matchRef.collection("gameResults").document(gameType)
            tx.set(
                gameResultRef,
                mapOf(
                    "gameType" to gameType,
                    "gameIndex" to gameIndex,
                    "player1Score" to p1GameScore,
                    "player2Score" to p2GameScore,
                    "player1Completed" to true,
                    "player2Completed" to true,
                ),
                SetOptions.merge(),
            )

            val newP1Total = (match.getLong("player1TotalScore") ?: 0L).toInt() + p1GameScore
            val newP2Total = (match.getLong("player2TotalScore") ?: 0L).toInt() + p2GameScore
            val nextGameIndex = gameIndex + 1

            if (nextGameIndex >= GAME_ORDER.size) {
                val winnerId = when {
                    newP1Total > newP2Total -> match.getString("player1Id")
                    newP2Total > newP1Total -> match.getString("player2Id")
                    else -> null
                }
                tx.update(
                    matchRef, mapOf(
                        "player1TotalScore" to newP1Total,
                        "player2TotalScore" to newP2Total,
                        "status" to "COMPLETED",
                        "winnerId" to winnerId,
                        "completedAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (p1Snap != null && p2Snap != null && p1Ref != null && p2Ref != null) {
                    awardStars(tx, p1Ref, p1Snap, p2Ref, p2Snap, newP1Total, newP2Total)
                }
            } else {
                tx.update(
                    matchRef, mapOf(
                        "player1TotalScore" to newP1Total,
                        "player2TotalScore" to newP2Total,
                        "currentGameIndex" to nextGameIndex,
                        "currentGameType" to GAME_ORDER[nextGameIndex],
                    )
                )
            }
        }.await()

        matchRef.get().await().toMatchResponseDto()
    }

    /**
     * Award stars at the end of a ranked match (spec "Igranje partija"):
     * winner +10, loser -10, plus +1 per 40 points for both; total stars clamped
     * at 0. Earned (positive) stars also feed [cycleStars] (regional monthly
     * leaderboard) and totalStarsEarned, and the league level is recomputed.
     * User docs must already be read in the same transaction (reads-before-writes).
     */
    private fun awardStars(
        tx: Transaction,
        p1Ref: DocumentReference,
        p1Snap: DocumentSnapshot,
        p2Ref: DocumentReference,
        p2Snap: DocumentSnapshot,
        p1Total: Int,
        p2Total: Int,
    ) {
        val (p1Base, p2Base) = when {
            p1Total > p2Total -> 10 to -10
            p2Total > p1Total -> -10 to 10
            else -> 0 to 0
        }
        applyStarDelta(tx, p1Ref, p1Snap, p1Base + p1Total / 40)
        applyStarDelta(tx, p2Ref, p2Snap, p2Base + p2Total / 40)
    }

    private fun applyStarDelta(tx: Transaction, ref: DocumentReference, snap: DocumentSnapshot, delta: Int) {
        val old = (snap.getLong("stars") ?: 0L).toInt()
        val newStars = (old + delta).coerceAtLeast(0)
        val earned = delta.coerceAtLeast(0)
        val oldCycle = (snap.getLong("cycleStars") ?: 0L).toInt()
        val oldTotal = (snap.getLong("totalStarsEarned") ?: 0L).toInt()
        tx.update(
            ref,
            mapOf(
                "stars" to newStars,
                "cycleStars" to oldCycle + earned,
                "totalStarsEarned" to oldTotal + earned,
                "leagueLevel" to leagueLevelForStars(newStars),
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.toGameStateDto(gameType: String) =
        GameStateDto(
            gameType = getString("gameType") ?: gameType,
            hostId = getString("hostId") ?: "",
            phase = getString("phase") ?: "",
            round = (getLong("round") ?: 1L).toInt(),
            index = (getLong("index") ?: 0L).toInt(),
            turn = getString("turn"),
            deadlineAt = getLong("deadlineAt") ?: 0L,
            payload = (get("payload") as? Map<String, Any?>) ?: emptyMap(),
            p1Input = (get("p1Input") as? Map<String, Any?>) ?: emptyMap(),
            p2Input = (get("p2Input") as? Map<String, Any?>) ?: emptyMap(),
            p1Score = (getLong("p1Score") ?: 0L).toInt(),
            p2Score = (getLong("p2Score") ?: 0L).toInt(),
        )

    private fun com.google.firebase.firestore.DocumentSnapshot.toMatchResponseDto() =
        MatchResponseDto(
            id = id,
            player1Id = getString("player1Id") ?: "",
            player1Username = getString("player1Username") ?: "",
            player2Id = getString("player2Id"),
            player2Username = getString("player2Username"),
            status = getString("status") ?: "WAITING_FOR_OPPONENT",
            isFriendly = getBoolean("isFriendly") ?: false,
            currentGameIndex = (getLong("currentGameIndex") ?: 0L).toInt(),
            currentGameType = getString("currentGameType"),
            player1TotalScore = (getLong("player1TotalScore") ?: 0L).toInt(),
            player2TotalScore = (getLong("player2TotalScore") ?: 0L).toInt(),
            winnerId = getString("winnerId"),
            player1Ready = getBoolean("player1Ready") ?: false,
            player2Ready = getBoolean("player2Ready") ?: false,
        )
}
