package uns.ac.rs.team23.slagalica.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import uns.ac.rs.team23.slagalica.R
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import java.util.concurrent.TimeUnit

class NtfyNotificationService : Service() {

    companion object {
        private const val NTFY_BASE_URL = "http://10.0.2.2:8081"
        private const val CHANNEL_ID = "slagalica_notifications"
        private const val FOREGROUND_NOTIF_ID = 1001
        private const val EXTRA_USER_ID = "user_id"

        private val _events = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
        val events: SharedFlow<Notification> = _events.asSharedFlow()

        fun start(context: Context, userId: Long) {
            val intent = Intent(context, NtfyNotificationService::class.java)
                .putExtra(EXTRA_USER_ID, userId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyNotificationService::class.java))
        }
    }

    private var eventSource: EventSource? = null
    private var userId: Long = -1L
    private lateinit var notifManager: NotificationManagerCompat

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        notifManager = NotificationManagerCompat.from(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_USER_ID, -1L) ?: -1L
        if (id == -1L) { stopSelf(); return START_NOT_STICKY }
        userId = id
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotif())
        subscribe()
        return START_STICKY
    }

    private fun subscribe() {
        eventSource?.cancel()
        val request = Request.Builder()
            .url("$NTFY_BASE_URL/slagalica-user-$userId/sse")
            .build()
        eventSource = EventSources.createFactory(client).newEventSource(request, SseListener())
    }

    private inner class SseListener : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            if (data.isBlank()) return
            try {
                val json = JSONObject(data)
                val event = json.optString("event")
                if (event == "keepalive" || event == "open") return
                val title = json.optString("title", "Slagalica")
                val message = json.optString("message", "")
                val tags = json.optString("tags", "")
                val isInvite = tags.contains("crossed_swords") && title.startsWith("Match invite")
                val notifType = when {
                    isInvite -> NotificationType.INVITE
                    tags.contains("speech_balloon") || tags.contains("envelope") || tags.contains("speech") -> NotificationType.CHAT
                    tags.contains("trophy") || tags.contains("star") -> NotificationType.RANKING
                    tags.contains("tada") -> NotificationType.REWARD
                    else -> NotificationType.OTHER
                }
                val inviteId = if (isInvite) {
                    Regex("inviteId:(\\d+)").find(message)?.groupValues?.get(1)?.toLongOrNull()
                } else null
                val notif = Notification(
                    id = id ?: System.currentTimeMillis().toString(),
                    title = title,
                    message = message.replace(Regex("\\s*inviteId:\\d+"), ""),
                    type = notifType,
                    isRead = false,
                    timestamp = "Just now",
                    inviteId = inviteId,
                )
                _events.tryEmit(notif)
                showNotification(title, message)
            } catch (_: Exception) {}
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            // Reconnect after 5 seconds on any failure
            Handler(Looper.getMainLooper()).postDelayed({
                if (userId != -1L) subscribe()
            }, 5_000)
        }
    }

    private fun showNotification(title: String, message: String) {
        if (!notifManager.areNotificationsEnabled()) return
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.slagalica_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notifManager.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun buildForegroundNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.slagalica_logo)
        .setContentTitle("Slagalica")
        .setContentText("Listening for notifications…")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Slagalica Notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Match invites, challenges, and game events" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        eventSource?.cancel()
        userId = -1L
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
