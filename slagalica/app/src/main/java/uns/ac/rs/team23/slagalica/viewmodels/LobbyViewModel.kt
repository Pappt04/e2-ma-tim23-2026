package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.network.dto.MatchResponseDto
import uns.ac.rs.team23.slagalica.repository.MatchRepository

sealed class LobbyState {
    data object Idle : LobbyState()
    data object Searching : LobbyState()
    data class InviteSent(val inviteId: String, val opponentName: String) : LobbyState()
    data class Error(val message: String) : LobbyState()
    data class OpponentFound(val opponentName: String, val opponentReady: Boolean = false) : LobbyState()
    data class YouAreReady(val opponentName: String, val opponentReady: Boolean = false) : LobbyState()
    data class Countdown(val opponentName: String, val seconds: Int) : LobbyState()
    data object Starting : LobbyState()
}

class LobbyViewModel(private val matchRepository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow<LobbyState>(LobbyState.Idle)
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var observerJob: Job? = null
    private var countdownJob: Job? = null
    private var myUsername: String = ""

    private var currentFriendly: Boolean = false
    private var opponentReadyLocal: Boolean = false
    private var myReadyLocal: Boolean = false

    fun startSearch(username: String, friendly: Boolean = false) {
        myUsername = username
        currentFriendly = friendly
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            matchRepository.startRandomMatch(friendly)
                .onSuccess { match ->
                    when {
                        match.status == "IN_PROGRESS" && match.player2Username != null -> {
                            onMatchFound(match)
                        }
                        match.status == "WAITING_FOR_OPPONENT" -> startPolling()
                        else -> _state.value = LobbyState.Error("Unexpected match state: ${match.status}")
                    }
                }
                .onFailure { _state.value = LobbyState.Error(it.message ?: "Failed to start match") }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(60) { attempt ->
                delay(2_000)
                matchRepository.getCurrentMatch()
                    .onSuccess { match ->
                        if (match != null && match.status == "IN_PROGRESS" && match.player2Username != null) {
                            onMatchFound(match)
                            pollingJob?.cancel()
                            return@onSuccess
                        }
                    }
                if (_state.value !is LobbyState.Searching) return@repeat

                if (attempt % 3 == 2) {
                    matchRepository.tryJoinWaiting(myUsername, currentFriendly)
                        .onSuccess { joined ->
                            if (joined != null) {
                                onMatchFound(joined)
                                pollingJob?.cancel()
                            }
                        }
                }
            }
            if (_state.value is LobbyState.Searching) {
                _state.value = LobbyState.Error("No opponent found. Try again.")
            }
        }
    }

    private fun resolveOpponent(player1: String, player2: String): String =
        if (player1 == myUsername) player2 else player1

    private fun onMatchFound(match: MatchResponseDto) {
        myReadyLocal = false
        opponentReadyLocal = false
        val opponent = resolveOpponent(match.player1Username, match.player2Username ?: "")
        MatchStore.set(
            match.id,
            opponent,
            friendly = currentFriendly,
            myUid = matchRepository.currentUserId() ?: "",
            hostId = match.player1Id,
            player1 = match.player1Username,
            player2 = match.player2Username ?: "",
        )
        _state.value = LobbyState.OpponentFound(opponent)
        startMatchObserver(match.id, opponent)
    }

    private fun startMatchObserver(matchId: String, opponentName: String) {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            matchRepository.observeMatch(matchId).collect { match ->
                val myUid = matchRepository.currentUserId() ?: return@collect
                val iAmPlayer1 = match.player1Id == myUid
                val newOpponentReady = if (iAmPlayer1) match.player2Ready else match.player1Ready
                opponentReadyLocal = newOpponentReady

                val current = _state.value
                if (current is LobbyState.Countdown || current is LobbyState.Starting) return@collect

                if (myReadyLocal && newOpponentReady) {
                    startCountdown(opponentName)
                } else if (myReadyLocal) {
                    _state.value = LobbyState.YouAreReady(opponentName, newOpponentReady)
                } else {
                    _state.value = LobbyState.OpponentFound(opponentName, newOpponentReady)
                }
            }
        }
    }

    fun clickReady() {
        val s = _state.value
        val opponent = when (s) {
            is LobbyState.OpponentFound -> s.opponentName
            else -> return
        }
        myReadyLocal = true
        _state.value = LobbyState.YouAreReady(opponent, opponentReadyLocal)
        viewModelScope.launch {
            matchRepository.markReady(MatchStore.matchId)
        }
        if (opponentReadyLocal) {
            startCountdown(opponent)
        }
    }

    private fun startCountdown(opponentName: String) {
        if (countdownJob != null) return
        countdownJob = viewModelScope.launch {
            delay(1_500)
            for (i in 3 downTo 1) {
                _state.value = LobbyState.Countdown(opponentName, i)
                delay(1_000)
            }
            _state.value = LobbyState.Starting
        }
    }

    fun startFriendSearch(friendId: String, username: String, friendly: Boolean = false) {
        myUsername = username
        currentFriendly = friendly
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            matchRepository.sendFriendInvite(friendId, friendly)
                .onSuccess { match ->
                    if (match.status == "INVITE_SENT") {
                        val opponentName = match.player2Username ?: "Friend"
                        _state.value = LobbyState.InviteSent(match.id, opponentName)
                    } else if (match.status == "IN_PROGRESS") {
                        onMatchFound(match)
                    } else {
                        _state.value = LobbyState.Error("Unexpected state: ${match.status}")
                    }
                }
                .onFailure { _state.value = LobbyState.Error(it.message ?: "Failed to send invite") }
        }
    }

    fun cancelInvite() {
        val s = _state.value as? LobbyState.InviteSent ?: return
        viewModelScope.launch {
            matchRepository.cancelInvite(s.inviteId)
        }
        MatchStore.clear()
        _state.value = LobbyState.Idle
    }

    fun cancel() {
        pollingJob?.cancel()
        observerJob?.cancel()
        countdownJob?.cancel()
        countdownJob = null
        viewModelScope.launch { matchRepository.cancelQueue() }
        MatchStore.clear()
        _state.value = LobbyState.Idle
    }

    override fun onCleared() {
        pollingJob?.cancel()
        observerJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }
}
