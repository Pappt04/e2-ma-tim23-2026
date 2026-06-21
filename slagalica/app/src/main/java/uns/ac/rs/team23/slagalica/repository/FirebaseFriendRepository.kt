package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Friend

class FirebaseFriendRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : FriendRepository {

    override fun currentUserId(): String? = auth.currentUser?.uid

    private fun friendsCol(uid: String) =
        firestore.collection("users").document(uid).collection("friends")

    override suspend fun searchUsers(prefix: String): Result<List<Friend>> = runCatching {
        val me = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        if (prefix.isBlank()) return@runCatching emptyList()

        val existingFriends = friendsCol(me).get().await().documents.map { it.id }.toSet()
        val ranks = loadMonthlyRanks()

        val snap = firestore.collection("users")
            .orderBy("username")
            .startAt(prefix)
            .endAt(prefix + "")
            .limit(20)
            .get()
            .await()

        snap.documents
            .filter { it.id != me && it.getBoolean("isGuest") != true && it.id !in existingFriends }
            .map { it.toFriend(ranks[it.id] ?: 0) }
    }

    override suspend fun addFriendByUsername(username: String): Result<Unit> = runCatching {
        val doc = firestore.collection("usernames").document(username).get().await()
        val uid = doc.getString("uid") ?: throw Exception("Korisnik nije pronađen")
        addFriendByUid(uid).getOrThrow()
    }

    override suspend fun addFriendByUid(uid: String): Result<Unit> = runCatching {
        val me = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        if (uid == me) throw Exception("Ne možete dodati sebe")

        val meDoc = firestore.collection("users").document(me).get().await()
        val friendDoc = firestore.collection("users").document(uid).get().await()
        if (!friendDoc.exists()) throw Exception("Korisnik nije pronađen")

        val now = FieldValue.serverTimestamp()
        firestore.runBatch { batch ->
            batch.set(
                friendsCol(me).document(uid),
                mapOf("uid" to uid, "username" to (friendDoc.getString("username") ?: ""), "addedAt" to now),
            )
            batch.set(
                friendsCol(uid).document(me),
                mapOf("uid" to me, "username" to (meDoc.getString("username") ?: ""), "addedAt" to now),
            )
        }.await()
    }

    override suspend fun removeFriend(uid: String): Result<Unit> = runCatching {
        val me = auth.currentUser?.uid ?: throw Exception("Nije prijavljen")
        firestore.runBatch { batch ->
            batch.delete(friendsCol(me).document(uid))
            batch.delete(friendsCol(uid).document(me))
        }.await()
    }

    override fun observeFriends(): Flow<List<Friend>> = callbackFlow {
        val me = auth.currentUser?.uid
        if (me == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = friendsCol(me).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            val friendUids = snap.documents.map { it.id }
            launch {
                runCatching { hydrate(friendUids) }
                    .onSuccess { trySend(it) }
                    .onFailure { trySend(emptyList()) }
            }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun hydrate(friendUids: List<String>): List<Friend> {
        if (friendUids.isEmpty()) return emptyList()
        val ranks = loadMonthlyRanks()
        return friendUids.mapNotNull { uid ->
            val doc = firestore.collection("users").document(uid).get().await()
            if (!doc.exists()) null else doc.toFriend(ranks[uid] ?: 0)
        }.sortedBy { it.username.lowercase() }
    }

    /** uid -> 1-based monthly rank (by cycleStars desc). Only players with cycleStars > 0 are ranked. */
    private suspend fun loadMonthlyRanks(): Map<String, Int> {
        val snap = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()
        val ordered = snap.documents
            .map { it.id to (it.getLong("cycleStars") ?: 0L) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        return ordered.mapIndexed { index, (uid, _) -> uid to (index + 1) }.toMap()
    }

    private fun DocumentSnapshot.toFriend(monthlyRank: Int) = Friend(
        uid = id,
        username = getString("username") ?: "",
        avatarIndex = (getLong("avatarIndex") ?: 0L).toInt(),
        region = getString("region") ?: "",
        stars = (getLong("stars") ?: 0L).toInt(),
        leagueLevel = (getLong("leagueLevel") ?: 0L).toInt(),
        monthlyRank = monthlyRank,
        onlineAt = getLong("onlineAt") ?: 0L,
        inMatch = getBoolean("inMatch") ?: false,
    )
}
