package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val currentInput: List<SkockoSymbol> = emptyList(),
    val secondsLeft: Int = 30,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val roundSolved: Boolean = false,
    val showSolution: Boolean = false
)

// ─── ViewModel ──────────────────────────────────────────────────────────────

class SkockoViewModel : ViewModel() {

    private val _state = MutableStateFlow(SkockoState())
    val state: StateFlow<SkockoState> = _state.asStateFlow()

    fun startRound() {
        _state.update { s ->
            s.copy(
                phase = SkockoPhase.PLAYER_TURN,
                solution = generateSolution(),
                attempts = emptyList(),
                currentInput = emptyList(),
                secondsLeft = 30,
                roundSolved = false,
                showSolution = false,
                activePlayerIsP1 = s.currentRound == 1
            )
        }
    }

    fun addSymbol(symbol: SkockoSymbol) {
        _state.update { s ->
            if (s.currentInput.size < 4) s.copy(currentInput = s.currentInput + symbol)
            else s
        }
    }

    fun removeSymbolAt(index: Int) {
        _state.update { s ->
            val list = s.currentInput.toMutableList()
            if (index in list.indices) list.removeAt(index)
            s.copy(currentInput = list)
        }
    }

    fun submitAttempt() {
        val s = _state.value
        if (s.currentInput.size != 4) return

        val (cp, cs) = computeFeedback(s.currentInput, s.solution)
        val attempt = SkockoAttempt(
            symbols = s.currentInput,
            correctPosition = cp,
            correctSymbol = cs,
            isOpponentAttempt = s.phase == SkockoPhase.OPPONENT_STEAL
        )
        val newAttempts = s.attempts + attempt
        val mainAttemptCount = newAttempts.count { !it.isOpponentAttempt }

        when {
            cp == 4 -> {
                // Pogođeno!
                val pts = if (s.phase == SkockoPhase.OPPONENT_STEAL) 10
                else pointsForAttempt(mainAttemptCount)
                val (p1, p2) = awardPoints(s, pts)
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = emptyList(),
                    roundSolved = true, showSolution = true,
                    phase = SkockoPhase.ROUND_END,
                    player1Points = p1, player2Points = p2
                )}
            }
            s.phase == SkockoPhase.OPPONENT_STEAL -> {
                // Krađa propuštena
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = emptyList(),
                    showSolution = true, phase = SkockoPhase.ROUND_END
                )}
            }
            mainAttemptCount >= 6 -> {
                // Ispucano 6 pokušaja → protivnik krade
                _state.update { it.copy(
                    attempts = newAttempts, currentInput = emptyList(),
                    phase = SkockoPhase.OPPONENT_STEAL, secondsLeft = 10
                )}
            }
            else -> {
                _state.update { it.copy(attempts = newAttempts, currentInput = emptyList()) }
            }
        }
    }

    fun skipOpponentSteal() {
        _state.update { it.copy(phase = SkockoPhase.ROUND_END, showSolution = true) }
    }

    fun nextRound() {
        val round = _state.value.currentRound
        if (round < 2) {
            _state.update { it.copy(
                currentRound = 2, phase = SkockoPhase.ROUND_INTRO,
                attempts = emptyList(), currentInput = emptyList(), showSolution = false
            )}
        } else {
            _state.update { it.copy(phase = SkockoPhase.GAME_OVER) }
        }
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
}