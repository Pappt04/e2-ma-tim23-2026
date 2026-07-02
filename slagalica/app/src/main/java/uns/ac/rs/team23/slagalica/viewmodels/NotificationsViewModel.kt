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
import uns.ac.rs.team23.slagalica.repository.NotificationRepository
import uns.ac.rs.team23.slagalica.services.SlagalicaFcmService

enum class NotificationFilter { ALL, UNREAD, READ }

class NotificationsViewModel(
    private val matchRepository: MatchRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _notifications = MutableStateFlow(emptyList<Notification>())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    private val _inviteCountdowns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inviteCountdowns: StateFlow<Map<String, Int>> = _inviteCountdowns.asStateFlow()

    private val countdownJobs = mutableMapOf<String, Job>()

    init {
        loadNotifications()
        fetchPendingInvites()
        viewModelScope.launch {
            SlagalicaFcmService.events.collect { incoming ->
                addNotification(incoming)
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            notificationRepository.getNotifications().onSuccess { saved ->
                _notifications.update { current ->
                    val existingIds = current.map { it.id }.toSet()
                    val newOnes = saved.filter { it.id !in existingIds }
                    current + newOnes
                }
                _notifications.value
                    .filter { it.type == NotificationType.INVITE && it.inviteId != null && !it.isRead }
                    .forEach { n -> if (n.inviteId != null) startInviteCountdown(n.id, n.inviteId) }
            }
        }
    }

    private fun addNotification(notification: Notification) {
        _notifications.update { listOf(notification) + it.filter { n -> n.id != notification.id } }
        viewModelScope.launch { notificationRepository.saveNotification(notification) }
        if (notification.type == NotificationType.INVITE && notification.inviteId != null) {
            startInviteCountdown(notification.id, notification.inviteId)
        }
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

    private fun startInviteCountdown(notificationId: String, inviteId: String) {
        countdownJobs[notificationId]?.cancel()
        _inviteCountdowns.update { it + (notificationId to 10) }
        countdownJobs[notificationId] = viewModelScope.launch {
            for (sec in 9 downTo 0) {
                delay(1_000)
                _inviteCountdowns.update { it + (notificationId to sec) }
            }
            respondToInvite(inviteId, accept = false, notificationId = notificationId)
        }
    }

    fun respondToInvite(
        inviteId: String,
        accept: Boolean,
        notificationId: String,
        onMatchStarted: () -> Unit = {},
    ) {
        countdownJobs[notificationId]?.cancel()
        countdownJobs.remove(notificationId)
        _inviteCountdowns.update { it - notificationId }
        viewModelScope.launch {
            matchRepository.respondToInvite(inviteId, accept)
                .onSuccess { match ->
                    if (accept && match.status == "IN_PROGRESS") {
                        val myUid = matchRepository.currentUserId().orEmpty()
                        val opponentName = if (match.player1Id == myUid) {
                            match.player2Username ?: match.player1Username
                        } else {
                            match.player1Username
                        }
                        MatchStore.set(
                            match.id,
                            opponentName,
                            friendly = match.isFriendly,
                            myUid = myUid,
                            hostId = match.player1Id,
                            player1 = match.player1Username,
                            player2 = match.player2Username ?: "",
                            player1Id = match.player1Id,
                            player2Id = match.player2Id.orEmpty(),
                        )
                        matchRepository.setInMatch(true)
                        // Navigate into the game only after MatchStore is populated, otherwise
                        // GameScreen mounts with a blank matchId and bails straight back to Home.
                        onMatchStarted()
                    }
                }
            _notifications.update { list -> list.filter { it.id != notificationId } }
        }
    }

    fun setFilter(f: NotificationFilter) { _filter.value = f }

    fun markAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
        viewModelScope.launch { notificationRepository.markAsRead(id) }
    }

    fun markAllAsRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
        viewModelScope.launch { notificationRepository.markAllAsRead() }
    }

    override fun onCleared() {
        countdownJobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}
