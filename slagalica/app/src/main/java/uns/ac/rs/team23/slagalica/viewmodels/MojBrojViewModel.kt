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
import uns.ac.rs.team23.slagalica.network.dto.GameStateDto
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.repository.MatchRepository
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
    val iSubmitted: Boolean = false,
    val activeIsMe: Boolean = true,
)

/**
 * Real-time, host-authoritative "Moj broj" (turn-based archetype).
 *
 * The host (player1) generates the shared target + 6 numbers for each of the two rounds, owns the
 * 60-second deadline, and resolves the round from both players' real submitted results. Both
 * players build their own expression locally and submit it; the result is written to the shared
 * document so the opponent's real answer is used for scoring (no simulation). The round owner
 * ([turn]) gets the spec tie-break preference.
 */
class MojBrojViewModel(
    private val gameRepository: GameRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MojBrojState())
    val state: StateFlow<MojBrojState> = _state.asStateFlow()

    private var started = false
    private var matchId: String = ""
    private var isHost: Boolean = false

    private var observerJob: Job? = null
    private var loopJob: Job? = null

    private var latest: GameStateDto? = null
    private var renderedRound = -1
    private var lastResolvedRound = -1
    private var advancingNextRound = false

    fun enter() {
        if (started) return
        started = true
        matchId = MatchStore.matchId
        isHost = MatchStore.isHost

        if (matchId.isBlank()) {
            // No live match: present a local puzzle so the screen is usable in isolation.
            startLocalFallback()
            return
        }

        observerJob = viewModelScope.launch {
            matchRepository.observeGameState(matchId, GAME_TYPE).collect { gs ->
                latest = gs
                rebuildState()
            }
        }
        if (isHost) startRoundAsHost(round = 1, p1Score = 0, p2Score = 0)
        startLoop()
    }

    private fun startRoundAsHost(round: Int, p1Score: Int, p2Score: Int) {
        viewModelScope.launch {
            val puzzle = gameRepository.getMojBrojPuzzle().getOrNull()
            val target = puzzle?.targetNumber ?: (100..999).random()
            val numbers = puzzle?.numbers ?: drawNumbersFallback()
            matchRepository.setGameState(
                matchId, GAME_TYPE,
                mapOf(
                    "gameType" to GAME_TYPE,
                    "hostId" to MatchStore.hostId,
                    "phase" to "INPUT",
                    "round" to round,
                    "index" to 0,
                    "turn" to if (round == 1) "p1" else "p2",
                    "deadlineAt" to System.currentTimeMillis() + INPUT_MILLIS,
                    "payload" to mapOf("target" to target, "numbers" to numbers),
                    "p1Input" to emptyMap<String, Any?>(),
                    "p2Input" to emptyMap<String, Any?>(),
                    "p1Score" to p1Score,
                    "p2Score" to p2Score,
                ),
            )
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (true) {
                rebuildState()
                if (isHost) maybeResolve()
                delay(200)
            }
        }
    }

    // --- Local input ---

    fun appendToken(token: ExprToken) {
        val s = _state.value
        if (s.phase != MojBrojPhase.Player1Input || s.iSubmitted) return
        if (token is ExprToken.Num && token.sourceIndex in s.usedIndices) return
        val newUsed = if (token is ExprToken.Num) s.usedIndices + token.sourceIndex else s.usedIndices
        _state.update { it.copy(tokens = it.tokens + token, usedIndices = newUsed) }
    }

    fun deleteLast() {
        val s = _state.value
        if (s.iSubmitted) return
        val last = s.tokens.lastOrNull() ?: return
        val newUsed = if (last is ExprToken.Num) s.usedIndices - last.sourceIndex else s.usedIndices
        _state.update { it.copy(tokens = it.tokens.dropLast(1), usedIndices = newUsed) }
    }

    fun clearExpression() {
        if (_state.value.iSubmitted) return
        _state.update { it.copy(tokens = emptyList(), usedIndices = emptySet()) }
    }

    /** Submit my expression (button or shake). Writes my real result to the shared document. */
    fun submitExpression() {
        val gs = latest ?: return
        val s = _state.value
        if (gs.phase != "INPUT" || s.iSubmitted) return
        val result = if (s.tokens.isEmpty()) null else ExpressionEvaluator.evaluate(s.tokens)
        val exprString = if (s.tokens.isEmpty()) "(no entry)" else s.tokens.joinToString(" ") { it.display() }
        val field = if (isHost) "p1Input" else "p2Input"
        _state.update { it.copy(iSubmitted = true) }
        viewModelScope.launch {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf(
                    field to mapOf(
                        "submitted" to true,
                        "result" to result,
                        "expr" to exprString,
                    ),
                ),
            )
        }
    }

    // --- Host resolution ---

    private fun maybeResolve() {
        val gs = latest ?: return
        if (gs.phase != "INPUT") return
        if (gs.round <= lastResolvedRound) return

        val p1Submitted = gs.p1Input["submitted"] == true
        val p2Submitted = gs.p2Input["submitted"] == true
        val now = System.currentTimeMillis()
        val expired = gs.deadlineAt in 1..now
        if (!expired && !(p1Submitted && p2Submitted)) return

        lastResolvedRound = gs.round

        val p1Result = numberOrNull(gs.p1Input["result"])
        val p2Result = numberOrNull(gs.p2Input["result"])
        val (p1Delta, p2Delta) = scoreRound(gs.payload["target"], gs.turn, p1Result, p2Result)
        val newP1 = gs.p1Score + p1Delta
        val newP2 = gs.p2Score + p2Delta

        viewModelScope.launch {
            if (gs.round >= TOTAL_ROUNDS) {
                matchRepository.patchGameState(
                    matchId, GAME_TYPE,
                    mapOf("phase" to "FINISHED", "p1Score" to newP1, "p2Score" to newP2),
                )
                matchRepository.advanceMatch(matchId, GAME_TYPE, newP1, newP2)
            } else {
                matchRepository.patchGameState(
                    matchId, GAME_TYPE,
                    mapOf("phase" to "ROUND_END", "p1Score" to newP1, "p2Score" to newP2),
                )
                if (!advancingNextRound) {
                    advancingNextRound = true
                    delay(ROUND_END_MILLIS)
                    startRoundAsHost(round = gs.round + 1, p1Score = newP1, p2Score = newP2)
                }
            }
        }
    }

    /** Spec scoring: exact target → 10; otherwise closer → 5; tie → round owner ([turn]) gets 5. */
    private fun scoreRound(
        targetAny: Any?,
        turn: String?,
        p1Result: Int?,
        p2Result: Int?,
    ): Pair<Int, Int> {
        val target = numberOrNull(targetAny) ?: return 0 to 0
        val activeIsP1 = turn != "p2"
        val activeResult = if (activeIsP1) p1Result else p2Result
        val opponentResult = if (activeIsP1) p2Result else p1Result
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
        return if (activeIsP1) activeDelta to opponentDelta else opponentDelta to activeDelta
    }

    // --- Rendering ---

    private fun rebuildState() {
        val gs = latest ?: run {
            _state.update { it.copy(phase = MojBrojPhase.RoundIntro) }
            return
        }
        if (gs.round != renderedRound) {
            renderedRound = gs.round
            _state.update { it.copy(tokens = emptyList(), usedIndices = emptySet()) }
        }

        @Suppress("UNCHECKED_CAST")
        val numbers = (gs.payload["numbers"] as? List<*>)?.mapNotNull { numberOrNull(it) } ?: emptyList()
        val target = numberOrNull(gs.payload["target"]) ?: 0
        val now = System.currentTimeMillis()
        val secsLeft = if (gs.deadlineAt > 0)
            (((gs.deadlineAt - now) + 999) / 1000).toInt().coerceIn(0, 60) else 60

        val myInput = if (isHost) gs.p1Input else gs.p2Input
        val iSubmitted = myInput["submitted"] == true
        val activeIsP1 = gs.turn != "p2"
        val activeIsMe = (activeIsP1 && isHost) || (!activeIsP1 && !isHost)

        val myScore = if (isHost) gs.p1Score else gs.p2Score
        val theirScore = if (isHost) gs.p2Score else gs.p1Score
        val myResult = numberOrNull(myInput["result"])
        val theirInput = if (isHost) gs.p2Input else gs.p1Input
        val theirResult = numberOrNull(theirInput["result"])
        val myExpr = myInput["expr"] as? String ?: ""
        val theirExpr = theirInput["expr"] as? String ?: ""

        val phase = when (gs.phase) {
            "INPUT" -> MojBrojPhase.Player1Input
            "ROUND_END" -> MojBrojPhase.RoundEnd
            "FINISHED" -> MojBrojPhase.GameOver
            else -> MojBrojPhase.RoundIntro
        }

        _state.update {
            it.copy(
                currentRound = gs.round,
                phase = phase,
                targetNumber = target,
                drawnNumbers = numbers,
                playSecondsLeft = secsLeft,
                player1Points = myScore,
                player2Points = theirScore,
                player1Answer = myResult,
                player2Answer = theirResult,
                player1Expression = myExpr,
                player2Expression = theirExpr,
                iSubmitted = iSubmitted,
                activeIsMe = activeIsMe,
            )
        }
    }

    private fun numberOrNull(v: Any?): Int? = when (v) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> v.toInt()
        else -> null
    }

    private fun drawNumbersFallback(): List<Int> {
        val singles = (1..9).shuffled().take(4)
        val medium = listOf(10, 15, 20).random()
        val large = listOf(25, 50, 75, 100).random()
        return (singles + medium + large).shuffled()
    }

    private fun startLocalFallback() {
        val nums = drawNumbersFallback()
        _state.update {
            it.copy(
                phase = MojBrojPhase.Player1Input,
                currentRound = 1,
                targetNumber = (100..999).random(),
                drawnNumbers = nums,
                playSecondsLeft = 60,
                activeIsMe = true,
            )
        }
    }

    override fun onCleared() {
        observerJob?.cancel()
        loopJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val GAME_TYPE = "MOJ_BROJ"
        private const val TOTAL_ROUNDS = 2
        private const val INPUT_MILLIS = 60_000L
        private const val ROUND_END_MILLIS = 4_000L
    }
}
