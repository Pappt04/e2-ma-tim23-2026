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
import uns.ac.rs.team23.slagalica.repository.StatisticsRepository

enum class KoZnaZnaPhase {
    ROUND_INTRO,
    PLAYING,
    ROUND_END,
}

data class KoZnaZnaQuestion(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
)

data class KoZnaZnaState(
    val phase: KoZnaZnaPhase = KoZnaZnaPhase.ROUND_INTRO,
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val roundSecondsLeft: Int = 25,
    val questionSecondsLeft: Int = 5,
    val currentQuestionIndex: Int = 0,
    val questions: List<KoZnaZnaQuestion> = emptyList(),
    val player1SelectedIndex: Int? = null,
    val player2SelectedIndex: Int? = null,
    val player1AnswerSecond: Int? = null,
    val player2AnswerSecond: Int? = null,
    val infoMessage: String = "",
    val waitingForOpponent: Boolean = false,
)

/**
 * Real-time, host-authoritative "Ko zna zna".
 *
 * The host (player1) generates the shared 5-question set, writes it to the game-state document,
 * owns the per-question 5-second deadline, and resolves scoring from both players' real answers.
 * The guest (player2) only renders the shared document and writes its own pick. Neither client
 * runs a simulated opponent — both see the same questions, the same countdown, and the same result.
 */
class KoZnaZnaViewModel(
    private val matchRepository: MatchRepository,
    private val statisticsRepository: StatisticsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(KoZnaZnaState())
    val state: StateFlow<KoZnaZnaState> = _state.asStateFlow()

    private var started = false
    private var matchId: String = ""
    private var isHost: Boolean = false

    private var observerJob: Job? = null
    private var loopJob: Job? = null

    /** Latest shared state from Firestore (source of truth for rendering). */
    private var latest: GameStateDto? = null

    /** Host guard so each question is resolved exactly once. */
    private var lastResolvedIndex = -1

    /** Stats guard: highest question index already tallied into the local player's stats. */
    private var lastStatTalliedIndex = -1

    private fun recordStats(increments: Map<String, Long>) {
        if (matchId.isBlank() || MatchStore.isFriendly) return
        viewModelScope.launch { statisticsRepository.recordGameStats(GAME_TYPE, increments) }
    }

    /** Tally the local player's hit/miss for every question that has finished resolving. */
    private fun tallyLocalAnswers(gs: GameStateDto, questions: List<KoZnaZnaQuestion>) {
        if (matchId.isBlank() || MatchStore.isFriendly) return
        val myInput = if (isHost) gs.p1Input else gs.p2Input
        // Questions strictly before gs.index are done; at FINISHED the current one is done too.
        val resolvedUpTo = if (gs.phase == "FINISHED") gs.index else gs.index - 1
        while (lastStatTalliedIndex < resolvedUpTo) {
            val q = lastStatTalliedIndex + 1
            val correctIndex = questions.getOrNull(q)?.correctIndex ?: break
            val answer = answerFor(myInput, q)?.first
            recordStats(mapOf((if (answer == correctIndex) "correct" else "incorrect") to 1L))
            lastStatTalliedIndex = q
        }
    }

    /** Called once when the screen opens. */
    fun enter() {
        if (started) return
        started = true
        matchId = MatchStore.matchId
        isHost = MatchStore.isHost

        if (matchId.isBlank()) {
            // No live match (preview / edge case): play locally with no opponent.
            startLocalFallback()
            return
        }

        observerJob = viewModelScope.launch {
            matchRepository.observeGameState(matchId, GAME_TYPE).collect { gs ->
                latest = gs
                rebuildState()
            }
        }

        if (isHost) initGameAsHost()
        startLoop()
    }

    private fun initGameAsHost() {
        viewModelScope.launch {
            val questions = sampleQuestions().shuffled().take(QUESTION_COUNT)
            val payload = mapOf(
                "questions" to questions.map {
                    mapOf(
                        "text" to it.text,
                        "options" to it.options,
                        "correctIndex" to it.correctIndex,
                    )
                },
            )
            matchRepository.setGameState(
                matchId, GAME_TYPE,
                mapOf(
                    "gameType" to GAME_TYPE,
                    "hostId" to MatchStore.hostId,
                    "phase" to "PLAYING",
                    "round" to 1,
                    "index" to 0,
                    "deadlineAt" to System.currentTimeMillis() + QUESTION_MILLIS,
                    "payload" to payload,
                    "p1Input" to emptyMap<String, Any?>(),
                    "p2Input" to emptyMap<String, Any?>(),
                    "p1Score" to 0,
                    "p2Score" to 0,
                ),
            )
        }
    }

    /** Drives the synchronized countdown (both roles) and the host's resolution logic. */
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

    fun submitPlayer1Answer(optionIndex: Int) {
        val gs = latest ?: return
        if (gs.phase != "PLAYING") return
        val myInput = if (isHost) gs.p1Input else gs.p2Input
        // Already answered this question?
        if ((myInput["index"] as? Long)?.toInt() == gs.index) return

        val field = if (isHost) "p1Input" else "p2Input"
        viewModelScope.launch {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf(
                    field to mapOf(
                        "index" to gs.index,
                        "optionIndex" to optionIndex,
                        "atMillis" to System.currentTimeMillis(),
                    ),
                ),
            )
        }
    }

    // --- Host resolution ---

    private suspend fun maybeResolve() {
        val gs = latest ?: return
        if (gs.phase != "PLAYING") return
        if (gs.index <= lastResolvedIndex) return

        val questions = parseQuestions(gs.payload)
        if (questions.isEmpty() || gs.index >= questions.size) return

        val p1 = answerFor(gs.p1Input, gs.index)
        val p2 = answerFor(gs.p2Input, gs.index)
        val now = System.currentTimeMillis()
        val expired = gs.deadlineAt in 1..now
        val bothAnswered = p1 != null && p2 != null
        val abandonerId = MatchStore.abandonedById
        val p1Absent = abandonerId == MatchStore.player1Id
        val p2Absent = abandonerId == MatchStore.player2Id
        val canResolve = expired || bothAnswered ||
            (p1Absent && (p2 != null || expired)) ||
            (p2Absent && (p1 != null || expired))
        if (!canResolve) return

        lastResolvedIndex = gs.index

        val question = questions[gs.index]
        val (p1Delta, p2Delta) = scoreQuestion(question.correctIndex, p1, p2)
        val newP1 = clampScore(gs.p1Score + p1Delta)
        val newP2 = clampScore(gs.p2Score + p2Delta)
        val nextIndex = gs.index + 1

        if (nextIndex >= questions.size) {
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf("phase" to "FINISHED", "p1Score" to newP1, "p2Score" to newP2),
            )
            matchRepository.advanceMatch(matchId, GAME_TYPE, newP1, newP2)
        } else {
            // No need to clear p1Input/p2Input: answerFor() ignores inputs whose stored index
            // doesn't match the current question, so stale answers don't carry over.
            matchRepository.patchGameState(
                matchId, GAME_TYPE,
                mapOf(
                    "index" to nextIndex,
                    "deadlineAt" to System.currentTimeMillis() + QUESTION_MILLIS,
                    "p1Score" to newP1,
                    "p2Score" to newP2,
                ),
            )
        }
    }

    /** Spec scoring: both correct → faster +10; one correct → +10 and wrong −5; no correct → wrong −5. */
    private fun scoreQuestion(
        correctIndex: Int,
        p1: Pair<Int, Long>?,
        p2: Pair<Int, Long>?,
    ): Pair<Int, Int> {
        val p1Correct = p1?.first == correctIndex
        val p2Correct = p2?.first == correctIndex
        var p1Delta = 0
        var p2Delta = 0
        when {
            p1Correct && p2Correct -> {
                if ((p1!!.second) <= (p2!!.second)) p1Delta = 10 else p2Delta = 10
            }
            p1Correct -> {
                p1Delta = 10
                if (p2 != null) p2Delta = -5
            }
            p2Correct -> {
                p2Delta = 10
                if (p1 != null) p1Delta = -5
            }
            else -> {
                if (p1 != null) p1Delta = -5
                if (p2 != null) p2Delta = -5
            }
        }
        return p1Delta to p2Delta
    }

    // --- Rendering: map the shared document into UI state ---

    private fun rebuildState() {
        val gs = latest
        if (gs == null) {
            _state.update { it.copy(phase = KoZnaZnaPhase.ROUND_INTRO, waitingForOpponent = true) }
            return
        }
        val questions = parseQuestions(gs.payload)
        tallyLocalAnswers(gs, questions)
        val now = System.currentTimeMillis()
        val qLeft = if (gs.deadlineAt > 0)
            (((gs.deadlineAt - now) + 999) / 1000).toInt().coerceIn(0, 5) else 5
        val questionsLeft = (questions.size - gs.index - 1).coerceAtLeast(0)
        val roundLeft = (qLeft + questionsLeft * 5).coerceAtLeast(0)

        val p1 = answerFor(gs.p1Input, gs.index)
        val p2 = answerFor(gs.p2Input, gs.index)
        val mine = if (isHost) p1 else p2
        val theirs = if (isHost) p2 else p1
        val myScore = if (isHost) gs.p1Score else gs.p2Score
        val theirScore = if (isHost) gs.p2Score else gs.p1Score

        val phase = when (gs.phase) {
            "FINISHED" -> KoZnaZnaPhase.ROUND_END
            "PLAYING" -> KoZnaZnaPhase.PLAYING
            else -> KoZnaZnaPhase.ROUND_INTRO
        }

        _state.update {
            it.copy(
                phase = phase,
                questions = questions,
                currentQuestionIndex = gs.index.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                player1Points = myScore,
                player2Points = theirScore,
                questionSecondsLeft = qLeft,
                roundSecondsLeft = roundLeft,
                player1SelectedIndex = mine?.first,
                player2SelectedIndex = theirs?.first,
                player1AnswerSecond = null,
                player2AnswerSecond = null,
                waitingForOpponent = questions.isEmpty(),
                infoMessage = if (mine != null && theirs == null && gs.phase == "PLAYING")
                    "Waiting for opponent..." else "",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQuestions(payload: Map<String, Any?>): List<KoZnaZnaQuestion> {
        val raw = payload["questions"] as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { q ->
            val text = q["text"] as? String ?: return@mapNotNull null
            val options = (q["options"] as? List<*>)?.map { it.toString() } ?: return@mapNotNull null
            val correct = (q["correctIndex"] as? Long)?.toInt()
                ?: (q["correctIndex"] as? Int) ?: return@mapNotNull null
            KoZnaZnaQuestion(text, options, correct)
        }
    }

    private fun answerFor(input: Map<String, Any?>, idx: Int): Pair<Int, Long>? {
        val i = (input["index"] as? Long)?.toInt() ?: (input["index"] as? Int) ?: return null
        if (i != idx) return null
        val opt = (input["optionIndex"] as? Long)?.toInt()
            ?: (input["optionIndex"] as? Int) ?: return null
        val at = (input["atMillis"] as? Long) ?: 0L
        return opt to at
    }

    private fun clampScore(v: Int): Int = v.coerceIn(-25, 50)

    // --- Local fallback (no live match) ---

    private fun startLocalFallback() {
        val questions = sampleQuestions().shuffled().take(QUESTION_COUNT)
        _state.update {
            it.copy(
                phase = KoZnaZnaPhase.PLAYING,
                questions = questions,
                currentQuestionIndex = 0,
                questionSecondsLeft = 5,
                roundSecondsLeft = 25,
                waitingForOpponent = false,
            )
        }
    }

    fun resetToIntro() { /* no-op: round flow is driven by the shared document */ }

    override fun onCleared() {
        observerJob?.cancel()
        loopJob?.cancel()
        super.onCleared()
    }

    private fun sampleQuestions(): List<KoZnaZnaQuestion> = listOf(
        KoZnaZnaQuestion("Which planet is known as the Red Planet?", listOf("Mars", "Venus", "Jupiter", "Mercury"), 0),
        KoZnaZnaQuestion("How many continents are there on Earth?", listOf("5", "6", "7", "8"), 2),
        KoZnaZnaQuestion("What is the capital city of Italy?", listOf("Milan", "Rome", "Naples", "Turin"), 1),
        KoZnaZnaQuestion("Which gas do plants mostly use for photosynthesis?", listOf("Oxygen", "Hydrogen", "Carbon Dioxide", "Nitrogen"), 2),
        KoZnaZnaQuestion("Which ocean is the largest?", listOf("Atlantic", "Indian", "Arctic", "Pacific"), 3),
        KoZnaZnaQuestion("What is the chemical symbol for gold?", listOf("Go", "Gd", "Au", "Ag"), 2),
        KoZnaZnaQuestion("Who wrote 'Romeo and Juliet'?", listOf("Dickens", "Shakespeare", "Tolstoy", "Homer"), 1),
    )

    companion object {
        private const val GAME_TYPE = "KO_ZNA_ZNA"
        private const val QUESTION_COUNT = 5
        private const val QUESTION_MILLIS = 5_000L
    }
}
