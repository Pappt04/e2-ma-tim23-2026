package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.network.dto.ChatMessageDto
import java.util.UUID

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
            val user = auth.currentUser ?: throw Exception("Not logged in")
            val uid = user.uid
            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username") ?: user.displayName ?: "User"
            val userRegion = userDoc.getString("region") ?: ""
            if (userRegion != region) {
                throw Exception("You can only chat in your own region")
            }

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

            // Best-effort: a peer-notification failure must not fail the send (the message is
            // already stored, and the daily "send a chat message" mission keys off this success).
            runCatching { notifyRegionPeers(uid, region, username, content) }

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

    private suspend fun notifyRegionPeers(senderUid: String, region: String, sender: String, preview: String) {
        val peers = firestore.collection("users")
            .whereEqualTo("region", region)
            .whereEqualTo("isGuest", false)
            .get()
            .await()
        val body = preview.take(80)
        peers.documents.forEach { doc ->
            if (doc.id == senderUid) return@forEach
            FirestoreNotificationWriter.push(
                firestore,
                doc.id,
                Notification(
                    id = UUID.randomUUID().toString(),
                    title = "New message in $region",
                    message = "$sender: $body",
                    type = NotificationType.CHAT,
                ),
            )
        }
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
