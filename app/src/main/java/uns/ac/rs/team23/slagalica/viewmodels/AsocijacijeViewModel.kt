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

// ─── Faze igre ──────────────────────────────────────────────────────────────

enum class AsocijacijePhase {
    ROUND_INTRO,
    PLAYING,
    ROUND_END,
    GAME_OVER
}

// ─── Model jedne kolone ──────────────────────────────────────────────────────

data class AsocijacijeColumn(
    val words: List<String>,                             // 4 reči/fraze
    val answer: String,                                  // rešenje kolone
    val revealedFields: List<Boolean> = List(4) { false },
    val isSolved: Boolean = false
)

sealed interface GuessTarget {
    data class Column(val index: Int) : GuessTarget
    data object Final : GuessTarget
}

// ─── Glavni state ────────────────────────────────────────────────────────────

data class AsocijacijeState(
    val phase: AsocijacijePhase = AsocijacijePhase.ROUND_INTRO,
    val currentRound: Int = 1,
    val columns: List<AsocijacijeColumn> = emptyList(),
    val finalAnswer: String = "",
    val isFinalSolved: Boolean = false,
    val activePlayer: Int = 1,           // 1 ili 2
    val secondsLeft: Int = 120,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val guessInput: String = "",
    val waitingForGuess: Boolean = false,  // posle otkrivanja, igrač može da pogađa
    val selectedGuessTarget: GuessTarget? = null,
    val wrongGuessTarget: GuessTarget? = null
)

// ─── Mock pitanja ────────────────────────────────────────────────────────────

private data class AsocijacijePuzzle(
    val columns: List<AsocijacijeColumn>,
    val finalAnswer: String
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class AsocijacijeViewModel : ViewModel() {

    private val _state = MutableStateFlow(AsocijacijeState())
    val state: StateFlow<AsocijacijeState> = _state.asStateFlow()

    private var timerJob: Job? = null

    private val puzzles = listOf(
        AsocijacijePuzzle(
            columns = listOf(
                AsocijacijeColumn(
                    words = listOf("FUDBAL", "TRPEZARIJA", "SVEDSKA", "NOGA"),
                    answer = "STO"
                ),
                AsocijacijeColumn(
                    words = listOf("NAUKA", "FORMULA", "ANALIZA", "GIMNAZIJA"),
                    answer = "MATEMATIKA"
                ),
                AsocijacijeColumn(
                    words = listOf("ARMIKA", "ROK", "DOM", "POLICIJA"),
                    answer = "VOJSKA"
                ),
                AsocijacijeColumn(
                    words = listOf("RUKAVICE", "KAPA", "IGLA", "KARDIO"),
                    answer = "HIRURG"
                )
            ),
            finalAnswer = "OPERACIJA"
        ),
        AsocijacijePuzzle(
            columns = listOf(
                AsocijacijeColumn(
                    words = listOf("PATIKE", "MUZIKA", "PANDORA", "CRNA"),
                    answer = "KUTIJA"
                ),
                AsocijacijeColumn(
                    words = listOf("TRKA", "LUDI", "REP", "TROJA"),
                    answer = "KONJ"
                ),
                AsocijacijeColumn(
                    words = listOf("RADIONICA", "MAJSTOR", "PIVO", "SKOLA"),
                    answer = "ZANAT"
                ),
                AsocijacijeColumn(
                    words = listOf("CILJ", "CISCENJE", "SMIRENJE", "PLACANJE"),
                    answer = "CILJ"
                )
            ),
            finalAnswer = "ALAT"
        )
    )

    fun startRound() {
        val puzzle = puzzles[(_state.value.currentRound - 1).coerceIn(0, puzzles.size - 1)]
        _state.update { s ->
            s.copy(
                phase = AsocijacijePhase.PLAYING,
                columns = puzzle.columns,
                finalAnswer = puzzle.finalAnswer,
                isFinalSolved = false,
                activePlayer = if (s.currentRound == 1) 1 else 2,
                secondsLeft = 120,
                guessInput = "",
                waitingForGuess = false,
                selectedGuessTarget = null,
                wrongGuessTarget = null
            )
        }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.secondsLeft > 0 && _state.value.phase == AsocijacijePhase.PLAYING) {
                delay(1000L)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            if (_state.value.phase == AsocijacijePhase.PLAYING) {
                endRound()
            }
        }
    }

    // Igrač klikne na polje da ga otkrije
    fun revealField(col: Int, row: Int) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING) return
        if (s.waitingForGuess) return       // mora da pogodi ili da proda pre otkrivanja
        if (col !in s.columns.indices) return
        val column = s.columns[col]
        if (column.revealedFields[row] || column.isSolved) return

        val newRevealed = column.revealedFields.toMutableList().also { it[row] = true }
        val newColumns = s.columns.toMutableList().also {
            it[col] = column.copy(revealedFields = newRevealed)
        }
        _state.update {
            it.copy(
                columns = newColumns,
                waitingForGuess = true,
                guessInput = "",
                selectedGuessTarget = null,
                wrongGuessTarget = null
            )
        }
    }

    fun selectGuessTarget(target: GuessTarget) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || !canGuessNow(s)) return
        when (target) {
            is GuessTarget.Column -> {
                val col = s.columns.getOrNull(target.index) ?: return
                if (col.isSolved) return
            }
            GuessTarget.Final -> {
                if (s.isFinalSolved) return
            }
        }
        _state.update { it.copy(selectedGuessTarget = target, guessInput = "", wrongGuessTarget = null) }
    }

    fun onGuessChange(text: String) {
        _state.update { it.copy(guessInput = text.uppercase(), wrongGuessTarget = null) }
    }

    fun submitGuess() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING) return
        if (!canGuessNow(s)) return
        val selectedTarget = s.selectedGuessTarget ?: return
        val guess = s.guessInput.trim()
        if (guess.isEmpty()) return

        when (selectedTarget) {
            is GuessTarget.Column -> {
                val targetColumn = s.columns.getOrNull(selectedTarget.index) ?: return
                if (targetColumn.isSolved) return
                if (guess.equals(targetColumn.answer, ignoreCase = true)) {
                    solveColumn(selectedTarget.index)
                    return
                }
            }
            GuessTarget.Final -> {
                if (!s.isFinalSolved && guess.equals(s.finalAnswer, ignoreCase = true)) {
                    solveFinal()
                    return
                }
            }
        }

        _state.update {
            it.copy(
                wrongGuessTarget = selectedTarget,
                waitingForGuess = false,
                guessInput = "",
                selectedGuessTarget = null
            )
        }
        viewModelScope.launch {
            delay(1000L)
            if (_state.value.phase == AsocijacijePhase.PLAYING) {
                _state.update { current -> current.copy(wrongGuessTarget = null) }
                switchPlayer()
            } else {
                _state.update { current -> current.copy(wrongGuessTarget = null) }
            }
        }
    }

    // Igrač proda (ne želi da pogađa, prelazi red)
    fun passGuess() {
        _state.update {
            it.copy(
                waitingForGuess = false,
                guessInput = "",
                selectedGuessTarget = null,
                wrongGuessTarget = null
            )
        }
        switchPlayer()
    }

    private fun solveColumn(col: Int) {
        val s = _state.value
        val column = s.columns[col]
        val unrevealedCount = column.revealedFields.count { !it }
        val colPoints = 2 + unrevealedCount   // f) 2 boda + 1 bod za svako neotvoreno

        val newColumns = s.columns.toMutableList().also {
            it[col] = column.copy(isSolved = true, revealedFields = List(4) { true })
        }
        val newP1 = if (s.activePlayer == 1) s.player1Points + colPoints else s.player1Points
        val newP2 = if (s.activePlayer == 2) s.player2Points + colPoints else s.player2Points

        _state.update {
            it.copy(
                columns = newColumns,
                player1Points = newP1,
                player2Points = newP2,
                guessInput = "",
                waitingForGuess = true,
                selectedGuessTarget = null,
                wrongGuessTarget = null
            )
        }
        if (newColumns.all { it.isSolved }) endRound()
    }

    private fun solveFinal() {
        val s = _state.value
        // g) 7 + 6 za svaku neotvorenu kolonu + bodovi za otvorene
        var points = 7
        for (col in s.columns) {
            if (!col.isSolved) {
                val anyRevealed = col.revealedFields.any { it }
                if (!anyRevealed) {
                    points += 6  // potpuno neotvorena kolona
                } else {
                    points += 2 + col.revealedFields.count { !it }  // delimično otvorena
                }
            }
        }

        val newP1 = if (s.activePlayer == 1) s.player1Points + points else s.player1Points
        val newP2 = if (s.activePlayer == 2) s.player2Points + points else s.player2Points

        _state.update {
            it.copy(
                isFinalSolved = true,
                player1Points = newP1,
                player2Points = newP2,
                guessInput = "",
                selectedGuessTarget = null,
                wrongGuessTarget = null
            )
        }
        endRound()
    }

    private fun switchPlayer() {
        _state.update { it.copy(activePlayer = if (it.activePlayer == 1) 2 else 1) }
    }

    private fun endRound() {
        timerJob?.cancel()
        if (_state.value.currentRound >= 2) {
            _state.update { it.copy(phase = AsocijacijePhase.GAME_OVER) }
        } else {
            _state.update { it.copy(phase = AsocijacijePhase.ROUND_END) }
        }
    }

    fun nextRound() {
        _state.update { it.copy(currentRound = 2, phase = AsocijacijePhase.ROUND_INTRO) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private fun canGuessNow(state: AsocijacijeState): Boolean {
        if (state.waitingForGuess) return true
        return !hasRevealableFields(state)
    }

    private fun hasRevealableFields(state: AsocijacijeState): Boolean {
        return state.columns.any { column ->
            !column.isSolved && column.revealedFields.any { revealed -> !revealed }
        }
    }
}