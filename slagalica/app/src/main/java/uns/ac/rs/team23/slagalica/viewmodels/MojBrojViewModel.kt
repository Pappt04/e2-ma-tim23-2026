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
import uns.ac.rs.team23.slagalica.models.MojBrojPuzzle
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.utils.ExpressionEvaluator
import kotlin.math.abs

enum class MojBrojPhase {
    RoundIntro,
    TargetCountdown,
    NumbersCountdown,
    Player1Input,
    Player2Input,
    RoundEnd,
    GameOver,
}

sealed class ExprToken {
    data class Num(val value: Int, val sourceIndex: Int) : ExprToken()
    data class Op(val symbol: String) : ExprToken()
    data object OpenParen : ExprToken()
    data object CloseParen : ExprToken()

    fun display(): String = when (this) {
        is Num -> value.toString()
        is Op -> symbol
        OpenParen -> "("
        CloseParen -> ")"
    }
}

data class MojBrojState(
    val currentRound: Int = 1,
    val phase: MojBrojPhase = MojBrojPhase.RoundIntro,
    val targetNumber: Int = 0,
    val drawnNumbers: List<Int> = emptyList(),
    val tokens: List<ExprToken> = emptyList(),
    val usedIndices: Set<Int> = emptySet(),
    val player1Answer: Int? = null,
    val player2Answer: Int? = null,
    val player1Expression: String = "",
    val player2Expression: String = "",
    val setupSecondsLeft: Int = 5,
    val playSecondsLeft: Int = 60,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val errorMessage: String? = null,
)

class MojBrojViewModel(
    private val gameRepository: GameRepository,
    private val matchRepository: uns.ac.rs.team23.slagalica.repository.MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MojBrojState())
    val state: StateFlow<MojBrojState> = _state.asStateFlow()
    private var timerJob: Job? = null

    /** Fetched puzzle cached until round ends. */
    private var pendingPuzzle: MojBrojPuzzle? = null

    fun startRound() {
        _state.update { it.copy(phase = MojBrojPhase.TargetCountdown, setupSecondsLeft = 5) }
        prefetchPuzzle()
        startSetupTimer()
    }

    private fun prefetchPuzzle() {
        viewModelScope.launch {
            gameRepository.getMojBrojPuzzle()
                .onSuccess { pendingPuzzle = it }
                .onFailure { err -> _state.update { it.copy(errorMessage = err.message) } }
        }
    }

    fun onStopPressed() {
        when (_state.value.phase) {
            MojBrojPhase.TargetCountdown -> {
                timerJob?.cancel()
                val target = pendingPuzzle?.targetNumber ?: (100..999).random()
                _state.update {
                    it.copy(
                        phase = MojBrojPhase.NumbersCountdown,
                        targetNumber = target,
                        setupSecondsLeft = 5,
                    )
                }
                startSetupTimer()
            }

            MojBrojPhase.NumbersCountdown -> {
                timerJob?.cancel()
                val numbers = pendingPuzzle?.numbers ?: drawNumbersFallback()
                _state.update {
                    it.copy(
                        phase = MojBrojPhase.Player1Input,
                        drawnNumbers = numbers,
                        playSecondsLeft = 60,
                        tokens = emptyList(),
                        usedIndices = emptySet(),
                    )
                }
                startPlayTimer()
            }

            else -> {}
        }
    }

    fun appendToken(token: ExprToken) {
        val s = _state.value
        if (s.phase != MojBrojPhase.Player1Input && s.phase != MojBrojPhase.Player2Input) return
        if (token is ExprToken.Num && token.sourceIndex in s.usedIndices) return
        val newUsed = if (token is ExprToken.Num) s.usedIndices + token.sourceIndex else s.usedIndices
        _state.update { it.copy(tokens = it.tokens + token, usedIndices = newUsed) }
    }

    fun deleteLast() {
        val s = _state.value
        val last = s.tokens.lastOrNull() ?: return
        val newUsed = if (last is ExprToken.Num) s.usedIndices - last.sourceIndex else s.usedIndices
        _state.update { it.copy(tokens = it.tokens.dropLast(1), usedIndices = newUsed) }
    }

    fun clearExpression() {
        _state.update { it.copy(tokens = emptyList(), usedIndices = emptySet()) }
    }

    fun submitExpression() {
        val s = _state.value
        if (s.phase != MojBrojPhase.Player1Input && s.phase != MojBrojPhase.Player2Input) return
        timerJob?.cancel()
        val exprString = if (s.tokens.isEmpty()) "(no entry)" else s.tokens.joinToString(" ") { it.display() }
        val result = if (s.tokens.isEmpty()) null else ExpressionEvaluator.evaluate(s.tokens)

        when (s.phase) {
            MojBrojPhase.Player1Input -> {
                _state.update {
                    it.copy(
                        phase = MojBrojPhase.Player2Input,
                        player1Answer = result,
                        player1Expression = exprString,
                        tokens = emptyList(),
                        usedIndices = emptySet(),
                        playSecondsLeft = 60,
                    )
                }
                startPlayTimer()
            }

            MojBrojPhase.Player2Input -> {
                _state.update { it.copy(player2Answer = result, player2Expression = exprString) }
                resolveRound()
            }

            else -> {}
        }
    }

    fun prepareNextRound() {
        pendingPuzzle = null
        _state.update {
            it.copy(
                currentRound = 2,
                phase = MojBrojPhase.RoundIntro,
                targetNumber = 0,
                drawnNumbers = emptyList(),
                tokens = emptyList(),
                usedIndices = emptySet(),
                player1Answer = null,
                player2Answer = null,
                player1Expression = "",
                player2Expression = "",
                errorMessage = null,
            )
        }
    }

    private fun startSetupTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (i in 4 downTo 0) {
                delay(1000)
                _state.update { it.copy(setupSecondsLeft = i) }
            }
            onStopPressed()
        }
    }

    private fun startPlayTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (i in 59 downTo 0) {
                delay(1000)
                _state.update { it.copy(playSecondsLeft = i) }
            }
            submitExpression()
        }
    }

    private fun drawNumbersFallback(): List<Int> {
        val singles = (1..9).shuffled().take(4)
        val medium = listOf(10, 15, 20).random()
        val large = listOf(25, 50, 75, 100).random()
        return singles + medium + large
    }

    private fun resolveRound() {
        val s = _state.value
        val target = s.targetNumber
        val activeIsP1 = s.currentRound == 1
        val activeResult = if (activeIsP1) s.player1Answer else s.player2Answer
        val opponentResult = if (activeIsP1) s.player2Answer else s.player1Answer

        val activeDiff = activeResult?.let { abs(it - target) }
        val opponentDiff = opponentResult?.let { abs(it - target) }

        val (activeDelta, opponentDelta) = when {
            activeDiff == 0 -> 10 to 0
            opponentDiff == 0 -> 0 to 10
            activeDiff == null && opponentDiff == null -> 0 to 0
            activeDiff == null -> 0 to 5
            opponentDiff == null -> 5 to 0
            activeDiff <= opponentDiff -> 5 to 0
            else -> 0 to 5
        }

        val (newP1, newP2) = if (activeIsP1) {
            (s.player1Points + activeDelta) to (s.player2Points + opponentDelta)
        } else {
            (s.player1Points + opponentDelta) to (s.player2Points + activeDelta)
        }

        _state.update {
            it.copy(
                phase = if (s.currentRound == 1) MojBrojPhase.RoundEnd else MojBrojPhase.GameOver,
                player1Points = newP1,
                player2Points = newP2,
            )
        }

        if (s.currentRound == 2) {
            submitMatchScore(newP1)
        }
    }

    private fun submitMatchScore(score: Int) {
        val matchId = uns.ac.rs.team23.slagalica.data.MatchStore.matchId
        if (matchId.isBlank()) return
        viewModelScope.launch {
            matchRepository.submitScore(matchId, score)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
