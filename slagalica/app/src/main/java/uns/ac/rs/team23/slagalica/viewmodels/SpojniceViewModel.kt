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

enum class SpojnicePhase {
    ROUND_INTRO,
    PLAYING_STARTER,
    PLAYING_OPPONENT,
    ROUND_END,
    GAME_OVER,
}

data class SpojnicePair(
    val left: String,
    val right: String,
)

data class SpojniceState(
    val currentRound: Int = 1,
    val phase: SpojnicePhase = SpojnicePhase.ROUND_INTRO,
    /** True = player 1 starts this round (round 1), false = player 2 starts (round 2). */
    val starterIsPlayer1: Boolean = true,
    val secondsLeft: Int = 30,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val pairs: List<SpojnicePair> = emptyList(),
    /** Shuffled order of right labels (fixed for the round). */
    val rightOptions: List<String> = emptyList(),
    /** Correct matches so far this round: left index -> index in rightOptions. */
    val correctLeftToRightIndex: Map<Int, Int> = emptyMap(),
    /** Starter has tried exactly one pick per left index (correct or wrong). */
    val starterAttemptsUsedLeft: Set<Int> = emptySet(),
    val selectedLeftIndex: Int? = null,
    val selectedRightIndex: Int? = null,
    val infoMessage: String = "",
)

/**
 * Real-time, host-authoritative "Spojnice".
 *
 * The host generates the shared pairs + right-column shuffle per round, owns the deadlines, and
 * applies both its own and the guest's picks (guest taps arrive as intents through `p2Input`).
 * Round 1 starter is player 1, round 2 starter is player 2; the non-starter gets a 30s window to
 * connect whatever the starter missed.
 */
class SpojniceViewModel(
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SpojniceState())
    val state: StateFlow<SpojniceState> = _state.asStateFlow()

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
        val s = freshRound(1)
        deadlineAt = now() + STARTER_MILLIS
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
                    "deadlineAt" to deadlineAt,
                    "payload" to stateToMap(s, deadlineAt),
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
        if (deadlineAt > 0 && (s.phase == SpojnicePhase.PLAYING_STARTER || s.phase == SpojnicePhase.PLAYING_OPPONENT)) {
            val max = if (s.phase == SpojnicePhase.PLAYING_OPPONENT) 30 else 45
            val left = secsLeft(deadlineAt, max)
            if (left != s.secondsLeft) _state.update { it.copy(secondsLeft = left) }
        }
        if (authoritative && deadlineAt > 0 && now >= deadlineAt && deadlineAt != lastHandledDeadline) {
            lastHandledDeadline = deadlineAt
            handleTimeout()
        }
        if (isHost) processGuestIntent()
    }

    // --- Public actions (screen) ---

    fun startRound() { /* host-driven: round auto-starts */ }

    fun nextRound() {
        if (!authoritative) return
        if (_state.value.phase == SpojnicePhase.ROUND_END) {
            lastHandledDeadline = -1
            startRoundInternal(2)
        }
    }

    fun selectLeft(index: Int) = act {
        if (authoritative) applySelectLeft(index) else sendIntent("L", index)
    }

    fun selectRight(index: Int) = act {
        if (authoritative) applySelectRight(index) else sendIntent("R", index)
    }

    private inline fun act(block: () -> Unit) {
        if (localActive(_state.value)) block()
    }

    // --- Apply (authoritative) ---

    private fun applySelectLeft(index: Int) {
        val s = _state.value
        if (s.phase != SpojnicePhase.PLAYING_STARTER && s.phase != SpojnicePhase.PLAYING_OPPONENT) return
        if (index in s.correctLeftToRightIndex.keys) return
        if (s.phase == SpojnicePhase.PLAYING_STARTER && index in s.starterAttemptsUsedLeft) return
        commit(s.copy(selectedLeftIndex = index), deadlineAt)
        tryMatch()
    }

    private fun applySelectRight(index: Int) {
        val s = _state.value
        if (s.phase != SpojnicePhase.PLAYING_STARTER && s.phase != SpojnicePhase.PLAYING_OPPONENT) return
        if (s.correctLeftToRightIndex.values.contains(index)) return
        commit(s.copy(selectedRightIndex = index), deadlineAt)
        tryMatch()
    }

    private fun tryMatch() {
        val s = _state.value
        val li = s.selectedLeftIndex ?: return
        val ri = s.selectedRightIndex ?: return
        if (li in s.correctLeftToRightIndex.keys) return
        if (s.correctLeftToRightIndex.values.contains(ri)) return

        val correct = s.pairs[li].right == s.rightOptions[ri]
        when (s.phase) {
            SpojnicePhase.PLAYING_STARTER -> {
                if (li in s.starterAttemptsUsedLeft) return
                val starterIsP1 = s.starterIsPlayer1
                if (correct) {
                    val p1 = if (starterIsP1) (s.player1Points + 2).coerceIn(0, 20) else s.player1Points
                    val p2 = if (starterIsP1) s.player2Points else (s.player2Points + 2).coerceIn(0, 20)
                    commit(
                        s.copy(
                            correctLeftToRightIndex = s.correctLeftToRightIndex + (li to ri),
                            selectedLeftIndex = null, selectedRightIndex = null,
                            starterAttemptsUsedLeft = s.starterAttemptsUsedLeft + li,
                            player1Points = p1, player2Points = p2,
                            infoMessage = "Correct: +2",
                        ),
                        deadlineAt,
                    )
                } else {
                    commit(
                        s.copy(
                            starterAttemptsUsedLeft = s.starterAttemptsUsedLeft + li,
                            selectedLeftIndex = null, selectedRightIndex = null,
                            infoMessage = "Wrong — hidden until opponent turn.",
                        ),
                        deadlineAt,
                    )
                }
                maybeFinishStarterPhase()
            }

            SpojnicePhase.PLAYING_OPPONENT -> {
                val opponentIsP1 = !s.starterIsPlayer1
                if (correct) {
                    val p1 = if (opponentIsP1) (s.player1Points + 2).coerceIn(0, 20) else s.player1Points
                    val p2 = if (opponentIsP1) s.player2Points else (s.player2Points + 2).coerceIn(0, 20)
                    commit(
                        s.copy(
                            correctLeftToRightIndex = s.correctLeftToRightIndex + (li to ri),
                            selectedLeftIndex = null, selectedRightIndex = null,
                            player1Points = p1, player2Points = p2,
                            infoMessage = "Correct: +2",
                        ),
                        deadlineAt,
                    )
                } else {
                    commit(
                        s.copy(
                            selectedLeftIndex = null, selectedRightIndex = null,
                            infoMessage = "Wrong — try again.",
                        ),
                        deadlineAt,
                    )
                }
                if (_state.value.correctLeftToRightIndex.size == _state.value.pairs.size) finishRoundAfterPlay()
            }

            else -> {}
        }
    }

    private fun maybeFinishStarterPhase() {
        val s = _state.value
        if (s.phase != SpojnicePhase.PLAYING_STARTER) return
        if (s.starterAttemptsUsedLeft.size < s.pairs.size) return
        if (s.correctLeftToRightIndex.size >= s.pairs.size) {
            finishRoundAfterPlay()
            return
        }
        beginOpponentPhase()
    }

    private fun beginOpponentPhase() {
        commit(
            _state.value.copy(
                phase = SpojnicePhase.PLAYING_OPPONENT,
                secondsLeft = 30,
                selectedLeftIndex = null, selectedRightIndex = null,
                infoMessage = "Opponent: connect remaining pairs. You have 30 seconds.",
            ),
            now() + OPPONENT_MILLIS,
        )
    }

    private fun finishRoundAfterPlay() {
        val s = _state.value
        val nextPhase = if (s.currentRound == 1) SpojnicePhase.ROUND_END else SpojnicePhase.GAME_OVER
        commit(
            s.copy(phase = nextPhase, infoMessage = "Round ${s.currentRound} finished"),
            now() + ROUND_END_MILLIS,
        )
        if (nextPhase == SpojnicePhase.GAME_OVER && isHost && !advanced) {
            advanced = true
            viewModelScope.launch { matchRepository.advanceMatch(matchId, GAME_TYPE, s.player1Points, s.player2Points) }
        }
    }

    private fun startRoundInternal(round: Int) {
        commit(freshRound(round), now() + STARTER_MILLIS)
    }

    private fun freshRound(round: Int): SpojniceState {
        val prev = _state.value
        val pairs = if (round == 1) samplePairsRound1() else samplePairsRound2()
        return SpojniceState(
            currentRound = round,
            phase = SpojnicePhase.PLAYING_STARTER,
            starterIsPlayer1 = round == 1,
            secondsLeft = 30,
            player1Points = if (round == 1) 0 else prev.player1Points,
            player2Points = if (round == 1) 0 else prev.player2Points,
            pairs = pairs,
            rightOptions = pairs.map { it.right }.shuffled(),
            infoMessage = "Starter: try each left term once. Only correct pairs stay visible.",
        )
    }

    private fun handleTimeout() {
        val s = _state.value
        when (s.phase) {
            SpojnicePhase.PLAYING_STARTER -> beginOpponentPhase()
            SpojnicePhase.PLAYING_OPPONENT -> finishRoundAfterPlay()
            SpojnicePhase.ROUND_END -> startRoundInternal(2)
            else -> {}
        }
    }

    // --- Commit / sync ---

    private fun commit(s: SpojniceState, deadline: Long) {
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

    private fun sendIntent(type: String, a: Int) {
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
        if (activeIsP1(_state.value)) return // not the guest's turn
        val type = gs.p2Input["type"] as? String ?: return
        val a = numberOrNull(gs.p2Input["a"]) ?: return
        when (type) {
            "L" -> applySelectLeft(a)
            "R" -> applySelectRight(a)
        }
    }

    private fun rebuildState(gs: GameStateDto) {
        val (s, dl) = mapToState(gs.payload) ?: return
        deadlineAt = dl
        _state.value = s
    }

    // --- Turn helpers ---

    private fun activeIsP1(s: SpojniceState): Boolean = when (s.phase) {
        SpojnicePhase.PLAYING_STARTER -> s.starterIsPlayer1
        SpojnicePhase.PLAYING_OPPONENT -> !s.starterIsPlayer1
        else -> s.starterIsPlayer1
    }

    private fun localActive(s: SpojniceState): Boolean =
        matchId.isBlank() || (activeIsP1(s) == isHost)

    // --- Content ---

    private fun samplePairsRound1(): List<SpojnicePair> = listOf(
        SpojnicePair("Serbia", "Belgrade"),
        SpojnicePair("France", "Paris"),
        SpojnicePair("Italy", "Rome"),
        SpojnicePair("Spain", "Madrid"),
        SpojnicePair("Germany", "Berlin"),
    )

    private fun samplePairsRound2(): List<SpojnicePair> = listOf(
        SpojnicePair("Mercury", "Closest to Sun"),
        SpojnicePair("Jupiter", "Largest planet"),
        SpojnicePair("Saturn", "Has large rings"),
        SpojnicePair("Mars", "Red planet"),
        SpojnicePair("Neptune", "Blue ice giant"),
    )

    // --- Serialization ---

    private fun stateToMap(s: SpojniceState, deadline: Long): Map<String, Any?> = mapOf(
        "round" to s.currentRound,
        "phase" to s.phase.name,
        "starterP1" to s.starterIsPlayer1,
        "p1" to s.player1Points,
        "p2" to s.player2Points,
        "pairs" to s.pairs.map { mapOf("l" to it.left, "r" to it.right) },
        "right" to s.rightOptions,
        "correct" to s.correctLeftToRightIndex.map { mapOf("k" to it.key, "v" to it.value) },
        "used" to s.starterAttemptsUsedLeft.toList(),
        "selL" to (s.selectedLeftIndex ?: -1),
        "selR" to (s.selectedRightIndex ?: -1),
        "info" to s.infoMessage,
        "deadline" to deadline,
    )

    @Suppress("UNCHECKED_CAST")
    private fun mapToState(p: Map<String, Any?>): Pair<SpojniceState, Long>? {
        val phaseName = p["phase"] as? String ?: return null
        val pairs = (p["pairs"] as? List<*>)?.mapNotNull {
            val m = it as? Map<String, Any?> ?: return@mapNotNull null
            val l = m["l"] as? String ?: return@mapNotNull null
            val r = m["r"] as? String ?: return@mapNotNull null
            SpojnicePair(l, r)
        } ?: emptyList()
        val right = (p["right"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val correct = (p["correct"] as? List<*>)?.mapNotNull {
            val m = it as? Map<String, Any?> ?: return@mapNotNull null
            val k = numberOrNull(m["k"]) ?: return@mapNotNull null
            val v = numberOrNull(m["v"]) ?: return@mapNotNull null
            k to v
        }?.toMap() ?: emptyMap()
        val used = (p["used"] as? List<*>)?.mapNotNull { numberOrNull(it) }?.toSet() ?: emptySet()
        val selL = numberOrNull(p["selL"]) ?: -1
        val selR = numberOrNull(p["selR"]) ?: -1
        val deadline = numberOrNull(p["deadline"])?.toLong() ?: 0L
        val phase = runCatching { SpojnicePhase.valueOf(phaseName) }.getOrDefault(SpojnicePhase.PLAYING_STARTER)
        val maxSecs = if (phase == SpojnicePhase.PLAYING_OPPONENT) 30 else 45
        val s = SpojniceState(
            currentRound = numberOrNull(p["round"]) ?: 1,
            phase = phase,
            starterIsPlayer1 = p["starterP1"] != false,
            secondsLeft = secsLeft(deadline, maxSecs),
            player1Points = numberOrNull(p["p1"]) ?: 0,
            player2Points = numberOrNull(p["p2"]) ?: 0,
            pairs = pairs,
            rightOptions = right,
            correctLeftToRightIndex = correct,
            starterAttemptsUsedLeft = used,
            selectedLeftIndex = if (selL >= 0) selL else null,
            selectedRightIndex = if (selR >= 0) selR else null,
            infoMessage = p["info"] as? String ?: "",
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
        private const val GAME_TYPE = "SPOJNICE"
        private const val STARTER_MILLIS = 45_000L
        private const val OPPONENT_MILLIS = 30_000L
        private const val ROUND_END_MILLIS = 4_000L
    }
}
