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
import uns.ac.rs.team23.slagalica.models.AsocijacijeColumnData
import uns.ac.rs.team23.slagalica.models.AsocijacijeQuestion
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.repository.MatchRepository

enum class AsocijacijePhase {
    ROUND_INTRO,
    LOADING,
    PLAYING,
    ROUND_END,
    GAME_OVER,
}

data class AsocijacijeColumn(
    val words: List<String>,
    val answer: String,
    val revealedFields: List<Boolean> = List(4) { false },
    val isSolved: Boolean = false,
)

sealed interface GuessTarget {
    data class Column(val index: Int) : GuessTarget
    data object Final : GuessTarget
}

data class AsocijacijeState(
    val phase: AsocijacijePhase = AsocijacijePhase.ROUND_INTRO,
    val currentRound: Int = 1,
    val columns: List<AsocijacijeColumn> = emptyList(),
    val finalAnswer: String = "",
    val isFinalSolved: Boolean = false,
    val activePlayer: Int = 1,
    val secondsLeft: Int = 120,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val guessInput: String = "",
    val waitingForGuess: Boolean = false,
    val selectedGuessTarget: GuessTarget? = null,
    val wrongGuessTarget: GuessTarget? = null,
    val errorMessage: String? = null,
)

class AsocijacijeViewModel(
    private val gameRepository: GameRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AsocijacijeState())
    val state: StateFlow<AsocijacijeState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var loadedQuestion: AsocijacijeQuestion? = null

    init {
        preloadQuestion()
    }

    private fun preloadQuestion() {
        viewModelScope.launch {
            gameRepository.getAsocijacijeQuestion()
                .onSuccess { loadedQuestion = it }
                .onFailure { _state.update { s -> s.copy(errorMessage = it.message) } }
        }
    }

    fun startRound() {
        val question = loadedQuestion
        if (question == null) {
            _state.update { it.copy(phase = AsocijacijePhase.LOADING) }
            viewModelScope.launch {
                gameRepository.getAsocijacijeQuestion()
                    .onSuccess { q ->
                        loadedQuestion = q
                        applyQuestion(q)
                    }
                    .onFailure { err ->
                        _state.update { it.copy(phase = AsocijacijePhase.ROUND_INTRO, errorMessage = err.message) }
                    }
            }
            return
        }
        applyQuestion(question)
    }

    private fun applyQuestion(question: AsocijacijeQuestion) {
        _state.update { s ->
            s.copy(
                phase = AsocijacijePhase.PLAYING,
                columns = question.columns.map { AsocijacijeColumn(words = it.words, answer = it.answer) },
                finalAnswer = question.finalAnswer,
                isFinalSolved = false,
                activePlayer = if (s.currentRound == 1) 1 else 2,
                secondsLeft = 120,
                guessInput = "",
                waitingForGuess = false,
                selectedGuessTarget = null,
                wrongGuessTarget = null,
                errorMessage = null,
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
            if (_state.value.phase == AsocijacijePhase.PLAYING) endRound()
        }
    }

    fun revealField(col: Int, row: Int) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING) return
        if (s.waitingForGuess) return
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
                wrongGuessTarget = null,
            )
        }
    }

    fun selectGuessTarget(target: GuessTarget) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || !canGuessNow(s)) return
        when (target) {
            is GuessTarget.Column -> if (s.columns.getOrNull(target.index)?.isSolved == true) return
            GuessTarget.Final -> if (s.isFinalSolved) return
        }
        _state.update { it.copy(selectedGuessTarget = target, guessInput = "", wrongGuessTarget = null) }
    }

    fun onGuessChange(text: String) {
        _state.update { it.copy(guessInput = text.uppercase(), wrongGuessTarget = null) }
    }

    fun submitGuess() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || !canGuessNow(s)) return
        val target = s.selectedGuessTarget ?: return
        val guess = s.guessInput.trim()
        if (guess.isEmpty()) return

        when (target) {
            is GuessTarget.Column -> {
                val col = s.columns.getOrNull(target.index) ?: return
                if (col.isSolved) return
                if (guess.equals(col.answer, ignoreCase = true)) {
                    solveColumn(target.index)
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
                wrongGuessTarget = target,
                waitingForGuess = false,
                guessInput = "",
                selectedGuessTarget = null,
            )
        }
        viewModelScope.launch {
            delay(1000L)
            if (_state.value.phase == AsocijacijePhase.PLAYING) {
                _state.update { it.copy(wrongGuessTarget = null) }
                switchPlayer()
            } else {
                _state.update { it.copy(wrongGuessTarget = null) }
            }
        }
    }

    fun passGuess() {
        _state.update {
            it.copy(
                waitingForGuess = false,
                guessInput = "",
                selectedGuessTarget = null,
                wrongGuessTarget = null,
            )
        }
        switchPlayer()
    }

    private fun solveColumn(col: Int) {
        val s = _state.value
        val column = s.columns[col]
        val unrevealedCount = column.revealedFields.count { !it }
        val colPoints = 2 + unrevealedCount

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
                wrongGuessTarget = null,
            )
        }
        if (newColumns.all { it.isSolved }) endRound()
    }

    private fun solveFinal() {
        val s = _state.value
        var points = 7
        for (col in s.columns) {
            if (!col.isSolved) {
                val anyRevealed = col.revealedFields.any { it }
                points += if (!anyRevealed) 6 else 2 + col.revealedFields.count { !it }
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
                wrongGuessTarget = null,
            )
        }
        endRound()
    }

    private fun switchPlayer() {
        _state.update { it.copy(activePlayer = if (it.activePlayer == 1) 2 else 1) }
    }

    private fun endRound() {
        timerJob?.cancel()
        val s = _state.value
        if (s.currentRound >= 2) {
            _state.update { it.copy(phase = AsocijacijePhase.GAME_OVER) }
            submitMatchScore(s.player1Points)
        } else {
            _state.update { it.copy(phase = AsocijacijePhase.ROUND_END) }
        }
    }

    fun nextRound() {
        _state.update { it.copy(currentRound = 2, phase = AsocijacijePhase.ROUND_INTRO) }
        loadedQuestion = null
        preloadQuestion()
    }

    private fun submitMatchScore(score: Int) {
        val matchId = MatchStore.matchId
        if (matchId.isBlank()) return
        viewModelScope.launch {
            matchRepository.submitScore(matchId, score)
        }
    }

    private fun canGuessNow(state: AsocijacijeState): Boolean {
        if (state.waitingForGuess) return true
        return !state.columns.any { col -> !col.isSolved && col.revealedFields.any { !it } }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
