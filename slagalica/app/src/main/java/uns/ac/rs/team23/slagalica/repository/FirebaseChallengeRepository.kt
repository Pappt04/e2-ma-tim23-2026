package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import uns.ac.rs.team23.slagalica.network.dto.ChallengeParticipantDto
import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto

private val GAME_ORDER = listOf(
    "KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"
)

class FirebaseChallengeRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ChallengeRepository {

    override suspend fun getChallenges(region: String): Result<List<ChallengeResponseDto>> =
        runCatching {
            val snapshot = firestore.collection("challenges")
                .whereEqualTo("region", region)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toChallengeResponseDto() }
        }

    override suspend fun createChallenge(
        region: String,
        stakedStars: Int,
        stakedTokens: Int,
    ): Result<ChallengeResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val userRef = firestore.collection("users").document(uid)
        val challengeRef = firestore.collection("challenges").document()
        val participantRef = challengeRef.collection("participants").document(uid)

        firestore.runTransaction { tx ->
            val user = tx.get(userRef)
            val username = user.getString("username") ?: ""
            requireStake(user, stakedStars, stakedTokens)

            tx.set(
                challengeRef,
                mapOf(
                    "creatorId" to uid,
                    "creatorUsername" to username,
                    "region" to region,
                    "stakedStars" to stakedStars,
                    "stakedTokens" to stakedTokens,
                    "status" to "OPEN",
                    "participantIds" to listOf(uid),
                    "participantCount" to 1,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            tx.set(participantRef, newParticipant(uid, username))
            tx.update(userRef, deductStake(stakedStars, stakedTokens))
        }.await()

        getChallenge(challengeRef.id).getOrThrow()
    }

    override suspend fun joinChallenge(challengeId: String): Result<ChallengeResponseDto> =
        runCatching {
            val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val userRef = firestore.collection("users").document(uid)
            val challengeRef = firestore.collection("challenges").document(challengeId)
            val participantRef = challengeRef.collection("participants").document(uid)

            firestore.runTransaction { tx ->
                val challenge = tx.get(challengeRef)
                val user = tx.get(userRef)

                if (challenge.getString("status") != "OPEN") throw Exception("Challenge is not open")
                val count = (challenge.getLong("participantCount") ?: 0L).toInt()
                if (count >= 4) throw Exception("Challenge is full")
                @Suppress("UNCHECKED_CAST")
                val ids = challenge.get("participantIds") as? List<String> ?: emptyList()
                if (uid in ids) throw Exception("You are already in this challenge")

                val stakedStars = (challenge.getLong("stakedStars") ?: 0L).toInt()
                val stakedTokens = (challenge.getLong("stakedTokens") ?: 0L).toInt()
                requireStake(user, stakedStars, stakedTokens)

                val username = user.getString("username") ?: ""
                tx.update(
                    challengeRef,
                    mapOf(
                        "participantIds" to ids + uid,
                        "participantCount" to count + 1,
                    ),
                )
                tx.set(participantRef, newParticipant(uid, username))
                tx.update(userRef, deductStake(stakedStars, stakedTokens))
            }.await()

            getChallenge(challengeId).getOrThrow()
        }

    override suspend fun submitScore(
        challengeId: String,
        gameType: String,
        score: Int,
    ): Result<ChallengeResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")

        val gameResultRef = firestore.collection("challenges").document(challengeId)
            .collection("participants").document(uid)
            .collection("gameResults").document(gameType)

        if (!gameResultRef.get().await().exists()) {
            gameResultRef.set(mapOf("gameType" to gameType, "score" to score)).await()
            firestore.collection("challenges").document(challengeId)
                .collection("participants").document(uid)
                .update(
                    mapOf(
                        "gamesCompleted" to FieldValue.increment(1),
                        "totalScore" to FieldValue.increment(score.toLong()),
                    )
                ).await()
        }

        getChallenge(challengeId).getOrThrow()
    }

    override suspend fun submitChallengeAttempt(
        challengeId: String,
        matchId: String,
    ): Result<ChallengeResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")

        // Pull the solo match's per-game scores (player1 is the challenger).
        val resultsSnap = firestore.collection("matches").document(matchId)
            .collection("gameResults").get().await()
        val perGame = resultsSnap.documents.mapNotNull { doc ->
            val gameType = doc.getString("gameType") ?: doc.id
            val score = (doc.getLong("player1Score") ?: 0L).toInt()
            gameType to score
        }
        val total = perGame.sumOf { it.second }

        val participantRef = firestore.collection("challenges").document(challengeId)
            .collection("participants").document(uid)

        firestore.runTransaction { tx ->
            val p = tx.get(participantRef)
            // Idempotent: a finished attempt is never overwritten.
            if ((p.getLong("gamesCompleted") ?: 0L).toInt() >= GAME_ORDER.size) return@runTransaction

            perGame.forEach { (gameType, score) ->
                tx.set(
                    participantRef.collection("gameResults").document(gameType),
                    mapOf("gameType" to gameType, "score" to score),
                )
            }
            tx.update(
                participantRef,
                mapOf(
                    "totalScore" to total,
                    "gamesCompleted" to GAME_ORDER.size,
                ),
            )
        }.await()

        maybeFinalize(challengeId)

        getChallenge(challengeId).getOrThrow()
    }

    override suspend fun getChallenge(challengeId: String): Result<ChallengeResponseDto> =
        runCatching {
            val doc = firestore.collection("challenges").document(challengeId).get().await()
            val participantsSnap = firestore.collection("challenges").document(challengeId)
                .collection("participants").get().await()
            doc.toChallengeResponseDto(participantsSnap.toParticipants())
                ?: throw Exception("Challenge not found")
        }

    // --- Finalization & payouts ---

    /**
     * Spec "Challenge" payout: once every participant (≥2) has played all 6 games, the highest total
     * score wins 75% of the pooled stakes, 2nd place gets their own stake back, everyone else
     * loses what they staked. Guarded by `status == "OPEN"` so it runs exactly once.
     */
    private suspend fun maybeFinalize(challengeId: String) {
        val challengeRef = firestore.collection("challenges").document(challengeId)
        val usersCol = firestore.collection("users")

        firestore.runTransaction { tx ->
            val challenge = tx.get(challengeRef)
            if (challenge.getString("status") != "OPEN") return@runTransaction
            val count = (challenge.getLong("participantCount") ?: 0L).toInt()
            if (count < 2) return@runTransaction
            @Suppress("UNCHECKED_CAST")
            val ids = challenge.get("participantIds") as? List<String> ?: emptyList()

            // Reads must precede writes: load every participant + the user docs we'll pay.
            val partSnaps = ids.map { tx.get(challengeRef.collection("participants").document(it)) }
            if (partSnaps.any { (it.getLong("gamesCompleted") ?: 0L).toInt() < GAME_ORDER.size }) {
                return@runTransaction
            }

            val ranked = partSnaps.sortedWith(
                compareByDescending<DocumentSnapshot> { (it.getLong("totalScore") ?: 0L) }
                    .thenBy { it.getTimestamp("joinedAt")?.toDate()?.time ?: Long.MAX_VALUE }
            )
            val stakedStars = (challenge.getLong("stakedStars") ?: 0L).toInt()
            val stakedTokens = (challenge.getLong("stakedTokens") ?: 0L).toInt()
            val poolStars = stakedStars * count
            val poolTokens = stakedTokens * count

            val winner = ranked[0]
            val winnerUid = winner.getString("uid") ?: winner.id
            val winnerRef = usersCol.document(winnerUid)
            val winnerSnap = tx.get(winnerRef)

            val second = ranked.getOrNull(1)
            val secondUid = second?.let { it.getString("uid") ?: it.id }
            val secondRef = secondUid?.let { usersCol.document(it) }
            val secondSnap = secondRef?.let { tx.get(it) }

            // Writes.
            grantToUser(tx, winnerRef, winnerSnap, poolStars * 3 / 4, poolTokens * 3 / 4, earned = true)
            if (secondRef != null && secondSnap != null) {
                grantToUser(tx, secondRef, secondSnap, stakedStars, stakedTokens, earned = false)
            }
            tx.update(
                challengeRef,
                mapOf(
                    "status" to "COMPLETED",
                    "winnerId" to winnerUid,
                    "completedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
    }

    /**
     * Add [starsDelta]/[tokensDelta] to a user. When [earned], positive stars also feed
     * `cycleStars` (regional monthly leaderboard) and `totalStarsEarned`; refunds (2nd place
     * stake-back) do not. The league level is recomputed from the new star total either way,
     * mirroring `FirebaseMatchRepository.applyStarDelta`.
     */
    private fun grantToUser(
        tx: Transaction,
        ref: DocumentReference,
        snap: DocumentSnapshot,
        starsDelta: Int,
        tokensDelta: Int,
        earned: Boolean,
    ) {
        val newStars = ((snap.getLong("stars") ?: 0L).toInt() + starsDelta).coerceAtLeast(0)
        val newTokens = ((snap.getLong("tokens") ?: 0L).toInt() + tokensDelta).coerceAtLeast(0)
        val updates = mutableMapOf<String, Any?>(
            "stars" to newStars,
            "tokens" to newTokens,
            "leagueLevel" to leagueLevelForStars(newStars),
        )
        if (earned && starsDelta > 0) {
            updates["cycleStars"] = (snap.getLong("cycleStars") ?: 0L).toInt() + starsDelta
            updates["weeklyCycleStars"] = (snap.getLong("weeklyCycleStars") ?: 0L).toInt() + starsDelta
            updates["totalStarsEarned"] = (snap.getLong("totalStarsEarned") ?: 0L).toInt() + starsDelta
        }
        tx.update(ref, updates)
    }

    // --- Stake helpers ---

    private fun requireStake(user: DocumentSnapshot, stakedStars: Int, stakedTokens: Int) {
        val stars = (user.getLong("stars") ?: 0L).toInt()
        val tokens = (user.getLong("tokens") ?: 0L).toInt()
        if (stars < stakedStars) throw Exception("Not enough stars")
        if (tokens < stakedTokens) throw Exception("Not enough tokens")
    }

    private fun deductStake(stakedStars: Int, stakedTokens: Int): Map<String, Any> = mapOf(
        "stars" to FieldValue.increment(-stakedStars.toLong()),
        "tokens" to FieldValue.increment(-stakedTokens.toLong()),
    )

    private fun newParticipant(uid: String, username: String): Map<String, Any?> = mapOf(
        "uid" to uid,
        "username" to username,
        "totalScore" to 0,
        "gamesCompleted" to 0,
        "joinedAt" to FieldValue.serverTimestamp(),
    )

    // --- Mapping ---

    private fun com.google.firebase.firestore.QuerySnapshot.toParticipants(): List<ChallengeParticipantDto> =
        documents.mapNotNull { p ->
            ChallengeParticipantDto(
                id = p.id,
                username = p.getString("username") ?: return@mapNotNull null,
                totalScore = (p.getLong("totalScore") ?: 0L).toInt(),
                gamesCompleted = (p.getLong("gamesCompleted") ?: 0L).toInt(),
            )
        }

    private suspend fun DocumentSnapshot.toChallengeResponseDto(): ChallengeResponseDto? {
        val participants = reference.collection("participants").get().await().toParticipants()
        return toChallengeResponseDto(participants)
    }

    private fun DocumentSnapshot.toChallengeResponseDto(
        participants: List<ChallengeParticipantDto>,
    ): ChallengeResponseDto? {
        return ChallengeResponseDto(
            id = id,
            creatorId = getString("creatorId") ?: "",
            creatorUsername = getString("creatorUsername") ?: return null,
            region = getString("region") ?: return null,
            stakedStars = (getLong("stakedStars") ?: 0L).toInt(),
            stakedTokens = (getLong("stakedTokens") ?: 0L).toInt(),
            status = getString("status") ?: "OPEN",
            participants = participants,
        )
    }
}
