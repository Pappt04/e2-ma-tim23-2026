package uns.ac.rs.team23.slagalica.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uns.ac.rs.team23.slagalica.R

/**
 * Foreground service that keeps the Firestore notification/invite listeners ([ClientDbListeners])
 * alive while the app is backgrounded. Without it the listeners are tied to the app process and
 * detach when the process is reclaimed, so invites/chat/reward notifications never arrive unless
 * the app is in the foreground (the app has no FCM push backend on the free plan).
 */
class NotificationListenerService : Service(), KoinComponent {

    private val listeners: ClientDbListeners by inject()

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = intent?.getStringExtra(EXTRA_UID)
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        listeners.start(uid)
        // Re-deliver the last intent (with the uid) if the system restarts us.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        listeners.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.slagalica_logo)
            .setContentTitle("Slagalica")
            .setContentText("Listening for match invites and messages")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0,
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background sync",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Keeps notifications arriving while the app is closed" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "slagalica_background_sync"
        private const val NOTIFICATION_ID = 4823
        private const val EXTRA_UID = "uid"

        fun start(context: Context, uid: String) {
            val intent = Intent(context, NotificationListenerService::class.java)
                .putExtra(EXTRA_UID, uid)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotificationListenerService::class.java))
        }
    }
}
