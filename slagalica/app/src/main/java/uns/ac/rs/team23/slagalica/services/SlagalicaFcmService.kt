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
        private const val CHANNEL_ID = "slagalica_notifications"

        private val _events = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
        val events: SharedFlow<Notification> = _events.asSharedFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        )

        _events.tryEmit(notification)
        showNotification(title, body)
    }

    private fun showNotification(title: String, message: String) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Slagalica Notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Match invites, challenges, and game events" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.slagalica_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notif)
    }

    override fun onDestroy() {
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}
