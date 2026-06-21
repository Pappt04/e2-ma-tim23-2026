package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType

class FirebaseNotificationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : NotificationRepository {

    private fun notificationsRef() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid).collection("notifications")
    }

    override suspend fun saveNotification(notification: Notification): Result<Unit> = runCatching {
        val ref = notificationsRef() ?: return@runCatching
        ref.document(notification.id).set(
            mapOf(
                "id" to notification.id,
                "title" to notification.title,
                "message" to notification.message,
                "type" to notification.type.name,
                "isRead" to notification.isRead,
                "inviteId" to notification.inviteId,
                "createdAtMillis" to notification.createdAtMillis,
                "suppressPush" to notification.suppressPush,
            )
        ).await()
    }

    override suspend fun getNotifications(): Result<List<Notification>> = runCatching {
        val ref = notificationsRef() ?: return@runCatching emptyList()
        val snap = ref
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
        snap.documents.mapNotNull { doc ->
            val typeStr = doc.getString("type") ?: return@mapNotNull null
            val type = runCatching { NotificationType.valueOf(typeStr) }.getOrElse { NotificationType.OTHER }
            val millis = doc.getLong("createdAtMillis") ?: 0L
            Notification(
                id = doc.getString("id") ?: doc.id,
                title = doc.getString("title") ?: "",
                message = doc.getString("message") ?: "",
                type = type,
                isRead = doc.getBoolean("isRead") ?: false,
                inviteId = doc.getString("inviteId"),
                createdAtMillis = millis,
                timestamp = formatTimestamp(millis),
            )
        }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        notificationsRef()?.document(notificationId)?.update("isRead", true)?.await()
    }

    override suspend fun markAllAsRead(): Result<Unit> = runCatching {
        val ref = notificationsRef() ?: return@runCatching
        val unread = ref.whereEqualTo("isRead", false).get().await()
        if (unread.isEmpty) return@runCatching
        val batch = firestore.batch()
        unread.documents.forEach { batch.update(it.reference, "isRead", true) }
        batch.commit().await()
    }

    private fun formatTimestamp(millis: Long): String {
        if (millis == 0L) return ""
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }
}
