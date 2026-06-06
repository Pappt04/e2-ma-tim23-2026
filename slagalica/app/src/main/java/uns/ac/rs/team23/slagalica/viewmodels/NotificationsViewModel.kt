package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.services.NtfyNotificationService
import java.time.LocalDateTime

enum class NotificationFilter { ALL, UNREAD, READ }

class NotificationsViewModel : ViewModel() {

    private val _notifications = MutableStateFlow(fakeMockNotifications())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    init {
        viewModelScope.launch {
            NtfyNotificationService.events.collect { incoming ->
                _notifications.update { listOf(incoming) + it }
            }
        }
    }

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    val filtered get() = _notifications.value.filter {
        when (_filter.value) {
            NotificationFilter.ALL    -> true
            NotificationFilter.UNREAD -> !it.isRead
            NotificationFilter.READ   -> it.isRead
        }
    }

    fun setFilter(f: NotificationFilter) { _filter.value = f }

    fun markAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    fun markAllAsRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
    }
}

private fun fakeMockNotifications() = listOf(
    Notification("1", "New Message", "Marko sent you a message in chat.",
        NotificationType.CHAT, false, "5 min ago"),
    Notification("2", "Chat Message", "Tamas: 'Wanna play a game?'",
        NotificationType.CHAT, true, "1h ago"),
    Notification("3", "Leaderboard", "You reached 3rd place on the weekly leaderboard!",
        NotificationType.RANKING, false, "3h ago"),
    Notification("4", "New League", "You've advanced to the Gold league!",
        NotificationType.RANKING, true, "Yesterday"),
    Notification("5", "Reward!", "You received 5 tokens for 1st place on the leaderboard.",
        NotificationType.REWARD, false, "Yesterday"),
    Notification("6", "Weekly Rewards", "Last week's rewards have been distributed.",
        NotificationType.REWARD, true, "2 days ago"),
    Notification("7", "Game Invite", "Doroca is inviting you to a friendly match.",
        NotificationType.OTHER, false, "20 min ago"),
    Notification("8", "Welcome!", "You have successfully registered. Here are 5 tokens.",
        NotificationType.OTHER, true, "5 days ago"),
)