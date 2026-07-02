package uns.ac.rs.team23.slagalica.services

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.R
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType

class SlagalicaFcmService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_CHAT = "channel_chat"
        const val CHANNEL_RANKING = "channel_ranking"
        const val CHANNEL_REWARD = "channel_reward"
        const val CHANNEL_OTHER = "channel_other"

        private val _events = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
        val events: SharedFlow<Notification> = _events.asSharedFlow()

        /** Used by [LocalNotificationDispatcher] and FCM when a push arrives. */
        fun emitNotification(notification: Notification) {
            _events.tryEmit(notification)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannelsCreated()
    }

    private fun ensureChannelsCreated() {
        val manager = getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_CHAT, "Chat", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Chat notifications" },
            NotificationChannel(CHANNEL_RANKING, "Ranking", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Ranking notifications" },
            NotificationChannel(CHANNEL_REWARD, "Rewards", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reward notifications" },
            NotificationChannel(CHANNEL_OTHER, "Other", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Friend invites and other notifications" },
        ).forEach { manager.createNotificationChannel(it) }
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "Slagalica"
        val body = data["body"] ?: message.notification?.body ?: ""
        val type = data["type"] ?: "OTHER"
        val inviteId = data["inviteId"]

        val notifType = when (type.uppercase()) {
            "INVITE" -> NotificationType.INVITE
            "CHAT" -> NotificationType.CHAT
            "RANKING" -> NotificationType.RANKING
            "REWARD" -> NotificationType.REWARD
            else -> NotificationType.OTHER
        }

        val notification = Notification(
            id = data["notificationId"] ?: System.currentTimeMillis().toString(),
            title = title,
            message = body,
            type = notifType,
            isRead = false,
            timestamp = "Just now",
            inviteId = inviteId,
            createdAtMillis = System.currentTimeMillis(),
        )

        _events.tryEmit(notification)
        showNotification(notification)
    }

    private fun showNotification(notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        val channelId = when (notification.type) {
            NotificationType.CHAT -> CHANNEL_CHAT
            NotificationType.RANKING -> CHANNEL_RANKING
            NotificationType.REWARD -> CHANNEL_REWARD
            else -> CHANNEL_OTHER
        }

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.slagalica_logo)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setContentIntent(NotificationNavigation.pendingIntent(this, notification))
            .setPriority(
                if (notification.type == NotificationType.REWARD) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .build()
        manager.notify(notification.id.hashCode(), notif)
    }

    private fun saveToFirestore(notification: Notification) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("notifications").document(notification.id)
        ref.set(
            mapOf(
                "id" to notification.id,
                "title" to notification.title,
                "message" to notification.message,
                "type" to notification.type.name,
                "isRead" to false,
                "inviteId" to notification.inviteId,
                "createdAtMillis" to notification.createdAtMillis,
            )
        )
    }

    override fun onDestroy() {
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}
