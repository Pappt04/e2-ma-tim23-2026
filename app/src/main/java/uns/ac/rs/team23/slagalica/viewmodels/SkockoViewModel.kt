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

// ─── Simboli ───────────────────────────────────────────────────────────────

enum class SkockoSymbol(val label: String, val hexColor: Long) {
    PIK    ("♠", 0xFF000000L),
    KARO   ("♦", 0xFFD32F2FL),
    TREF   ("♣", 0xFF000000L),
    SRCE   ("♥", 0xFFD32F2FL),
    ZVEZDA ("★", 0xFFFFC107L),
    SKOCKO ("SK", 0xFF000000L)
}

// ─── State modeli ───────────────────────────────────────────────────────────

data class SkockoAttempt(
    val symbols: List<SkockoSymbol>,
    val correctPosition: Int,          // tačan simbol, tačna pozicija (crni)
    val correctSymbol: Int,            // tačan simbol, pogrešna pozicija (beli)
    val isOpponentAttempt: Boolean = false
)

enum class SkockoPhase {
    ROUND_INTRO,
    PLAYER_TURN,       // aktivni igrač – do 6 pokušaja
    OPPONENT_STEAL,    // protivnik ima 1 pokušaj (10s)
    ROUND_END,
    GAME_OVER
}

data class SkockoState(
    val phase: SkockoPhase = SkockoPhase.ROUND_INTRO,
    val currentRound: Int = 1,
    val activePlayerIsP1: Boolean = true,
    val solution: List<SkockoSymbol> = emptyList(),
    val attempts: List<SkockoAttempt> = emptyList(),
    val currentInput: List<SkockoSymbol?> = List(4) { null },
    val secondsLeft: Int = 30,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val roundSolved: Boolean = false,
    val showSolution: Boolean = false,
    val awaitingRoundEndConfirm: Boolean = false
)

// ─── ViewModel ──────────────────────────────────────────────────────────────

class SkockoViewModel : ViewModel() {

    private val _state = MutableStateFlow(SkockoState())
    val state: StateFlow<SkockoState> = _state.asStateFlow()
    private var timerJob: Job? = null

    fun startRound() {
        _state.update { s ->
            s.copy(
                phase = SkockoPhase.PLAYER_TURN,
                solution = generateSolution(),
                attempts = emptyList(),
                currentInput = List(4) { null },
                secondsLeft = 30,
                roundSolved = false,
                showSolution = false,
                awaitingRoundEndConfirm = false,
                activePlayerIsP1 = s.currentRound == 1
            )
        }
        startTimer(30) {
            handlePlayerTurnTimeout()
        }
    }

    fun addSymbol(symbol: SkockoSymbol) {
        _state.update { s ->
            val targetIndex = s.currentInput.indexOfFirst { it == null }
            if (targetIndex == -1) {
                s
            } else {
                val updated = s.currentInput.toMutableList()
                updated[targetIndex] = symbol
                s.copy(currentInput = updated)
            }
        }
    }

    fun removeSymbolAt(index: Int) {
        _state.update { s ->
            if (index !in s.currentInput.indices) return@update s
            val updated = s.currentInput.toMutableList()
            updated[index] = null
            s.copy(currentInput = updated)
        }
    }

    fun submitAttempt() {
        val s = _state.value
        if (s.currentInput.any { it == null }) return
        val guess = s.currentInput.filterNotNull()

        val (cp, cs) = computeFeedback(guess, s.solution)
        val attempt = SkockoAttempt(
            symbols = guess,
            correctPosition = cp,
            correctSymbol = cs,
            isOpponentAttempt = s.phase == SkockoPhase.OPPONENT_STEAL
        )
        val newAttempts = s.attempts + attempt
        val mainAttemptCount = newAttempts.count { !it.isOpponentAttempt }

        when {
            cp == 4 -> {
                // Pogođeno!
                timerJob?.cancel()
                val pts = if (s.phase == SkockoPhase.OPPONENT_STEAL) 10
                else pointsForAttempt(mainAttemptCount)
                val (p1, p2) = awardPoints(s, pts)
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = List(4) { null },
                    roundSolved = true, showSolution = true,
                    player1Points = p1, player2Points = p2,
                    awaitingRoundEndConfirm = true
                )}
            }
            s.phase == SkockoPhase.OPPONENT_STEAL -> {
                // Krađa propuštena
                timerJob?.cancel()
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = List(4) { null },
                    showSolution = true,
                    phase = SkockoPhase.ROUND_END,
                    awaitingRoundEndConfirm = false
                )}
            }
            mainAttemptCount >= 6 -> {
                // Ispucano 6 pokušaja → protivnik krade
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = List(4) { null },
                    phase = SkockoPhase.OPPONENT_STEAL, secondsLeft = 10
                )}
                startTimer(10) {
                    endStealByTimeout()
                }
            }
            else -> {
                _state.update { it.copy(attempts = newAttempts, currentInput = List(4) { null }) }
            }
        }
    }

    fun nextRound() {
        val round = _state.value.currentRound
        timerJob?.cancel()
        if (round < 2) {
            _state.update { it.copy(
                currentRound = 2, phase = SkockoPhase.ROUND_INTRO,
                attempts = emptyList(),
                currentInput = List(4) { null },
                showSolution = false,
                awaitingRoundEndConfirm = false
            )}
        } else {
            _state.update { it.copy(phase = SkockoPhase.GAME_OVER) }
        }
    }

    fun confirmRoundEnd() {
        if (!_state.value.awaitingRoundEndConfirm) return
        _state.update { it.copy(phase = SkockoPhase.ROUND_END, awaitingRoundEndConfirm = false) }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun generateSolution(): List<SkockoSymbol> =
        List(4) { SkockoSymbol.values().random() }

    private fun computeFeedback(
        guess: List<SkockoSymbol>,
        solution: List<SkockoSymbol>
    ): Pair<Int, Int> {
        var correctPos = 0
        val solCount = solution.groupingBy { it }.eachCount().toMutableMap()
        val remaining = mutableListOf<SkockoSymbol>()

        guess.forEachIndexed { i, sym ->
            if (sym == solution[i]) {
                correctPos++
                solCount[sym] = solCount[sym]!! - 1
            } else {
                remaining.add(sym)
            }
        }
        var correctSym = 0
        for (sym in remaining) {
            if ((solCount[sym] ?: 0) > 0) {
                correctSym++
                solCount[sym] = solCount[sym]!! - 1
            }
        }
        return Pair(correctPos, correctSym)
    }

    private fun pointsForAttempt(attemptNum: Int) = when (attemptNum) {
        1, 2 -> 20
        3, 4 -> 15
        else -> 10
    }

    private fun awardPoints(s: SkockoState, pts: Int): Pair<Int, Int> {
        val stealActive = s.phase == SkockoPhase.OPPONENT_STEAL
        val p1Gets = (!stealActive && s.activePlayerIsP1) || (stealActive && !s.activePlayerIsP1)
        return if (p1Gets) Pair(s.player1Points + pts, s.player2Points)
        else Pair(s.player1Points, s.player2Points + pts)
    }

    private fun startTimer(durationSeconds: Int, onTimeout: () -> Unit) {
        timerJob?.cancel()
        _state.update { it.copy(secondsLeft = durationSeconds) }
        timerJob = viewModelScope.launch {
            while (_state.value.secondsLeft > 0 &&
                (_state.value.phase == SkockoPhase.PLAYER_TURN || _state.value.phase == SkockoPhase.OPPONENT_STEAL)
            ) {
                delay(1000L)
                _state.update { it.copy(secondsLeft = (it.secondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_state.value.secondsLeft == 0) onTimeout()
        }
    }

    private fun handlePlayerTurnTimeout() {
        if (_state.value.phase != SkockoPhase.PLAYER_TURN) return
        _state.update {
            it.copy(
                phase = SkockoPhase.OPPONENT_STEAL,
                currentInput = List(4) { null },
                secondsLeft = 10
            )
        }
        startTimer(10) {
            endStealByTimeout()
        }
    }

    private fun endStealByTimeout() {
        if (_state.value.phase != SkockoPhase.OPPONENT_STEAL) return
        _state.update {
            it.copy(
                phase = SkockoPhase.ROUND_END,
                showSolution = true,
                currentInput = List(4) { null },
                awaitingRoundEndConfirm = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}