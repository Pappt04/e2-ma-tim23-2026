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
import uns.ac.rs.team23.slagalica.repository.MatchRepository

sealed class LobbyState {
    data object Idle : LobbyState()
    data object Searching : LobbyState()
    data class InviteSent(val inviteId: String, val opponentName: String) : LobbyState()
    data class Error(val message: String) : LobbyState()
    data class OpponentFound(val opponentName: String) : LobbyState()
    data class YouAreReady(val opponentName: String) : LobbyState()
    data class Countdown(val opponentName: String, val seconds: Int) : LobbyState()
    data object Starting : LobbyState()
}

class LobbyViewModel(private val matchRepository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow<LobbyState>(LobbyState.Idle)
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var myUsername: String = ""

    fun startSearch(username: String, friendly: Boolean = false) {
        myUsername = username
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            matchRepository.startRandomMatch(friendly)
                .onSuccess { match ->
                    when {
                        match.status == "IN_PROGRESS" && match.player2Username != null -> {
                            val opponent = resolveOpponent(match.player1Username, match.player2Username)
                            onMatchFound(match.id, opponent)
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
            repeat(60) {
                delay(2_000)
                matchRepository.getCurrentMatch()
                    .onSuccess { match ->
                        if (match != null && match.status == "IN_PROGRESS" && match.player2Username != null) {
                            val opponent = resolveOpponent(match.player1Username, match.player2Username)
                            onMatchFound(match.id, opponent)
                            pollingJob?.cancel()
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

    private fun onMatchFound(matchId: String, opponentName: String) {
        MatchStore.set(matchId, opponentName)
        _state.value = LobbyState.OpponentFound(opponentName)
    }

    fun clickReady() {
        val s = _state.value
        val opponent = when (s) {
            is LobbyState.OpponentFound -> s.opponentName
            else -> return
        }
        _state.value = LobbyState.YouAreReady(opponent)
        viewModelScope.launch {
            delay(1_500)
            for (i in 3 downTo 1) {
                _state.value = LobbyState.Countdown(opponent, i)
                delay(1_000)
            }
            _state.value = LobbyState.Starting
        }
    }

    fun startFriendSearch(friendId: String, username: String, friendly: Boolean = false) {
        myUsername = username
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            matchRepository.sendFriendInvite(friendId, friendly)
                .onSuccess { match ->
                    if (match.status == "INVITE_SENT") {
                        val opponentName = match.player2Username ?: "Friend"
                        _state.value = LobbyState.InviteSent(match.id, opponentName)
                    } else if (match.status == "IN_PROGRESS") {
                        val opponent = resolveOpponent(match.player1Username, match.player2Username ?: "Opponent")
                        onMatchFound(match.id, opponent)
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
        viewModelScope.launch { matchRepository.cancelQueue() }
        MatchStore.clear()
        _state.value = LobbyState.Idle
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
