package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Notification

/** Writes an in-app notification document for another user (picked up by [NotificationsViewModel]). */
object FirestoreNotificationWriter {

    suspend fun push(firestore: FirebaseFirestore, userId: String, notification: Notification) {
        firestore.collection("users").document(userId).collection("notifications")
            .document(notification.id)
            .set(
                mapOf(
                    "id" to notification.id,
                    "title" to notification.title,
                    "message" to notification.message,
                    "type" to notification.type.name,
                    "isRead" to notification.isRead,
                    "inviteId" to notification.inviteId,
                    "createdAtMillis" to notification.createdAtMillis,
                )
            )
            .await()
    }
}
