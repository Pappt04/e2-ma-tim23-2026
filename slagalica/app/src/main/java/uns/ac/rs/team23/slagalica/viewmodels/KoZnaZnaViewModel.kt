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
)

class KoZnaZnaViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        KoZnaZnaState(
            questions = sampleQuestions(),
        ),
    )
    val state: StateFlow<KoZnaZnaState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var questionElapsedSeconds = 0
    private var opponentAnswerDelay = 2
    private var opponentAnswerIndex = 0

    fun startRound() {
        timerJob?.cancel()
        questionElapsedSeconds = 0
        prepareOpponentPlan()
        _state.update {
            it.copy(
                phase = KoZnaZnaPhase.PLAYING,
                player1Points = 0,
                player2Points = 0,
                roundSecondsLeft = 25,
                questionSecondsLeft = 5,
                currentQuestionIndex = 0,
                player1SelectedIndex = null,
                player2SelectedIndex = null,
                player1AnswerSecond = null,
                player2AnswerSecond = null,
                infoMessage = "",
            )
        }
        startTickLoop()
    }

    fun submitPlayer1Answer(optionIndex: Int) {
        val s = _state.value
        if (s.phase != KoZnaZnaPhase.PLAYING || s.player1SelectedIndex != null) return

        _state.update {
            it.copy(
                player1SelectedIndex = optionIndex,
                player1AnswerSecond = questionElapsedSeconds,
            )
        }
        tryResolveQuestion()
    }

    private fun startTickLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.phase == KoZnaZnaPhase.PLAYING) {
                delay(1000)
                val current = _state.value
                if (current.phase != KoZnaZnaPhase.PLAYING) break

                questionElapsedSeconds++
                val nextRoundSeconds = current.roundSecondsLeft - 1
                val nextQuestionSeconds = current.questionSecondsLeft - 1

                _state.update {
                    it.copy(
                        roundSecondsLeft = nextRoundSeconds.coerceAtLeast(0),
                        questionSecondsLeft = nextQuestionSeconds.coerceAtLeast(0),
                    )
                }

                maybeAutoAnswerForOpponent()

                if (_state.value.phase != KoZnaZnaPhase.PLAYING) break

                val st = _state.value
                val expired = st.questionSecondsLeft <= 0 || st.roundSecondsLeft <= 0
                val bothAnswered =
                    st.player1SelectedIndex != null && st.player2SelectedIndex != null
                if (expired && !bothAnswered) {
                    resolveAndAdvanceQuestion()
                }
            }
        }
    }

    private fun maybeAutoAnswerForOpponent() {
        val s = _state.value
        if (s.player2SelectedIndex != null) return
        if (questionElapsedSeconds < opponentAnswerDelay) return

        _state.update {
            it.copy(
                player2SelectedIndex = opponentAnswerIndex,
                player2AnswerSecond = questionElapsedSeconds,
            )
        }
        tryResolveQuestion()
    }

    private fun tryResolveQuestion() {
        val s = _state.value
        if (s.phase != KoZnaZnaPhase.PLAYING) return
        val bothAnswered = s.player1SelectedIndex != null && s.player2SelectedIndex != null
        if (!bothAnswered) return
        resolveAndAdvanceQuestion()
    }

    private fun clampScore(v: Int): Int = v.coerceIn(-25, 50)

    /**
     * Resolves the current question once, applies spec score limits, then either ends the round
     * or advances to the next question in the same update (avoids double-counting during delays).
     */
    private fun resolveAndAdvanceQuestion() {
        val s = _state.value
        if (s.phase != KoZnaZnaPhase.PLAYING) return

        val question = s.questions[s.currentQuestionIndex]
        val p1Answer = s.player1SelectedIndex
        val p2Answer = s.player2SelectedIndex
        val p1Correct = p1Answer == question.correctIndex
        val p2Correct = p2Answer == question.correctIndex
        val p1Answered = p1Answer != null
        val p2Answered = p2Answer != null

        var p1Delta = 0
        var p2Delta = 0
        when {
            p1Correct && p2Correct -> {
                val p1Time = s.player1AnswerSecond ?: Int.MAX_VALUE
                val p2Time = s.player2AnswerSecond ?: Int.MAX_VALUE
                if (p1Time <= p2Time) p1Delta = 10 else p2Delta = 10
            }
            p1Correct -> {
                p1Delta = 10
                if (p2Answered && !p2Correct) p2Delta -= 5
            }
            p2Correct -> {
                p2Delta = 10
                if (p1Answered && !p1Correct) p1Delta -= 5
            }
            p1Answered || p2Answered -> {
                if (p1Answered && !p1Correct) p1Delta -= 5
                if (p2Answered && !p2Correct) p2Delta -= 5
            }
        }

        val message =
            when {
                p1Correct && p2Correct ->
                    if (p1Delta == 10) {
                        "Correct! +10 points"
                    } else {
                        "Correct, but you were not the fastest. +0 points"
                    }
                p1Correct -> "Correct! +10 points"
                p2Correct && p1Answered && !p1Correct -> "Wrong answer. -5 points"
                p2Correct && !p1Answered -> "No points this question."
                p1Answered && !p1Correct -> "Wrong answer. -5 points"
                !p1Answered && !p2Answered -> "No answer in time. No change to your score."
                else -> "No change to your score."
            }

        val nextIndex = s.currentQuestionIndex + 1
        val finished = nextIndex >= s.questions.size || s.roundSecondsLeft <= 0
        val newP1 = clampScore(s.player1Points + p1Delta)
        val newP2 = clampScore(s.player2Points + p2Delta)

        if (finished) {
            _state.update {
                it.copy(
                    player1Points = newP1,
                    player2Points = newP2,
                    infoMessage = message,
                    phase = KoZnaZnaPhase.ROUND_END,
                )
            }
        } else {
            questionElapsedSeconds = 0
            _state.update {
                it.copy(
                    player1Points = newP1,
                    player2Points = newP2,
                    infoMessage = message,
                    currentQuestionIndex = nextIndex,
                    questionSecondsLeft = 5,
                    player1SelectedIndex = null,
                    player2SelectedIndex = null,
                    player1AnswerSecond = null,
                    player2AnswerSecond = null,
                )
            }
            prepareOpponentPlan()
        }
    }

    private fun prepareOpponentPlan() {
        val s = _state.value
        val question = s.questions[s.currentQuestionIndex]
        val questionNumber = s.currentQuestionIndex + 1
        opponentAnswerDelay = (2 + (questionNumber % 3)).coerceAtMost(5)
        opponentAnswerIndex = when (questionNumber) {
            1 -> question.correctIndex
            2 -> (question.correctIndex + 1) % 4
            3 -> question.correctIndex
            4 -> (question.correctIndex + 2) % 4
            else -> question.correctIndex
        }
    }

    fun resetToIntro() {
        timerJob?.cancel()
        _state.update {
            it.copy(
                phase = KoZnaZnaPhase.ROUND_INTRO,
                player1Points = 0,
                player2Points = 0,
                roundSecondsLeft = 25,
                questionSecondsLeft = 5,
                currentQuestionIndex = 0,
                player1SelectedIndex = null,
                player2SelectedIndex = null,
                player1AnswerSecond = null,
                player2AnswerSecond = null,
                infoMessage = "",
            )
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private fun sampleQuestions(): List<KoZnaZnaQuestion> = listOf(
        KoZnaZnaQuestion(
            text = "Which planet is known as the Red Planet?",
            options = listOf("Mars", "Venus", "Jupiter", "Mercury"),
            correctIndex = 0,
        ),
        KoZnaZnaQuestion(
            text = "How many continents are there on Earth?",
            options = listOf("5", "6", "7", "8"),
            correctIndex = 2,
        ),
        KoZnaZnaQuestion(
            text = "What is the capital city of Italy?",
            options = listOf("Milan", "Rome", "Naples", "Turin"),
            correctIndex = 1,
        ),
        KoZnaZnaQuestion(
            text = "Which gas do plants mostly use for photosynthesis?",
            options = listOf("Oxygen", "Hydrogen", "Carbon Dioxide", "Nitrogen"),
            correctIndex = 2,
        ),
        KoZnaZnaQuestion(
            text = "Which ocean is the largest?",
            options = listOf("Atlantic", "Indian", "Arctic", "Pacific"),
            correctIndex = 3,
        ),
    )
}
