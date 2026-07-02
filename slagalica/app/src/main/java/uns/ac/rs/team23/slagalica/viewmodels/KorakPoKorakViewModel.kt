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
import uns.ac.rs.team23.slagalica.models.KorakPoKorakQuestion
import uns.ac.rs.team23.slagalica.network.dto.GameStateDto
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.repository.StatisticsRepository

enum class KorakPhase { RoundIntro, Loading, PlayerTurn, OpponentChance, RoundEnd, GameOver }

data class KorakPoKorakState(
    val currentRound: Int = 1,
    val phase: KorakPhase = KorakPhase.RoundIntro,
    val currentStep: Int = 1,
    val revealedClues: List<String> = emptyList(),
    val allClues: List<String> = emptyList(),
    val targetAnswer: String = "",
    val timeLeft: Int = 10,
    val currentAnswer: String = "",
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val roundCorrectAnswer: String = "",
    val showWrongFeedback: Boolean = false,
    val errorMessage: String? = null,
    val p1Ready: Boolean = false,
    val p2Ready: Boolean = false,
)

/**
 * Real-time, host-authoritative "Korak po korak".
 *
 * The host fetches one question, writes the shared state into the game-state `payload`, owns the
 * per-step 10s deadline (revealing one clue per step) and the opponent's 10s steal window. The
 * active player validates its own answer locally for instant feedback and, on a correct answer,
 * tells the host to finish the round; the host applies all scoring and round transitions. Round 1
 * is played by player 1, round 2 by player 2. `currentAnswer`/`showWrongFeedback` are local UI only.
 */
class KorakPoKorakViewModel(
    private val gameRepository: GameRepository,
    private val matchRepository: MatchRepository,
    private val statisticsRepository: StatisticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(KorakPoKorakState())
    val state: StateFlow<KorakPoKorakState> = _state.asStateFlow()

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

    /** Host-only: the full clue list / answer for the active question. */
    private var hostClues: List<String> = emptyList()

    /** Stats guard so the active player's solve is recorded at most once per round. */
    private var statSolveRecorded = false

    private fun recordStats(increments: Map<String, Long>) {
        if (matchId.isBlank() || MatchStore.isFriendly) return
        viewModelScope.launch { statisticsRepository.recordGameStats(GAME_TYPE, increments) }
    }

    fun enter() {
        if (started) return
        started = true
        matchId = MatchStore.matchId
        isHost = MatchStore.isHost

        if (matchId.isNotBlank()) {
            _state.update { it.copy(phase = KorakPhase.Loading) }
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
        if (deadlineAt > 0 && (s.phase == KorakPhase.PlayerTurn || s.phase == KorakPhase.OpponentChance)) {
            val left = (((deadlineAt - now) + 999) / 1000).toInt().coerceIn(0, 10)
            if (left != s.timeLeft) _state.update { it.copy(timeLeft = left) }
        }
        if (authoritative && deadlineAt > 0 && now >= deadlineAt && deadlineAt != lastHandledDeadline) {
            lastHandledDeadline = deadlineAt
            handleTimeout()
        }
        if (isHost) processGuestIntent()
        maybeFastForwardAbandoned()
    }

    private fun maybeFastForwardAbandoned() {
        if (!authoritative || !MatchStore.opponentAbandoned) return
        val s = _state.value
        when {
            s.phase == KorakPhase.OpponentChance -> applyFinish(0, scoredByOpponent = false)
            s.phase == KorakPhase.RoundEnd -> {
                if (!s.p2Ready) commit(s.copy(p2Ready = true), 0)
                checkBothReady()
            }
        }
    }

    // --- Public actions (screen) ---

    fun beginRound() { /* host-driven: round auto-starts */ }

    fun markReady() {
        val s = _state.value
        if (s.phase != KorakPhase.RoundEnd) return
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

    /** @deprecated use [markReady] */
    fun prepareNextRound() = markReady()

    fun onAnswerChange(text: String) {
        _state.update { it.copy(currentAnswer = text, showWrongFeedback = false) }
    }

    fun submitAnswer() {
        val s = _state.value
        if (!localActive(s)) return
        if (s.phase != KorakPhase.PlayerTurn && s.phase != KorakPhase.OpponentChance) return
        if (!matchesAnswer(s.currentAnswer, s.targetAnswer)) {
            _state.update { it.copy(currentAnswer = "", showWrongFeedback = true) }
            return
        }
        val opp = s.phase == KorakPhase.OpponentChance
        val points = if (opp) 5 else 20 - 2 * (s.currentStep - 1)
        // Spec stat: which step the active player guessed the concept at.
        if (!opp && !statSolveRecorded) {
            statSolveRecorded = true
            val step = s.currentStep.coerceIn(1, 7)
            recordStats(mapOf("step$step" to 1L, "solved" to 1L))
        }
        _state.update { it.copy(currentAnswer = "", showWrongFeedback = false) }
        if (authoritative) applyFinish(points, opp)
        else sendIntent(mapOf("t" to "solve", "pts" to points, "opp" to opp))
    }

    // --- Apply (authoritative) ---

    private fun applyFinish(points: Int, scoredByOpponent: Boolean) {
        val s = _state.value
        val activeIsP1 = s.currentRound == 1
        val (newP1, newP2) = when {
            !scoredByOpponent && activeIsP1 -> s.player1Points + points to s.player2Points
            !scoredByOpponent && !activeIsP1 -> s.player1Points to s.player2Points + points
            scoredByOpponent && activeIsP1 -> s.player1Points to s.player2Points + points
            else -> s.player1Points + points to s.player2Points
        }
        val over = s.currentRound >= 2
        commit(
            s.copy(
                phase = if (over) KorakPhase.RoundEnd else KorakPhase.RoundEnd,
                player1Points = newP1, player2Points = newP2,
                roundCorrectAnswer = s.targetAnswer,
                revealedClues = hostClues.ifEmpty { s.revealedClues },
                p1Ready = false, p2Ready = false,
            ),
            0,
        )
        if (over) { /* wait for both ready before finishMatch */ }
    }

    private fun checkBothReady() {
        val s = _state.value
        if (s.phase != KorakPhase.RoundEnd || !s.p1Ready || !s.p2Ready) return
        if (roundStarting) return
        roundStarting = true
        if (s.currentRound < 2) {
            hostStartRound(2)
        } else {
            commit(s.copy(phase = KorakPhase.GameOver), 0)
            finishMatch(s.player1Points, s.player2Points)
        }
        roundStarting = false
    }

    private fun handleTimeout() {
        val s = _state.value
        when (s.phase) {
            KorakPhase.PlayerTurn -> {
                val nextStep = s.currentStep + 1
                if (nextStep <= hostClues.size) {
                    commit(s.copy(currentStep = nextStep, revealedClues = hostClues.take(nextStep)), now() + STEP_MILLIS)
                } else {
                    commit(s.copy(phase = KorakPhase.OpponentChance), now() + STEP_MILLIS)
                }
            }
            KorakPhase.OpponentChance -> applyFinish(0, scoredByOpponent = false)
            else -> {}
        }
    }

    private fun finishMatch(p1: Int, p2: Int) {
        if (isHost && !advanced) {
            advanced = true
            viewModelScope.launch { matchRepository.recordGameResult(matchId, GAME_TYPE, p1, p2) }
        }
    }

    private fun hostStartRound(round: Int) {
        if (!authoritative) return
        viewModelScope.launch {
            val q = gameRepository.getKorakPoKorakQuestion().getOrNull()
            if (q == null || q.clues.isEmpty()) {
                val msg = "Failed to load question"
                _state.update { it.copy(errorMessage = msg, phase = KorakPhase.RoundIntro) }
                if (isHost && matchId.isNotBlank()) {
                    matchRepository.setGameState(
                        matchId, GAME_TYPE,
                        mapOf(
                            "gameType" to GAME_TYPE,
                            "hostId" to MatchStore.hostId,
                            "phase" to "ERROR",
                            "payload" to mapOf("phase" to "ERROR", "message" to msg),
                        ),
                    )
                }
                return@launch
            }
            hostClues = q.clues
            val s = buildRoundState(round, q)
            val deadline = now() + STEP_MILLIS
            lastHandledDeadline = -1
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
                deadlineAt = deadline
                _state.value = s
            }
        }
    }

    private fun buildRoundState(round: Int, q: KorakPoKorakQuestion): KorakPoKorakState {
        val prev = _state.value
        statSolveRecorded = false
        return KorakPoKorakState(
            currentRound = round,
            phase = KorakPhase.PlayerTurn,
            currentStep = 1,
            revealedClues = listOf(q.clues[0]),
            allClues = q.clues,
            targetAnswer = q.answer,
            timeLeft = 10,
            player1Points = if (round == 1) 0 else prev.player1Points,
            player2Points = if (round == 1) 0 else prev.player2Points,
        )
    }

    // --- Commit / sync ---

    private fun commit(s: KorakPoKorakState, deadline: Long) {
        deadlineAt = deadline
        if (deadline > now()) lastHandledDeadline = -1
        _state.update { cur -> s.copy(currentAnswer = cur.currentAnswer, showWrongFeedback = cur.showWrongFeedback) }
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
                if (s.phase == KorakPhase.RoundEnd && !s.p2Ready) {
                    commit(s.copy(p2Ready = true), 0)
                    checkBothReady()
                }
            }
            "solve" -> {
                val pts = numberOrNull(gs.p2Input["pts"]) ?: 0
                val opp = gs.p2Input["opp"] == true
                applyFinish(pts, opp)
            }
        }
    }

    private fun rebuildState(gs: GameStateDto) {
        val phaseName = gs.payload["phase"] as? String
        if (phaseName == "ERROR") {
            _state.update {
                it.copy(
                    phase = KorakPhase.RoundIntro,
                    errorMessage = gs.payload["message"] as? String ?: "Failed to load",
                )
            }
            return
        }
        val (s, _) = mapToState(gs.payload, gs) ?: return
        deadlineAt = effectiveDeadline(gs, gs.payload)
        if (isHost && s.allClues.isNotEmpty()) hostClues = s.allClues
        _state.update { cur ->
            s.copy(currentAnswer = cur.currentAnswer, showWrongFeedback = cur.showWrongFeedback,
                timeLeft = secsLeft(deadlineAt, 10))
        }
    }

    // --- Turn helpers ---

    private fun activeIsP1(s: KorakPoKorakState): Boolean = when (s.phase) {
        KorakPhase.PlayerTurn -> s.currentRound == 1
        KorakPhase.OpponentChance -> s.currentRound != 1
        else -> s.currentRound == 1
    }

    private fun localActive(s: KorakPoKorakState): Boolean =
        matchId.isBlank() || (activeIsP1(s) == isHost)

    private fun matchesAnswer(input: String, target: String): Boolean {
        val norm = input.trim().lowercase()
        val t = target.trim().lowercase()
        return norm.isNotEmpty() && (norm == t || t.split(" ").any { it == norm })
    }

    // --- Serialization ---

    private fun stateToMap(s: KorakPoKorakState, deadline: Long): Map<String, Any?> = mapOf(
        "round" to s.currentRound,
        "phase" to s.phase.name,
        "step" to s.currentStep,
        "revealed" to s.revealedClues,
        "allClues" to s.allClues.ifEmpty { s.revealedClues },
        "target" to s.targetAnswer,
        "correct" to s.roundCorrectAnswer,
        "p1" to s.player1Points,
        "p2" to s.player2Points,
        "p1Ready" to s.p1Ready,
        "p2Ready" to s.p2Ready,
        "deadline" to deadline,
    )

    private fun mapToState(p: Map<String, Any?>, gs: GameStateDto): Pair<KorakPoKorakState, Long>? {
        val phaseName = p["phase"] as? String ?: return null
        val revealed = (p["revealed"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val allClues = (p["allClues"] as? List<*>)?.map { it.toString() } ?: revealed
        val deadline = effectiveDeadline(gs, p)
        val s = KorakPoKorakState(
            currentRound = numberOrNull(p["round"]) ?: 1,
            phase = runCatching { KorakPhase.valueOf(phaseName) }.getOrDefault(KorakPhase.PlayerTurn),
            currentStep = numberOrNull(p["step"]) ?: 1,
            revealedClues = revealed,
            allClues = allClues,
            targetAnswer = p["target"] as? String ?: "",
            roundCorrectAnswer = p["correct"] as? String ?: "",
            player1Points = numberOrNull(p["p1"]) ?: 0,
            player2Points = numberOrNull(p["p2"]) ?: 0,
            timeLeft = secsLeft(deadline, 10),
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
        private const val GAME_TYPE = "KORAK_PO_KORAK"
        private const val STEP_MILLIS = 10_000L
        private const val ROUND_END_MILLIS = 4_000L
    }
}
