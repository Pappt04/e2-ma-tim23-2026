package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto

class FirebaseChatRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ChatRepository {

    override suspend fun getHistory(region: String): Result<List<ChatMessageDto>> = runCatching {
        val snapshot = firestore.collection("chat")
            .document(region)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(50)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toChatMessageDto() }
    }

    override suspend fun sendMessage(region: String, content: String): Result<ChatMessageDto> =
        runCatching {
            val user = auth.currentUser ?: throw Exception("Nije prijavljen")
            val uid = user.uid
            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username") ?: user.displayName ?: "Korisnik"

            val now = com.google.firebase.Timestamp.now()
            val data = mapOf(
                "senderUid" to uid,
                "senderUsername" to username,
                "region" to region,
                "content" to content,
                "sentAt" to now,
            )
            val ref = firestore.collection("chat").document(region)
                .collection("messages")
                .add(data)
                .await()

            ChatMessageDto(
                id = ref.id,
                senderUsername = username,
                region = region,
                content = content,
                sentAt = now.toDate().toString(),
            )
        }

    override fun observeMessages(region: String): Flow<ChatMessageDto> = callbackFlow {
        val query = firestore.collection("chat")
            .document(region)
            .collection("messages")
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limitToLast(50)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                    change.document.toChatMessageDto()?.let { trySend(it) }
                }
            }
        }
        awaitClose { registration.remove() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChatMessageDto(): ChatMessageDto? {
        val sender = getString("senderUsername") ?: return null
        val region = getString("region") ?: return null
        val content = getString("content") ?: return null
        val sentAt = getTimestamp("sentAt")?.toDate()?.toString() ?: ""
        return ChatMessageDto(
            id = id,
            senderUsername = sender,
            region = region,
            content = content,
            sentAt = sentAt,
        )
    }
}
