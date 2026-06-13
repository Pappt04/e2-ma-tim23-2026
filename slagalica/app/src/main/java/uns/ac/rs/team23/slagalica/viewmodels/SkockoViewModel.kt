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
import uns.ac.rs.team23.slagalica.repository.MatchRepository

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

/**
 * Real-time, host-authoritative "Skočko".
 *
 * The host owns the whole [SkockoState], serializes it into the game-state `payload`, owns every
 * deadline, and applies both its own moves and the guest's (received as intents through `p2Input`).
 * The guest only renders the shared document and forwards its taps. Round 1 is played by player 1,
 * round 2 by player 2; the opponent gets a single 10s "steal" attempt if the active player misses.
 */
class SkockoViewModel(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SkockoState())
    val state: StateFlow<SkockoState> = _state.asStateFlow()

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

    private val syms = SkockoSymbol.values()

    fun enter() {
        if (started) return
        started = true
        matchId = MatchStore.matchId
        isHost = MatchStore.isHost

        if (matchId.isNotBlank()) {
            observerJob = viewModelScope.launch {
                matchRepository.observeGameState(matchId, GAME_TYPE).collect { gs ->
                    latest = gs
                    if (gs != null) rebuildState(gs)
                }
            }
            if (isHost) hostInit()
        } else {
            startRoundInternal(1)
        }
        startLoop()
    }

    private fun hostInit() {
        val s = SkockoState(
            phase = SkockoPhase.PLAYER_TURN,
            currentRound = 1,
            activePlayerIsP1 = true,
            solution = generateSolution(),
        )
        val deadline = now() + PLAYER_MILLIS
        deadlineAt = deadline
        _state.value = s
        viewModelScope.launch {
            matchRepository.setGameState(
                matchId, GAME_TYPE,
                mapOf(
                    "gameType" to GAME_TYPE,
                    "hostId" to MatchStore.hostId,
                    "phase" to "RUN",
                    "round" to 1,
                    "index" to 0,
                    "turn" to "p1",
                    "deadlineAt" to deadline,
                    "payload" to stateToMap(s, deadline),
                    "p1Input" to emptyMap<String, Any?>(),
                    "p2Input" to emptyMap<String, Any?>(),
                    "p1Score" to 0,
                    "p2Score" to 0,
                ),
            )
        }
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
        if (deadlineAt > 0 && (s.phase == SkockoPhase.PLAYER_TURN || s.phase == SkockoPhase.OPPONENT_STEAL)) {
            val left = (((deadlineAt - now) + 999) / 1000).toInt().coerceAtLeast(0)
            if (left != s.secondsLeft) _state.update { it.copy(secondsLeft = left) }
        }
        if (authoritative && deadlineAt > 0 && now >= deadlineAt && deadlineAt != lastHandledDeadline) {
            lastHandledDeadline = deadlineAt
            handleTimeout()
        }
        if (isHost) processGuestIntent()
    }

    // --- Public actions (screen) ---

    fun startRound() { /* host-driven: rounds auto-start; no-op kept for screen compatibility */ }

    fun nextRound() {
        if (!authoritative) return
        if (_state.value.phase == SkockoPhase.ROUND_END) {
            lastHandledDeadline = -1
            startRoundInternal(2)
        }
    }

    fun confirmRoundEnd() = act {
        if (authoritative) applyConfirm() else sendIntent("confirm")
    }

    fun addSymbol(symbol: SkockoSymbol) = act {
        if (authoritative) applyAdd(symbol) else sendIntent("add", symbol.ordinal)
    }

    fun removeSymbolAt(index: Int) = act {
        if (authoritative) applyRemove(index) else sendIntent("rm", index)
    }

    fun submitAttempt() = act {
        if (authoritative) applySubmit() else sendIntent("submit")
    }

    private inline fun act(block: () -> Unit) {
        if (localActive(_state.value)) block()
    }

    // --- Apply (authoritative) ---

    private fun applyAdd(symbol: SkockoSymbol) {
        val s = _state.value
        if (s.awaitingRoundEndConfirm) return
        if (s.phase != SkockoPhase.PLAYER_TURN && s.phase != SkockoPhase.OPPONENT_STEAL) return
        val target = s.currentInput.indexOfFirst { it == null }
        if (target == -1) return
        val updated = s.currentInput.toMutableList().also { it[target] = symbol }
        commit(s.copy(currentInput = updated), deadlineAt)
    }

    private fun applyRemove(index: Int) {
        val s = _state.value
        if (s.awaitingRoundEndConfirm) return
        if (index !in s.currentInput.indices) return
        val updated = s.currentInput.toMutableList().also { it[index] = null }
        commit(s.copy(currentInput = updated), deadlineAt)
    }

    private fun applySubmit() {
        val s = _state.value
        if (s.currentInput.any { it == null }) return
        val guess = s.currentInput.filterNotNull()
        val (cp, cs) = computeFeedback(guess, s.solution)
        val attempt = SkockoAttempt(guess, cp, cs, isOpponentAttempt = s.phase == SkockoPhase.OPPONENT_STEAL)
        val newAttempts = s.attempts + attempt
        val mainCount = newAttempts.count { !it.isOpponentAttempt }
        when {
            cp == 4 -> {
                val pts = if (s.phase == SkockoPhase.OPPONENT_STEAL) 10 else pointsForAttempt(mainCount)
                val (p1, p2) = awardPoints(s, pts)
                commit(
                    s.copy(
                        attempts = newAttempts, currentInput = List(4) { null },
                        roundSolved = true, showSolution = true,
                        player1Points = p1, player2Points = p2,
                        awaitingRoundEndConfirm = true,
                    ),
                    now() + REVEAL_MILLIS,
                )
            }
            s.phase == SkockoPhase.OPPONENT_STEAL -> {
                commit(
                    s.copy(
                        attempts = newAttempts, currentInput = List(4) { null },
                        showSolution = true, phase = SkockoPhase.ROUND_END,
                        awaitingRoundEndConfirm = false,
                    ),
                    now() + ROUND_END_MILLIS,
                )
            }
            mainCount >= 6 -> {
                commit(
                    s.copy(
                        attempts = newAttempts, currentInput = List(4) { null },
                        phase = SkockoPhase.OPPONENT_STEAL,
                    ),
                    now() + STEAL_MILLIS,
                )
            }
            else -> commit(s.copy(attempts = newAttempts, currentInput = List(4) { null }), deadlineAt)
        }
    }

    private fun applyConfirm() {
        val s = _state.value
        if (!s.awaitingRoundEndConfirm) return
        commit(s.copy(phase = SkockoPhase.ROUND_END, awaitingRoundEndConfirm = false), now() + ROUND_END_MILLIS)
    }

    private fun startRoundInternal(round: Int) {
        val s = _state.value
        val ns = SkockoState(
            phase = SkockoPhase.PLAYER_TURN,
            currentRound = round,
            activePlayerIsP1 = round == 1,
            solution = generateSolution(),
            player1Points = s.player1Points,
            player2Points = s.player2Points,
        )
        commit(ns, now() + PLAYER_MILLIS)
    }

    private fun handleTimeout() {
        val s = _state.value
        if (s.awaitingRoundEndConfirm) { applyConfirm(); return }
        when (s.phase) {
            SkockoPhase.PLAYER_TURN ->
                commit(s.copy(phase = SkockoPhase.OPPONENT_STEAL, currentInput = List(4) { null }), now() + STEAL_MILLIS)
            SkockoPhase.OPPONENT_STEAL ->
                commit(s.copy(phase = SkockoPhase.ROUND_END, showSolution = true, currentInput = List(4) { null }), now() + ROUND_END_MILLIS)
            SkockoPhase.ROUND_END -> {
                if (s.currentRound < 2) startRoundInternal(2)
                else finishGame(s)
            }
            else -> {}
        }
    }

    private fun finishGame(s: SkockoState) {
        commit(s.copy(phase = SkockoPhase.GAME_OVER), 0)
        if (isHost && !advanced) {
            advanced = true
            viewModelScope.launch { matchRepository.advanceMatch(matchId, GAME_TYPE, s.player1Points, s.player2Points) }
        }
    }

    // --- Commit / sync ---

    private fun commit(s: SkockoState, deadline: Long) {
        deadlineAt = deadline
        _state.value = s
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

    private fun sendIntent(type: String, a: Int = -1) {
        mySeq++
        val seq = mySeq
        viewModelScope.launch {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf("p2Input" to mapOf("seq" to seq, "type" to type, "a" to a)),
            )
        }
    }

    private fun processGuestIntent() {
        val gs = latest ?: return
        val seq = numberOrNull(gs.p2Input["seq"])?.toLong() ?: return
        if (seq <= lastIntentSeq) return
        lastIntentSeq = seq
        if (activeIsP1(_state.value)) return // not the guest's turn → ignore
        val type = gs.p2Input["type"] as? String ?: return
        val a = numberOrNull(gs.p2Input["a"]) ?: -1
        when (type) {
            "add" -> if (a in syms.indices) applyAdd(syms[a])
            "rm" -> applyRemove(a)
            "submit" -> applySubmit()
            "confirm" -> applyConfirm()
        }
    }

    private fun rebuildState(gs: GameStateDto) {
        val (s, dl) = mapToState(gs.payload) ?: return
        deadlineAt = dl
        _state.value = s
    }

    // --- Turn helpers ---

    private fun activeIsP1(s: SkockoState): Boolean = when (s.phase) {
        SkockoPhase.PLAYER_TURN -> s.activePlayerIsP1
        SkockoPhase.OPPONENT_STEAL -> !s.activePlayerIsP1
        else -> s.activePlayerIsP1
    }

    private fun localActive(s: SkockoState): Boolean =
        matchId.isBlank() || (activeIsP1(s) == isHost)

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun generateSolution(): List<SkockoSymbol> = List(4) { syms.random() }

    private fun computeFeedback(guess: List<SkockoSymbol>, solution: List<SkockoSymbol>): Pair<Int, Int> {
        var correctPos = 0
        val solCount = solution.groupingBy { it }.eachCount().toMutableMap()
        val remaining = mutableListOf<SkockoSymbol>()
        guess.forEachIndexed { i, sym ->
            if (sym == solution[i]) {
                correctPos++
                solCount[sym] = solCount[sym]!! - 1
            } else remaining.add(sym)
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

    // --- Serialization ---

    private fun stateToMap(s: SkockoState, deadline: Long): Map<String, Any?> = mapOf(
        "round" to s.currentRound,
        "phase" to s.phase.name,
        "activeP1" to s.activePlayerIsP1,
        "solution" to s.solution.map { it.ordinal },
        "attempts" to s.attempts.map {
            mapOf(
                "s" to it.symbols.map { x -> x.ordinal },
                "cp" to it.correctPosition,
                "cs" to it.correctSymbol,
                "opp" to it.isOpponentAttempt,
            )
        },
        "input" to s.currentInput.map { it?.ordinal ?: -1 },
        "p1" to s.player1Points,
        "p2" to s.player2Points,
        "solved" to s.roundSolved,
        "show" to s.showSolution,
        "confirm" to s.awaitingRoundEndConfirm,
        "deadline" to deadline,
    )

    @Suppress("UNCHECKED_CAST")
    private fun mapToState(p: Map<String, Any?>): Pair<SkockoState, Long>? {
        val phaseName = p["phase"] as? String ?: return null
        fun sym(i: Int?): SkockoSymbol? = i?.let { if (it in syms.indices) syms[it] else null }
        val solution = (p["solution"] as? List<*>)?.mapNotNull { sym(numberOrNull(it)) } ?: emptyList()
        val attempts = (p["attempts"] as? List<*>)?.mapNotNull { a ->
            val m = a as? Map<String, Any?> ?: return@mapNotNull null
            val ss = (m["s"] as? List<*>)?.mapNotNull { sym(numberOrNull(it)) } ?: return@mapNotNull null
            SkockoAttempt(ss, numberOrNull(m["cp"]) ?: 0, numberOrNull(m["cs"]) ?: 0, m["opp"] == true)
        } ?: emptyList()
        val input = (p["input"] as? List<*>)?.map { sym(numberOrNull(it)) } ?: List(4) { null }
        val deadline = numberOrNull(p["deadline"])?.toLong() ?: 0L
        val phase = runCatching { SkockoPhase.valueOf(phaseName) }.getOrDefault(SkockoPhase.PLAYER_TURN)
        val maxSecs = if (phase == SkockoPhase.OPPONENT_STEAL) 10 else 30
        val s = SkockoState(
            phase = phase,
            currentRound = numberOrNull(p["round"]) ?: 1,
            activePlayerIsP1 = p["activeP1"] != false,
            solution = solution,
            attempts = attempts,
            currentInput = if (input.size == 4) input else List(4) { null },
            secondsLeft = secsLeft(deadline, maxSecs),
            player1Points = numberOrNull(p["p1"]) ?: 0,
            player2Points = numberOrNull(p["p2"]) ?: 0,
            roundSolved = p["solved"] == true,
            showSolution = p["show"] == true,
            awaitingRoundEndConfirm = p["confirm"] == true,
        )
        return s to deadline
    }

    private fun secsLeft(deadline: Long, max: Int): Int =
        if (deadline <= 0) max else (((deadline - now()) + 999) / 1000).toInt().coerceIn(0, max)

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
        private const val GAME_TYPE = "SKOCKO"
        private const val PLAYER_MILLIS = 30_000L
        private const val STEAL_MILLIS = 10_000L
        private const val REVEAL_MILLIS = 3_000L
        private const val ROUND_END_MILLIS = 4_000L
    }
}
