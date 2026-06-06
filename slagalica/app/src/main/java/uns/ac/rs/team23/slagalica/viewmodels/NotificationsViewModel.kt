package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.services.NtfyNotificationService
import java.util.UUID

enum class NotificationFilter { ALL, UNREAD, READ }

class NotificationsViewModel(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _notifications = MutableStateFlow(emptyList<Notification>())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    // notificationId -> seconds remaining for invite countdown
    private val _inviteCountdowns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inviteCountdowns: StateFlow<Map<String, Int>> = _inviteCountdowns.asStateFlow()

    private val countdownJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            NtfyNotificationService.events.collect { incoming ->
                _notifications.update { listOf(incoming) + it }
                if (incoming.type == NotificationType.INVITE && incoming.inviteId != null) {
                    startInviteCountdown(incoming.id, incoming.inviteId)
                }
            }
        }
        fetchPendingInvites()
    }

    private fun fetchPendingInvites() {
        viewModelScope.launch {
            matchRepository.getPendingInvites().onSuccess { invites ->
                val inviteNotifs = invites.map { inv ->
                    Notification(
                        id = "invite_${inv.id}",
                        title = "Match invite",
                        message = "${inv.inviterUsername} is challenging you to a ${if (inv.isFriendly) "friendly" else "ranked"} match",
                        type = NotificationType.INVITE,
                        isRead = false,
                        timestamp = "Now",
                        inviteId = inv.id,
                    )
                }
                if (inviteNotifs.isNotEmpty()) {
                    _notifications.update { existing ->
                        val existingInviteIds = existing.mapNotNull { it.inviteId }.toSet()
                        val newOnes = inviteNotifs.filter { it.inviteId !in existingInviteIds }
                        newOnes + existing
                    }
                    inviteNotifs.forEach { n ->
                        if (n.inviteId != null) startInviteCountdown(n.id, n.inviteId)
                    }
                }
            }
        }
    }

    private fun startInviteCountdown(notificationId: String, inviteId: Long) {
        countdownJobs[notificationId]?.cancel()
        _inviteCountdowns.update { it + (notificationId to 10) }
        countdownJobs[notificationId] = viewModelScope.launch {
            for (sec in 9 downTo 0) {
                delay(1_000)
                _inviteCountdowns.update { it + (notificationId to sec) }
            }
            // Auto-reject on timeout
            respondToInvite(inviteId, accept = false, notificationId = notificationId)
        }
    }

    fun respondToInvite(inviteId: Long, accept: Boolean, notificationId: String) {
        countdownJobs[notificationId]?.cancel()
        countdownJobs.remove(notificationId)
        _inviteCountdowns.update { it - notificationId }
        viewModelScope.launch {
            matchRepository.respondToInvite(inviteId, accept)
                .onSuccess { match ->
                    if (accept && match.status == "IN_PROGRESS") {
                        val opponentName = if (match.player1Username == match.player2Username)
                            match.player1Username
                        else
                            match.player2Username ?: match.player1Username
                        MatchStore.set(match.id, opponentName)
                    }
                }
            _notifications.update { list -> list.filter { it.id != notificationId } }
        }
    }

    val filtered get() = _notifications.value.filter {
        when (_filter.value) {
            NotificationFilter.ALL -> true
            NotificationFilter.UNREAD -> !it.isRead
            NotificationFilter.READ -> it.isRead
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

    override fun onCleared() {
        countdownJobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}
