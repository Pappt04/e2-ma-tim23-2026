package uns.ac.rs.team23.slagalica.models

import java.time.LocalDateTime

enum class NotificationType(val label: String) {
    CHAT("Chat"),
    RANKING("Ranking"),
    REWARD("Rewards"),
    INVITE("Invite"),
    OTHER("Other")
}

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val isRead: Boolean = false,
    val timestamp: String = "",
    val inviteId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** When true, Firestore listener won't re-post a system notification (local-only). */
    val suppressPush: Boolean = false,
)