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
    private var activeMatchId: String = ""

    private var currentFriendly: Boolean = false
    private var opponentReadyLocal: Boolean = false
    private var myReadyLocal: Boolean = false

    fun startSearch(username: String, friendly: Boolean = false) {
        if (_state.value is LobbyState.Searching) return
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
        activeMatchId = match.id
        val opponent = resolveOpponent(match.player1Username, match.player2Username ?: "")
        MatchStore.set(
            match.id,
            opponent,
            friendly = match.isFriendly,
            myUid = matchRepository.currentUserId() ?: "",
            hostId = match.player1Id,
            player1 = match.player1Username,
            player2 = match.player2Username ?: "",
            player1Id = match.player1Id,
            player2Id = match.player2Id.orEmpty(),
        )
        _state.value = LobbyState.OpponentFound(opponent)
        viewModelScope.launch { matchRepository.setInMatch(true) }
        startMatchObserver(match.id, opponent)
    }

    /**
     * A friend accepted the invite and the match is already IN_PROGRESS. Unlike random
     * matchmaking there is no ready lobby for invites — the invitee jumps straight into the game,
     * so the inviter must too (otherwise it waits forever on a ready screen the invitee never sees).
     * Populate [MatchStore] and go directly to [LobbyState.Starting] → the game.
     */
    private fun onInviteMatchStarted(match: MatchResponseDto) {
        myReadyLocal = false
        opponentReadyLocal = false
        activeMatchId = match.id
        val opponent = resolveOpponent(match.player1Username, match.player2Username ?: "")
        MatchStore.set(
            match.id,
            opponent,
            friendly = match.isFriendly,
            myUid = matchRepository.currentUserId() ?: "",
            hostId = match.player1Id,
            player1 = match.player1Username,
            player2 = match.player2Username ?: "",
            player1Id = match.player1Id,
            player2Id = match.player2Id.orEmpty(),
        )
        viewModelScope.launch { matchRepository.setInMatch(true) }
        _state.value = LobbyState.Starting
    }

    private fun onOpponentLeftLobby() {
        countdownJob?.cancel()
        countdownJob = null
        observerJob?.cancel()
        activeMatchId = ""
        MatchStore.clear()
        viewModelScope.launch { matchRepository.setInMatch(false) }
        _state.value = LobbyState.Error("Your opponent left the lobby.")
    }

    private fun isLobbyPhase(): Boolean = when (_state.value) {
        is LobbyState.OpponentFound,
        is LobbyState.YouAreReady,
        is LobbyState.Countdown,
        -> true
        else -> false
    }

    private fun startMatchObserver(matchId: String, opponentName: String) {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            matchRepository.observeMatch(matchId).collect { match ->
                if (match.status == "CANCELLED" ||
                    (match.abandonedById != null && isLobbyPhase())
                ) {
                    onOpponentLeftLobby()
                    return@collect
                }
                if (match.status != "IN_PROGRESS") return@collect

                val myUid = matchRepository.currentUserId() ?: return@collect
                val iAmPlayer1 = match.player1Id == myUid
                val newOpponentReady = if (iAmPlayer1) match.player2Ready else match.player1Ready
                opponentReadyLocal = newOpponentReady

                val current = _state.value
                if (current is LobbyState.Countdown || current is LobbyState.Starting) return@collect

                if (myReadyLocal && newOpponentReady) {
                    startCountdown(opponentName, matchId)
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
            startCountdown(opponent, activeMatchId)
        }
    }

    private fun startCountdown(opponentName: String, matchId: String) {
        if (countdownJob != null) return
        countdownJob = viewModelScope.launch {
            delay(1_500)
            for (i in 3 downTo 1) {
                if (_state.value is LobbyState.Error) return@launch
                _state.value = LobbyState.Countdown(opponentName, i)
                delay(1_000)
            }
            val stillValid = matchRepository.getCurrentMatch()
                .getOrNull()
                ?.let { it.id == matchId && it.status == "IN_PROGRESS" }
                ?: false
            if (!stillValid) {
                onOpponentLeftLobby()
                return@launch
            }
            _state.value = LobbyState.Starting
        }
    }

    fun startFriendSearch(friendId: String, username: String, friendly: Boolean = false) {
        if (_state.value is LobbyState.Searching) return
        myUsername = username
        currentFriendly = friendly
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            matchRepository.sendFriendInvite(friendId, friendly)
                .onSuccess { match ->
                    when (match.status) {
                        "INVITE_SENT" -> {
                            val opponentName = match.player2Username ?: "Friend"
                            _state.value = LobbyState.InviteSent(match.id, opponentName)
                            startInviteAcceptPolling(match.id)
                        }
                        "IN_PROGRESS" -> onMatchFound(match)
                        else -> _state.value = LobbyState.Error("Unexpected state: ${match.status}")
                    }
                }
                .onFailure { _state.value = LobbyState.Error(it.message ?: "Failed to send invite") }
        }
    }

    private fun startInviteAcceptPolling(inviteId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(30) {
                delay(1_000)
                matchRepository.getAcceptedInviteMatch(inviteId).onSuccess { match ->
                    if (match != null && match.status == "IN_PROGRESS" && match.player2Username != null) {
                        onInviteMatchStarted(match)
                        pollingJob?.cancel()
                        return@onSuccess
                    }
                }
                if (_state.value !is LobbyState.InviteSent) return@repeat
            }
            if (_state.value is LobbyState.InviteSent) {
                matchRepository.cancelInvite(inviteId)
                MatchStore.clear()
                _state.value = LobbyState.Error("Your friend did not accept the invite.")
            }
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
        viewModelScope.launch {
            matchRepository.cancelQueue()
            matchRepository.setInMatch(false)
        }
        activeMatchId = ""
        MatchStore.clear()
        _state.value = LobbyState.Idle
    }

    /** Leave lobby — refunds tokens when backing out during search or the ready screen. */
    fun leaveLobby() {
        if (_state.value is LobbyState.Starting) return

        val matchId = activeMatchId.ifBlank { MatchStore.matchId }
        val inReadyLobby = isLobbyPhase()

        pollingJob?.cancel()
        observerJob?.cancel()
        countdownJob?.cancel()
        countdownJob = null

        when (_state.value) {
            is LobbyState.Searching, is LobbyState.InviteSent -> {
                viewModelScope.launch {
                    matchRepository.cancelQueue()
                    matchRepository.setInMatch(false)
                }
            }
            is LobbyState.OpponentFound,
            is LobbyState.YouAreReady,
            is LobbyState.Countdown,
            -> {
                viewModelScope.launch {
                    if (matchId.isNotBlank()) matchRepository.leavePreGameLobby(matchId)
                    matchRepository.setInMatch(false)
                }
            }
            else -> Unit
        }

        if (inReadyLobby || _state.value is LobbyState.Searching || _state.value is LobbyState.InviteSent) {
            activeMatchId = ""
            MatchStore.clear()
            _state.value = LobbyState.Idle
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        observerJob?.cancel()
        countdownJob?.cancel()
        if (_state.value is LobbyState.Starting) {
            super.onCleared()
            return
        }
        val matchId = activeMatchId.ifBlank { MatchStore.matchId }
        kotlinx.coroutines.runBlocking {
            when {
                isLobbyPhase() && matchId.isNotBlank() ->
                    runCatching { matchRepository.leavePreGameLobby(matchId) }
                _state.value is LobbyState.Searching || _state.value is LobbyState.InviteSent ->
                    runCatching { matchRepository.cancelQueue() }
            }
        }
        super.onCleared()
    }
}
