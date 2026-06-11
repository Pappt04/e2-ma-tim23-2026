package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.network.dto.ChallengeParticipantDto
import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto

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
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        val userDoc = firestore.collection("users").document(uid).get().await()
        val username = userDoc.getString("username") ?: ""

        val challengeRef = firestore.collection("challenges").document()
        challengeRef.set(
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
            )
        ).await()

        challengeRef.collection("participants").document(uid).set(
            mapOf(
                "uid" to uid,
                "username" to username,
                "totalScore" to 0,
                "gamesCompleted" to 0,
                "joinedAt" to FieldValue.serverTimestamp(),
            )
        ).await()

        getChallenge(challengeRef.id).getOrThrow()
    }

    override suspend fun joinChallenge(challengeId: String): Result<ChallengeResponseDto> =
        runCatching {
            val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username") ?: ""

            val challengeRef = firestore.collection("challenges").document(challengeId)

            firestore.runTransaction { tx ->
                val challenge = tx.get(challengeRef)
                if (challenge.getString("status") != "OPEN") throw Exception("Izazov nije otvoren")
                val count = (challenge.getLong("participantCount") ?: 0L).toInt()
                if (count >= 4) throw Exception("Izazov je pun")
                @Suppress("UNCHECKED_CAST")
                val ids = challenge.get("participantIds") as? List<String> ?: emptyList()
                if (uid in ids) throw Exception("Već ste u izazovu")
                tx.update(
                    challengeRef, mapOf(
                        "participantIds" to ids + uid,
                        "participantCount" to count + 1,
                    )
                )
            }.await()

            challengeRef.collection("participants").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "username" to username,
                    "totalScore" to 0,
                    "gamesCompleted" to 0,
                    "joinedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            getChallenge(challengeId).getOrThrow()
        }

    override suspend fun submitScore(
        challengeId: String,
        gameType: String,
        score: Int,
    ): Result<ChallengeResponseDto> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")

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

    override suspend fun getChallenge(challengeId: String): Result<ChallengeResponseDto> =
        runCatching {
            val doc = firestore.collection("challenges").document(challengeId).get().await()
            val participantsSnap = firestore.collection("challenges").document(challengeId)
                .collection("participants").get().await()
            val participants = participantsSnap.documents.mapNotNull { p ->
                ChallengeParticipantDto(
                    id = p.id,
                    username = p.getString("username") ?: return@mapNotNull null,
                    totalScore = (p.getLong("totalScore") ?: 0L).toInt(),
                    gamesCompleted = (p.getLong("gamesCompleted") ?: 0L).toInt(),
                )
            }
            doc.toChallengeResponseDto(participants) ?: throw Exception("Izazov nije pronađen")
        }

    private suspend fun com.google.firebase.firestore.DocumentSnapshot.toChallengeResponseDto(): ChallengeResponseDto? {
        val participantsSnap = reference.collection("participants").get().await()
        val participants = participantsSnap.documents.mapNotNull { p ->
            ChallengeParticipantDto(
                id = p.id,
                username = p.getString("username") ?: return@mapNotNull null,
                totalScore = (p.getLong("totalScore") ?: 0L).toInt(),
                gamesCompleted = (p.getLong("gamesCompleted") ?: 0L).toInt(),
            )
        }
        return toChallengeResponseDto(participants)
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChallengeResponseDto(
        participants: List<ChallengeParticipantDto>,
    ): ChallengeResponseDto? {
        return ChallengeResponseDto(
            id = id,
            creatorUsername = getString("creatorUsername") ?: return null,
            region = getString("region") ?: return null,
            stakedStars = (getLong("stakedStars") ?: 0L).toInt(),
            stakedTokens = (getLong("stakedTokens") ?: 0L).toInt(),
            status = getString("status") ?: "OPEN",
            participants = participants,
        )
    }
}
