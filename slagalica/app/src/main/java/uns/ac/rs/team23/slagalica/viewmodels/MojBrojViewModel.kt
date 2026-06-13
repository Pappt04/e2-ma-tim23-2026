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
    val p1Ready: Boolean = false,
    val p2Ready: Boolean = false,
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
    private var lastReadySeq = 0L
    private var roundStarting = false

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
                if (!isHost) rebuildState()
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
            renderedRound = round
            lastReadySeq = 0L
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
                    "payload" to mapOf(
                        "target" to target,
                        "numbers" to numbers,
                        "p1Ready" to false,
                        "p2Ready" to false,
                    ),
                    "p1Input" to emptyMap<String, Any?>(),
                    "p2Input" to emptyMap<String, Any?>(),
                    "p1Score" to p1Score,
                    "p2Score" to p2Score,
                ),
            )
            _state.update {
                it.copy(
                    currentRound = round,
                    phase = MojBrojPhase.Player1Input,
                    targetNumber = target,
                    drawnNumbers = numbers,
                    tokens = emptyList(),
                    usedIndices = emptySet(),
                    player1Points = p1Score,
                    player2Points = p2Score,
                    playSecondsLeft = 60,
                    iSubmitted = false,
                    p1Ready = false,
                    p2Ready = false,
                    player1Answer = null,
                    player2Answer = null,
                    player1Expression = "",
                    player2Expression = "",
                    activeIsMe = if (round == 1) isHost else !isHost,
                )
            }
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (true) {
                if (isHost) {
                    updateHostTimer()
                    maybeResolve()
                    processGuestReady()
                    checkBothReady()
                } else {
                    rebuildState()
                }
                delay(200)
            }
        }
    }

    fun markReady() {
        val s = _state.value
        if (s.phase != MojBrojPhase.RoundEnd) return
        if (isHost) {
            if (s.p1Ready) return
            _state.update { it.copy(p1Ready = true) }
            patchPayloadReady(p1Ready = true, p2Ready = s.p2Ready)
            checkBothReady()
        } else {
            if (s.p2Ready) return
            _state.update { it.copy(p2Ready = true) }
            viewModelScope.launch {
                matchRepository.patchGameState(
                    matchId, GAME_TYPE,
                    mapOf("p2Input" to mapOf("t" to "ready", "seq" to System.currentTimeMillis())),
                )
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
            val payload = gs.payload.toMutableMap().apply {
                put("p1Ready", false)
                put("p2Ready", false)
            }
            val roundEndPatch = mapOf(
                "phase" to "ROUND_END",
                "p1Score" to newP1,
                "p2Score" to newP2,
                "payload" to payload,
            )
            matchRepository.patchGameState(matchId, GAME_TYPE, roundEndPatch)
            _state.update {
                it.copy(
                    phase = MojBrojPhase.RoundEnd,
                    player1Points = newP1,
                    player2Points = newP2,
                    player1Answer = p1Result,
                    player2Answer = p2Result,
                    player1Expression = gs.p1Input["expr"] as? String ?: it.player1Expression,
                    player2Expression = gs.p2Input["expr"] as? String ?: it.player2Expression,
                    p1Ready = false,
                    p2Ready = false,
                )
            }
        }
    }

    private fun processGuestReady() {
        val gs = latest ?: return
        if (gs.phase != "ROUND_END") return
        val seq = longOrNull(gs.p2Input["seq"]) ?: return
        if (seq <= lastReadySeq) return
        if (gs.p2Input["t"] != "ready") return
        lastReadySeq = seq
        if (!_state.value.p2Ready) {
            val p1Ready = _state.value.p1Ready
            _state.update { it.copy(p2Ready = true) }
            patchPayloadReady(p1Ready = p1Ready, p2Ready = true)
            checkBothReady()
        }
    }

    private fun checkBothReady() {
        val s = _state.value
        if (s.phase != MojBrojPhase.RoundEnd || !s.p1Ready || !s.p2Ready) return
        if (roundStarting) return
        roundStarting = true
        if (s.currentRound >= TOTAL_ROUNDS) {
            viewModelScope.launch {
                matchRepository.patchGameState(
                    matchId, GAME_TYPE,
                    mapOf("phase" to "FINISHED"),
                )
                matchRepository.advanceMatch(matchId, GAME_TYPE, s.player1Points, s.player2Points)
                _state.update { it.copy(phase = MojBrojPhase.GameOver) }
            }
        } else {
            startRoundAsHost(round = s.currentRound + 1, p1Score = s.player1Points, p2Score = s.player2Points)
        }
        roundStarting = false
    }

    private fun patchPayloadReady(p1Ready: Boolean, p2Ready: Boolean) {
        val gs = latest ?: return
        val payload = gs.payload.toMutableMap().apply {
            put("p1Ready", p1Ready)
            put("p2Ready", p2Ready)
        }
        viewModelScope.launch {
            matchRepository.patchGameState(matchId, GAME_TYPE, mapOf("payload" to payload))
        }
    }

    private fun updateHostTimer() {
        val gs = latest ?: return
        val s = _state.value
        if (gs.phase != "INPUT" || s.phase != MojBrojPhase.Player1Input) return
        val left = secsLeft(gs.deadlineAt, 60)
        if (left != s.playSecondsLeft) _state.update { it.copy(playSecondsLeft = left) }
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
        val secsLeft = secsLeft(gs.deadlineAt, 60, now)

        val myInput = if (isHost) gs.p1Input else gs.p2Input
        val iSubmitted = myInput["submitted"] == true
        val activeIsP1 = gs.turn != "p2"
        val activeIsMe = (activeIsP1 && isHost) || (!activeIsP1 && !isHost)

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
                player1Points = gs.p1Score,
                player2Points = gs.p2Score,
                player1Answer = numberOrNull(gs.p1Input["result"]),
                player2Answer = numberOrNull(gs.p2Input["result"]),
                player1Expression = gs.p1Input["expr"] as? String ?: "",
                player2Expression = gs.p2Input["expr"] as? String ?: "",
                iSubmitted = iSubmitted,
                activeIsMe = activeIsMe,
                p1Ready = gs.payload["p1Ready"] == true,
                p2Ready = gs.payload["p2Ready"] == true || it.p2Ready,
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
    }
}
