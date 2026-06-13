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
import uns.ac.rs.team23.slagalica.models.AsocijacijeQuestion
import uns.ac.rs.team23.slagalica.network.dto.GameStateDto
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
    val p1Ready: Boolean = false,
    val p2Ready: Boolean = false,
)

/**
 * Real-time, host-authoritative "Asocijacije".
 *
 * The host fetches one question and writes the whole [AsocijacijeState] into the game-state
 * `payload` so both players see the identical board. The host owns the 2-minute deadline and
 * applies both its own and the guest's moves (reveals/guesses arrive as intents through `p2Input`).
 * The active player alternates (round 1 → player 1, round 2 → player 2; a wrong guess passes the
 * turn). `guessInput`/`selectedGuessTarget`/`wrongGuessTarget` are local UI only and never synced.
 */
class AsocijacijeViewModel(
    private val gameRepository: GameRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AsocijacijeState())
    val state: StateFlow<AsocijacijeState> = _state.asStateFlow()

    private var started = false
    private var matchId: String = ""
    private var isHost: Boolean = false
    private val authoritative: Boolean get() = isHost || matchId.isBlank()

    private var observerJob: Job? = null
    private var loopJob: Job? = null
    private var latest: GameStateDto? = null
    private var deadlineAt: Long = 0
    private var lastHandledDeadline: Long = -1
    private var lastIntentSeq: Long = -1
    private var mySeq: Long = 0
    private var advanced = false
    private var roundStarting = false

    fun enter() {
        if (started) return
        started = true
        matchId = MatchStore.matchId
        isHost = MatchStore.isHost

        if (matchId.isNotBlank()) {
            _state.update { it.copy(phase = AsocijacijePhase.LOADING) }
            observerJob = viewModelScope.launch {
                matchRepository.observeGameState(matchId, GAME_TYPE).collect { gs ->
                    latest = gs
                    if (gs != null && !isHost) rebuildState(gs)
                }
            }
            if (isHost) hostStartRound(1)
        } else {
            hostStartRound(1)
        }
        startLoop()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (true) {
                tick()
                delay(200)
            }
        }
    }

    private fun tick() {
        val s = _state.value
        val now = now()
        if (deadlineAt > 0 && s.phase == AsocijacijePhase.PLAYING) {
            val left = (((deadlineAt - now) + 999) / 1000).toInt().coerceIn(0, 120)
            if (left != s.secondsLeft) _state.update { it.copy(secondsLeft = left) }
        }
        if (authoritative && deadlineAt > 0 && now >= deadlineAt && deadlineAt != lastHandledDeadline) {
            lastHandledDeadline = deadlineAt
            handleTimeout()
        }
        if (isHost) processGuestIntent()
    }

    // --- Public actions (screen) ---

    fun startRound() { /* host-driven */ }

    fun markReady() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.ROUND_END) return
        if (isHost) {
            if (s.p1Ready) return
            commit(s.copy(p1Ready = true), 0)
            checkBothReady()
        } else {
            if (s.p2Ready) return
            _state.update { it.copy(p2Ready = true) }
            sendIntent(mapOf("t" to "ready"))
        }
    }

    fun nextRound() = markReady()

    /** Local UI only: which cell/answer the active player is typing into. */
    fun selectGuessTarget(target: GuessTarget) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || !localActive(s) || !canGuessNow(s)) return
        when (target) {
            is GuessTarget.Column -> if (s.columns.getOrNull(target.index)?.isSolved == true) return
            GuessTarget.Final -> if (s.isFinalSolved) return
        }
        _state.update { it.copy(selectedGuessTarget = target, guessInput = "", wrongGuessTarget = null) }
    }

    fun onGuessChange(text: String) {
        _state.update { it.copy(guessInput = text.uppercase(), wrongGuessTarget = null) }
    }

    fun revealField(col: Int, row: Int) = act {
        if (authoritative) applyReveal(col, row) else sendIntent(mapOf("t" to "reveal", "a" to col, "b" to row))
    }

    fun submitGuess() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || !localActive(s) || !canGuessNow(s)) return
        val target = s.selectedGuessTarget ?: return
        val guess = s.guessInput.trim()
        if (guess.isEmpty()) return
        val targetCode = when (target) {
            is GuessTarget.Column -> "C${target.index}"
            GuessTarget.Final -> "F"
        }
        // Resolve correctness locally (the board answers are in state on both devices) purely
        // to drive the red flash; the authoritative scoring still happens in applySubmit.
        val correct = when (target) {
            is GuessTarget.Column ->
                s.columns.getOrNull(target.index)?.answer?.equals(guess, ignoreCase = true) == true
            GuessTarget.Final -> s.finalAnswer.equals(guess, ignoreCase = true)
        }
        if (authoritative) applySubmit(targetCode, guess)
        else sendIntent(mapOf("t" to "guess", "tg" to targetCode, "g" to guess))
        // Local flash for the submitting device; cleared after a short beat.
        _state.update {
            it.copy(guessInput = "", selectedGuessTarget = null, wrongGuessTarget = if (correct) null else target)
        }
        if (!correct) {
            viewModelScope.launch {
                delay(800L)
                _state.update { if (it.wrongGuessTarget == target) it.copy(wrongGuessTarget = null) else it }
            }
        }
    }

    fun passGuess() = act {
        if (authoritative) applyPass() else sendIntent(mapOf("t" to "pass"))
        _state.update { it.copy(selectedGuessTarget = null, guessInput = "", wrongGuessTarget = null) }
    }

    private inline fun act(block: () -> Unit) {
        if (localActive(_state.value)) block()
    }

    // --- Apply (authoritative) ---

    private fun applyReveal(col: Int, row: Int) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING || s.waitingForGuess) return
        if (col !in s.columns.indices) return
        val column = s.columns[col]
        if (row !in column.revealedFields.indices || column.revealedFields[row] || column.isSolved) return
        val newRevealed = column.revealedFields.toMutableList().also { it[row] = true }
        val newColumns = s.columns.toMutableList().also { it[col] = column.copy(revealedFields = newRevealed) }
        commit(s.copy(columns = newColumns, waitingForGuess = true), deadlineAt)
    }

    private fun applySubmit(targetCode: String, guess: String) {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING) return
        if (targetCode == "F") {
            if (!s.isFinalSolved && guess.equals(s.finalAnswer, ignoreCase = true)) { solveFinal(); return }
        } else {
            val idx = targetCode.removePrefix("C").toIntOrNull() ?: return
            val col = s.columns.getOrNull(idx) ?: return
            if (col.isSolved) return
            if (guess.equals(col.answer, ignoreCase = true)) { solveColumn(idx); return }
        }
        // Wrong → end this player's turn and pass after a brief beat.
        commit(s.copy(waitingForGuess = false), deadlineAt)
        viewModelScope.launch {
            delay(800L)
            if (_state.value.phase == AsocijacijePhase.PLAYING) switchPlayer()
        }
    }

    private fun applyPass() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.PLAYING) return
        commit(s.copy(waitingForGuess = false), deadlineAt)
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
        commit(s.copy(columns = newColumns, player1Points = newP1, player2Points = newP2, waitingForGuess = true), deadlineAt)
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
        commit(s.copy(isFinalSolved = true, player1Points = newP1, player2Points = newP2), deadlineAt)
        endRound()
    }

    private fun switchPlayer() {
        val s = _state.value
        commit(s.copy(activePlayer = if (s.activePlayer == 1) 2 else 1, waitingForGuess = false), deadlineAt)
    }

    private fun endRound() {
        val s = _state.value
        commit(s.copy(phase = AsocijacijePhase.ROUND_END, p1Ready = false, p2Ready = false), 0)
    }

    private fun checkBothReady() {
        val s = _state.value
        if (s.phase != AsocijacijePhase.ROUND_END || !s.p1Ready || !s.p2Ready) return
        if (roundStarting) return
        roundStarting = true
        if (s.currentRound < 2) {
            hostStartRound(2)
        } else {
            commit(s.copy(phase = AsocijacijePhase.GAME_OVER), 0)
            if (isHost && !advanced) {
                advanced = true
                viewModelScope.launch { matchRepository.advanceMatch(matchId, GAME_TYPE, s.player1Points, s.player2Points) }
            }
        }
        roundStarting = false
    }

    private fun hostStartRound(round: Int) {
        if (!authoritative) return
        viewModelScope.launch {
            val q = gameRepository.getAsocijacijeQuestion().getOrNull()
            if (q == null) {
                _state.update { it.copy(errorMessage = "Failed to load question") }
                return@launch
            }
            val s = buildRoundState(round, q)
            val deadline = now() + PLAY_MILLIS
            if (isHost) {
                deadlineAt = deadline
                _state.value = s
                matchRepository.setGameState(
                    matchId, GAME_TYPE,
                    mapOf(
                        "gameType" to GAME_TYPE,
                        "hostId" to MatchStore.hostId,
                        "phase" to "RUN",
                        "round" to round,
                        "index" to 0,
                        "turn" to if (round == 1) "p1" else "p2",
                        "deadlineAt" to deadline,
                        "payload" to stateToMap(s, deadline),
                        "p1Input" to emptyMap<String, Any?>(),
                        "p2Input" to emptyMap<String, Any?>(),
                        "p1Score" to s.player1Points,
                        "p2Score" to s.player2Points,
                    ),
                )
            } else {
                // Local fallback (no match): just drive it directly.
                deadlineAt = deadline
                _state.value = s
            }
        }
    }

    private fun buildRoundState(round: Int, q: AsocijacijeQuestion): AsocijacijeState {
        val prev = _state.value
        return AsocijacijeState(
            phase = AsocijacijePhase.PLAYING,
            currentRound = round,
            columns = q.columns.map { AsocijacijeColumn(words = it.words, answer = it.answer) },
            finalAnswer = q.finalAnswer,
            isFinalSolved = false,
            activePlayer = if (round == 1) 1 else 2,
            secondsLeft = 120,
            player1Points = if (round == 1) 0 else prev.player1Points,
            player2Points = if (round == 1) 0 else prev.player2Points,
        )
    }

    private fun handleTimeout() {
        val s = _state.value
        if (s.phase == AsocijacijePhase.PLAYING) endRound()
    }

    private fun commit(s: AsocijacijeState, deadline: Long) {
        deadlineAt = deadline
        if (deadline > now()) lastHandledDeadline = -1
        // Preserve local-only UI fields already in _state.
        _state.update { cur ->
            s.copy(
                guessInput = cur.guessInput,
                selectedGuessTarget = cur.selectedGuessTarget,
                wrongGuessTarget = cur.wrongGuessTarget,
            )
        }
        if (!isHost) return
        viewModelScope.launch {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf(
                    "payload" to stateToMap(s, deadline),
                    "deadlineAt" to deadline,
                    "p1Score" to s.player1Points,
                    "p2Score" to s.player2Points,
                ),
            )
        }
    }

    private fun sendIntent(fields: Map<String, Any?>) {
        mySeq++
        viewModelScope.launch {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf("p2Input" to (fields + ("seq" to mySeq))),
            )
        }
    }

    private fun processGuestIntent() {
        val gs = latest ?: return
        val seq = numberOrNull(gs.p2Input["seq"])?.toLong() ?: return
        if (seq <= lastIntentSeq) return
        lastIntentSeq = seq
        when (gs.p2Input["t"] as? String) {
            "ready" -> {
                val s = _state.value
                if (s.phase == AsocijacijePhase.ROUND_END && !s.p2Ready) {
                    commit(s.copy(p2Ready = true), 0)
                    checkBothReady()
                }
            }
            "reveal" -> if (_state.value.activePlayer != 1)
                applyReveal(numberOrNull(gs.p2Input["a"]) ?: return, numberOrNull(gs.p2Input["b"]) ?: return)
            "guess" -> if (_state.value.activePlayer != 1)
                applySubmit(gs.p2Input["tg"] as? String ?: return, gs.p2Input["g"] as? String ?: return)
            "pass" -> if (_state.value.activePlayer != 1) applyPass()
        }
    }

    private fun rebuildState(gs: GameStateDto) {
        val (s, _) = mapToState(gs.payload, gs) ?: return
        deadlineAt = effectiveDeadline(gs, gs.payload)
        _state.update { cur ->
            s.copy(
                guessInput = cur.guessInput,
                selectedGuessTarget = cur.selectedGuessTarget,
                wrongGuessTarget = cur.wrongGuessTarget,
                secondsLeft = secsLeft(deadlineAt, 120),
            )
        }
    }

    private fun localActive(s: AsocijacijeState): Boolean =
        matchId.isBlank() || ((s.activePlayer == 1) == isHost)

    private fun canGuessNow(s: AsocijacijeState): Boolean {
        if (s.waitingForGuess) return true
        return !s.columns.any { col -> !col.isSolved && col.revealedFields.any { !it } }
    }

    // --- Serialization ---

    private fun stateToMap(s: AsocijacijeState, deadline: Long): Map<String, Any?> = mapOf(
        "phase" to s.phase.name,
        "round" to s.currentRound,
        "columns" to s.columns.map {
            mapOf("words" to it.words, "answer" to it.answer, "revealed" to it.revealedFields, "solved" to it.isSolved)
        },
        "final" to s.finalAnswer,
        "finalSolved" to s.isFinalSolved,
        "active" to s.activePlayer,
        "p1" to s.player1Points,
        "p2" to s.player2Points,
        "waiting" to s.waitingForGuess,
        "p1Ready" to s.p1Ready,
        "p2Ready" to s.p2Ready,
        "deadline" to deadline,
    )

    @Suppress("UNCHECKED_CAST")
    private fun mapToState(p: Map<String, Any?>, gs: GameStateDto): Pair<AsocijacijeState, Long>? {
        val phaseName = p["phase"] as? String ?: return null
        val columns = (p["columns"] as? List<*>)?.mapNotNull {
            val m = it as? Map<String, Any?> ?: return@mapNotNull null
            val words = (m["words"] as? List<*>)?.map { w -> w.toString() } ?: return@mapNotNull null
            val answer = m["answer"] as? String ?: return@mapNotNull null
            val revealed = (m["revealed"] as? List<*>)?.map { r -> r == true } ?: List(4) { false }
            AsocijacijeColumn(words, answer, revealed, m["solved"] == true)
        } ?: emptyList()
        val deadline = effectiveDeadline(gs, p)
        val s = AsocijacijeState(
            phase = runCatching { AsocijacijePhase.valueOf(phaseName) }.getOrDefault(AsocijacijePhase.PLAYING),
            currentRound = numberOrNull(p["round"]) ?: 1,
            columns = columns,
            finalAnswer = p["final"] as? String ?: "",
            isFinalSolved = p["finalSolved"] == true,
            activePlayer = numberOrNull(p["active"]) ?: 1,
            secondsLeft = secsLeft(deadline, 120),
            player1Points = numberOrNull(p["p1"]) ?: 0,
            player2Points = numberOrNull(p["p2"]) ?: 0,
            waitingForGuess = p["waiting"] == true,
            p1Ready = p["p1Ready"] == true,
            p2Ready = p["p2Ready"] == true,
        )
        return s to deadline
    }

    private fun numberOrNull(v: Any?): Int? = when (v) {
        is Long -> v.toInt()
        is Int -> v
        is Double -> v.toInt()
        else -> null
    }

    private fun now() = System.currentTimeMillis()

    override fun onCleared() {
        observerJob?.cancel()
        loopJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val GAME_TYPE = "ASOCIJACIJE"
        private const val PLAY_MILLIS = 120_000L
        private const val ROUND_END_MILLIS = 4_000L
    }
}
