package uns.ac.rs.team23.slagalica.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.views.MainActivity

/** Deep-link target when the user taps a system notification. */
data class NotificationNavTarget(
    val route: String,
    val inviteId: String? = null,
)

/**
 * Routes notification taps into [MainActivity] and then into Compose navigation.
 * Pending targets are held until the user is logged in (cold start from a notification).
 */
object NotificationNavigation {

    const val EXTRA_ROUTE = "slagalica_route"
    const val EXTRA_INVITE_ID = "slagalica_invite_id"
    const val ROUTE_NOTIFICATIONS = "notifications"

    @Volatile
    private var pending: NotificationNavTarget? = null

    private val _tapSignal = MutableStateFlow(0)
    /** Bumps on every notification tap so [AppNavHost] can navigate while already logged in. */
    val tapSignal: StateFlow<Int> = _tapSignal.asStateFlow()

    fun setPending(target: NotificationNavTarget) {
        pending = target
        _tapSignal.value++
    }

    fun takePending(): NotificationNavTarget? = pending.also { pending = null }

    fun parseIntent(intent: Intent?): NotificationNavTarget? {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return null
        return NotificationNavTarget(route, intent.getStringExtra(EXTRA_INVITE_ID))
    }

    fun pendingIntent(context: Context, notification: Notification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, routeFor(notification.type))
            notification.inviteId?.let { putExtra(EXTRA_INVITE_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun routeFor(type: NotificationType): String = when (type) {
        NotificationType.INVITE,
        NotificationType.CHAT,
        NotificationType.RANKING,
        NotificationType.REWARD,
        NotificationType.OTHER,
        -> ROUTE_NOTIFICATIONS
    }
}
