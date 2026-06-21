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
                .apply { description = "Obaveštenja u četu" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_RANKING, "Rangiranje", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Obaveštenja o rangiranju" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_REWARD, "Nagrade", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Obaveštenja o nagradama" },
            NotificationChannel(SlagalicaFcmService.CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Pozivi za prijatelje i ostala obaveštenja" },
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
