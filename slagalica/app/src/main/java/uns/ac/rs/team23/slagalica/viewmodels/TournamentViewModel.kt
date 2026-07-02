package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.data.TournamentStore
import uns.ac.rs.team23.slagalica.models.DailyMissionType
import uns.ac.rs.team23.slagalica.network.dto.TournamentDto
import uns.ac.rs.team23.slagalica.repository.DailyMissionRepository
import uns.ac.rs.team23.slagalica.repository.TournamentMatchResult
import uns.ac.rs.team23.slagalica.repository.TournamentRepository

/** A participant shown on the matchmaking / ready / bracket screens (spec 10.f). */
data class TournamentPlayer(
    val uid: String,
    val username: String,
    val avatarIndex: Int,
    val leagueLevel: Int,
    val ready: Boolean = false,
)

/** Visual bracket snapshot derived from the live tournament document. */
data class TournamentBracketUi(
    val semi1: List<TournamentPlayer?>,
    val semi2: List<TournamentPlayer?>,
    val semi1Winner: TournamentPlayer?,
    val semi2Winner: TournamentPlayer?,
    val finalWinner: TournamentPlayer?,
    /** True once the host has drawn random semifinal pairings. */
    val pairingsKnown: Boolean,
)

sealed class TournamentUiState {
    /** Joining the lobby / generic loading. */
    data object Joining : TournamentUiState()

    /** Post-match bookkeeping in flight (recording winner, applying reward). */
    data object Syncing : TournamentUiState()

    /** WAITING — fewer than four players have joined. */
    data class Searching(val players: List<TournamentPlayer>) : TournamentUiState()

    /** READY_CHECK — four players present, each must confirm ready. */
    data class ReadyCheck(val players: List<TournamentPlayer>, val iAmReady: Boolean) : TournamentUiState()

    /** Transient — match created, navigating into the game. */
    data object EnteringMatch : TournamentUiState()

    /** Just won a semifinal — a "you won this match" screen before waiting for the other match. */
    data class SemifinalWon(val reward: TournamentMatchResult?) : TournamentUiState()

    /** Won the semifinal, the other semifinal is still being played. */
    data object WaitingForOther : TournamentUiState()

    /** Both finalists known — each confirms ready before the final. */
    data class FinalReadyCheck(val finalists: List<TournamentPlayer>, val iAmReady: Boolean) : TournamentUiState()

    /** Lost a semifinal — out of the tournament. */
    data class Eliminated(val reward: TournamentMatchResult?) : TournamentUiState()

    /** Won the final. */
    data class Victory(val reward: TournamentMatchResult?) : TournamentUiState()

    /** Lost the final. */
    data class Defeat(val reward: TournamentMatchResult?) : TournamentUiState()

    data class Error(val message: String) : TournamentUiState()
}

class TournamentViewModel(
    private val repo: TournamentRepository,
    private val missions: DailyMissionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TournamentUiState>(TournamentUiState.Joining)
    val state: StateFlow<TournamentUiState> = _state.asStateFlow()

    private val _bracket = MutableStateFlow<TournamentBracketUi?>(null)
    val bracket: StateFlow<TournamentBracketUi?> = _bracket.asStateFlow()

    /** Emitted when the player should navigate into the game screen (MatchStore already set). */
    private val _navigateToGame = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToGame: SharedFlow<Unit> = _navigateToGame.asSharedFlow()

    private var myUid: String? = null
    private var tournamentId: String = ""
    private var started = false

    private var observeJob: Job? = null
    private var timeoutJob: Job? = null

    /** Matches this VM instance has already played (so [render] never re-enters them). */
    private val playedMatchIds = mutableSetOf<String>()
    private var navigatedToMatchId: String? = null
    private var bracketRequested = false
    private var finalRequested = false
    private var lastResult: TournamentMatchResult? = null

    /** After a semifinal win, show the "you won this match" screen until the player taps Continue. */
    private var showSemifinalWon = false
    private var lastTournament: TournamentDto? = null

    fun enter() {
        if (started) return
        started = true
        myUid = repo.currentUserId()
        viewModelScope.launch {
            val resuming = TournamentStore.isActive
            val tid = if (resuming) {
                TournamentStore.tournamentId
            } else {
                _state.value = TournamentUiState.Joining
                repo.joinTournament().getOrElse {
                    _state.value = TournamentUiState.Error(it.message ?: "Failed to join tournament")
                    return@launch
                }
            }
            tournamentId = tid
            TournamentStore.set(tid)

            // Returned from a finished match — record the winner and apply our reward.
            val returning = TournamentStore.currentMatchId
            if (returning.isNotBlank()) {
                _state.value = TournamentUiState.Syncing
                processFinishedMatch(returning, TournamentStore.isFinalRound)
                TournamentStore.setCurrentMatch("", "")
            }

            startWaitingTimeout()
            observeJob = launch { repo.observeTournament(tid).collect(::render) }
        }
    }

    private fun render(t: TournamentDto) {
        val uid = myUid ?: return
        lastTournament = t
        _bracket.value = t.toBracketUi()
        when (t.status) {
            "WAITING" -> _state.value = TournamentUiState.Searching(t.cards(t.playerUids, t.readyUids))
            "READY_CHECK" -> {
                if (t.hostUid == uid && t.readyUids.size >= 4) requestBracket()
                _state.value = TournamentUiState.ReadyCheck(
                    players = t.cards(t.playerUids, t.readyUids),
                    iAmReady = uid in t.readyUids,
                )
            }
            "SEMIFINALS" -> renderSemifinals(t, uid)
            "FINAL" -> renderFinal(t, uid)
            "COMPLETED" -> _state.value = when {
                t.finalWinner == uid -> TournamentUiState.Victory(lastResult)
                uid in t.finalists -> TournamentUiState.Defeat(lastResult)
                else -> TournamentUiState.Eliminated(lastResult)
            }
            "CANCELLED" -> _state.value = TournamentUiState.Error("Tournament was cancelled.")
        }
    }

    private fun renderSemifinals(t: TournamentDto, uid: String) {
        val myMatchId = t.semifinalMatchId(uid)
        val myWinner = when (uid) {
            in t.semi1Uids -> t.semi1Winner
            in t.semi2Uids -> t.semi2Winner
            else -> null
        }
        when {
            myMatchId.isNullOrBlank() -> _state.value = TournamentUiState.Syncing
            myWinner == uid -> {
                if (showSemifinalWon) {
                    // "You won this match" screen first; the player taps Continue to proceed.
                    _state.value = TournamentUiState.SemifinalWon(lastResult)
                } else if (t.bothSemifinalsDecided) {
                    if (uid == t.semi1Winner && t.finalReadyUids.size >= 2) requestFinal()
                    _state.value = TournamentUiState.FinalReadyCheck(
                        finalists = t.cards(t.finalists, t.finalReadyUids),
                        iAmReady = uid in t.finalReadyUids,
                    )
                } else {
                    _state.value = TournamentUiState.WaitingForOther
                }
            }
            myWinner != null -> _state.value = TournamentUiState.Eliminated(lastResult) // someone else won my semifinal
            myMatchId in playedMatchIds -> _state.value = TournamentUiState.Syncing      // winner not yet recorded
            else -> {
                val pair = if (uid in t.semi1Uids) t.semi1Uids else t.semi2Uids
                enterMatch(t, myMatchId, pair, TournamentStore.ROUND_SEMIFINAL)
            }
        }
    }

    private fun renderFinal(t: TournamentDto, uid: String) {
        val finalMatchId = t.finalMatchId
        when {
            uid !in t.finalists -> _state.value = TournamentUiState.Eliminated(lastResult)
            t.finalWinner == uid -> _state.value = TournamentUiState.Victory(lastResult)
            t.finalWinner != null -> _state.value = TournamentUiState.Defeat(lastResult)
            finalMatchId.isNullOrBlank() -> _state.value = TournamentUiState.Syncing
            finalMatchId in playedMatchIds -> _state.value = TournamentUiState.Syncing
            else -> enterMatch(t, finalMatchId, t.finalists, TournamentStore.ROUND_FINAL)
        }
    }

    private fun enterMatch(t: TournamentDto, matchId: String, pair: List<String>, round: String) {
        if (matchId.isBlank() || navigatedToMatchId == matchId || pair.size < 2) return
        navigatedToMatchId = matchId
        val uid = myUid ?: return
        _state.value = TournamentUiState.EnteringMatch
        viewModelScope.launch {
            // Authoritative player ids/names/host come from the match doc (identical for both
            // players); fall back to the tournament doc only if the match read fails.
            val setup = repo.matchSetup(matchId)
            // The match may already be over (e.g., the opponent forfeited before we navigated in).
            // Never bounce into a finished game — record the result and advance instead.
            if (setup?.isCompleted == true) {
                _state.value = TournamentUiState.Syncing
                TournamentStore.setCurrentMatch(matchId, round)
                processFinishedMatch(matchId, round == TournamentStore.ROUND_FINAL)
                TournamentStore.setCurrentMatch("", "")
                lastTournament?.let { render(it) }
                return@launch
            }
            val p1 = setup?.player1Id?.takeIf { it.isNotBlank() } ?: pair[0]
            val p2 = setup?.player2Id?.takeIf { it.isNotBlank() } ?: pair[1]
            val p1Name = setup?.player1Name?.takeIf { it.isNotBlank() } ?: (t.player(p1)?.username ?: "")
            val p2Name = setup?.player2Name?.takeIf { it.isNotBlank() } ?: (t.player(p2)?.username ?: "")
            MatchStore.set(
                id = matchId,
                opponent = if (uid == p1) p2Name else p1Name,
                friendly = true,
                myUid = uid,
                hostId = p1,
                player1 = p1Name,
                player2 = p2Name,
                player1Id = p1,
                player2Id = p2,
            )
            TournamentStore.setCurrentMatch(matchId, round)
            _navigateToGame.emit(Unit)
        }
    }

    /** Record a finished match's winner + reward (idempotent), and remember we played it. */
    private suspend fun processFinishedMatch(matchId: String, isFinal: Boolean) {
        playedMatchIds.add(matchId)
        repo.finishTournamentMatch(tournamentId, matchId, isFinal).onSuccess { result ->
            lastResult = result
            if (result.iWon && !result.isFinal) showSemifinalWon = true
            if (result.iWon) missions.completeMission(DailyMissionType.WIN_TOURNAMENT)
        }
    }

    fun clickReady() {
        viewModelScope.launch {
            when (_state.value) {
                is TournamentUiState.ReadyCheck -> repo.markReady(tournamentId)
                is TournamentUiState.FinalReadyCheck -> repo.markFinalReady(tournamentId)
                else -> Unit
            }
        }
    }

    /** Dismiss the "you won this match" screen and move on to waiting / the final ready check. */
    fun continueFromSemifinal() {
        showSemifinalWon = false
        lastTournament?.let { render(it) }
    }

    private fun requestBracket() {
        if (bracketRequested) return
        bracketRequested = true
        viewModelScope.launch { repo.createBracketIfHost(tournamentId) }
    }

    private fun requestFinal() {
        if (finalRequested) return
        finalRequested = true
        viewModelScope.launch { repo.createFinalIfCreator(tournamentId) }
    }

    private fun startWaitingTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(120_000)
            if (_state.value is TournamentUiState.Searching) {
                repo.cancel(tournamentId)
                TournamentStore.clear()
                _state.value = TournamentUiState.Error("Not enough players joined. Try again later.")
            }
        }
    }

    /** User backs out before the tournament starts — leave the lobby and refund the fee. */
    fun cancel() {
        val tid = tournamentId
        viewModelScope.launch {
            if (tid.isNotBlank()) repo.cancel(tid)
            TournamentStore.clear()
            MatchStore.clear()
        }
    }

    /** Leave after being eliminated / the tournament is over (no refund). */
    fun leave() {
        TournamentStore.clear()
        MatchStore.clear()
    }

    override fun onCleared() {
        observeJob?.cancel()
        timeoutJob?.cancel()
        super.onCleared()
    }

    private fun TournamentDto.cards(uids: List<String>, readyUids: List<String>): List<TournamentPlayer> =
        uids.mapNotNull { uid ->
            player(uid)?.let {
                TournamentPlayer(it.uid, it.username, it.avatarIndex, it.leagueLevel, ready = uid in readyUids)
            }
        }

    private fun TournamentDto.toBracketUi(): TournamentBracketUi {
        fun card(uid: String?): TournamentPlayer? = uid?.let { u ->
            player(u)?.let { p ->
                TournamentPlayer(p.uid, p.username, p.avatarIndex, p.leagueLevel, ready = u in readyUids)
            }
        }

        val pairingsKnown = semi1Uids.size >= 2 && semi2Uids.size >= 2
        val semi1 = if (pairingsKnown) {
            listOf(card(semi1Uids.getOrNull(0)), card(semi1Uids.getOrNull(1)))
        } else {
            listOf(card(playerUids.getOrNull(0)), card(playerUids.getOrNull(1)))
        }
        val semi2 = if (pairingsKnown) {
            listOf(card(semi2Uids.getOrNull(0)), card(semi2Uids.getOrNull(1)))
        } else {
            listOf(card(playerUids.getOrNull(2)), card(playerUids.getOrNull(3)))
        }

        return TournamentBracketUi(
            semi1 = semi1,
            semi2 = semi2,
            semi1Winner = card(semi1Winner),
            semi2Winner = card(semi2Winner),
            finalWinner = card(finalWinner),
            pairingsKnown = pairingsKnown,
        )
    }
}
