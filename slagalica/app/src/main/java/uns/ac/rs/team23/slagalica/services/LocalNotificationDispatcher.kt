package uns.ac.rs.team23.slagalica.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uns.ac.rs.team23.slagalica.R
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType

/**
 * Shows Android system notifications from in-app Firestore listeners (free tier —
 * no Cloud Functions / FCM backend required).
 */
object LocalNotificationDispatcher {

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(SlagalicaFcmService.CHANNEL_CHAT, "Chat", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Chat notifications" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_RANKING, "Ranking", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Ranking notifications" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_REWARD, "Rewards", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reward notifications" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_OTHER, "Other", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Friend invites and other notifications" },
        ).forEach { manager.createNotificationChannel(it) }
    }

    fun show(context: Context, notification: Notification) {
        ensureChannels(context)
        SlagalicaFcmService.emitNotification(notification)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channelId = when (notification.type) {
            NotificationType.CHAT -> SlagalicaFcmService.CHANNEL_CHAT
            NotificationType.RANKING -> SlagalicaFcmService.CHANNEL_RANKING
            NotificationType.REWARD -> SlagalicaFcmService.CHANNEL_REWARD
            else -> SlagalicaFcmService.CHANNEL_OTHER
        }

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.slagalica_logo)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setPriority(
                if (notification.type == NotificationType.REWARD) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setAutoCancel(true)
            .build()
        manager.notify(notification.id.hashCode(), notif)
    }
}
