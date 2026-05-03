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

enum class KorakPhase { RoundIntro, PlayerTurn, OpponentChance, RoundEnd, GameOver }

data class KorakPoKorakState(
    val currentRound: Int = 1,
    val phase: KorakPhase = KorakPhase.RoundIntro,
    val currentStep: Int = 1,
    val revealedClues: List<String> = emptyList(),
    val targetAnswer: String = "",
    val timeLeft: Int = 10,
    val currentAnswer: String = "",
    val player1Points: Int = 0,
    val player2Points: Int = 0,
    val roundCorrectAnswer: String = "",
    val showWrongFeedback: Boolean = false,
)

private data class KorakQuestion(
    val answer: String,
    val steps: List<String>,
)

class KorakPoKorakViewModel : ViewModel() {
    private val questions =
        listOf(
            KorakQuestion(
                answer = "Nikola Tesla",
                steps =
                    listOf(
                        "Secured backing from J.P. Morgan for his Wardenclyffe Tower",
                        "His New York laboratory was destroyed by fire in 1895",
                        "Demonstrated the first wireless radio transmission",
                        "Born in Smiljan, in present-day Croatia, in 1856",
                        "Had a famous conflict with Thomas Edison (AC vs DC)",
                        "Pioneer of alternating current (AC) technology",
                        "Serbian-American inventor; the SI unit of magnetic flux density bears his name",
                    ),
            ),
            KorakQuestion(
                answer = "Albert Einstein",
                steps =
                    listOf(
                        "Renounced his German citizenship in 1896 to avoid military service",
                        "Worked as a patent clerk in Bern, Switzerland",
                        "Played the violin and loved sailing as hobbies",
                        "Born in Ulm, Germany, in 1879",
                        "Awarded the Nobel Prize in Physics in 1921",
                        "Formulated the equation E = mc²",
                        "German-born theoretical physicist who developed the theory of relativity",
                    ),
            ),
        )

    private val _state =
        MutableStateFlow(
            KorakPoKorakState(targetAnswer = questions[0].answer),
        )
    val state: StateFlow<KorakPoKorakState> = _state.asStateFlow()

    private var timerJob: Job? = null

    fun beginRound() {
        val question = questions[_state.value.currentRound - 1]
        _state.update {
            it.copy(
                phase = KorakPhase.PlayerTurn,
                currentStep = 1,
                revealedClues = listOf(question.steps[0]),
                timeLeft = 10,
                currentAnswer = "",
                showWrongFeedback = false,
            )
        }
        startTimer()
    }

    fun onAnswerChange(text: String) {
        _state.update { it.copy(currentAnswer = text, showWrongFeedback = false) }
    }

    fun submitAnswer() {
        val s = _state.value
        val correct = matchesAnswer(s.currentAnswer, s.targetAnswer)
        if (!correct) {
            _state.update { it.copy(currentAnswer = "", showWrongFeedback = true) }
            return
        }
        timerJob?.cancel()
        val points =
            if (s.phase == KorakPhase.OpponentChance) {
                5
            } else {
                20 - 2 * (s.currentStep - 1)
            }
        finishRound(points, scoredByOpponent = s.phase == KorakPhase.OpponentChance)
    }

    fun prepareNextRound() {
        _state.update {
            it.copy(
                currentRound = 2,
                phase = KorakPhase.RoundIntro,
                targetAnswer = questions[1].answer,
                currentAnswer = "",
                revealedClues = emptyList(),
                showWrongFeedback = false,
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob =
            viewModelScope.launch {
                for (i in 9 downTo 0) {
                    delay(1000)
                    _state.update { it.copy(timeLeft = i) }
                }
                onTimerExpired()
            }
    }

    private fun onTimerExpired() {
        val s = _state.value
        when (s.phase) {
            KorakPhase.PlayerTurn -> {
                val nextStep = s.currentStep + 1
                if (nextStep <= 7) {
                    val clues = questions[s.currentRound - 1].steps.take(nextStep)
                    _state.update {
                        it.copy(
                            currentStep = nextStep,
                            revealedClues = clues,
                            timeLeft = 10,
                            showWrongFeedback = false,
                        )
                    }
                    startTimer()
                } else {
                    _state.update {
                        it.copy(
                            phase = KorakPhase.OpponentChance,
                            timeLeft = 10,
                            showWrongFeedback = false,
                        )
                    }
                    startTimer()
                }
            }

            KorakPhase.OpponentChance -> {
                finishRound(0, scoredByOpponent = false)
            }

            else -> {}
        }
    }

    private fun finishRound(
        points: Int,
        scoredByOpponent: Boolean,
    ) {
        timerJob?.cancel()
        val s = _state.value
        val activeIsP1 = s.currentRound == 1

        val (newP1, newP2) =
            when {
                !scoredByOpponent && activeIsP1 -> s.player1Points + points to s.player2Points
                !scoredByOpponent && !activeIsP1 -> s.player1Points to s.player2Points + points
                scoredByOpponent && activeIsP1 -> s.player1Points to s.player2Points + points
                else -> s.player1Points + points to s.player2Points
            }

        _state.update {
            it.copy(
                phase = if (s.currentRound == 1) KorakPhase.RoundEnd else KorakPhase.GameOver,
                player1Points = newP1,
                player2Points = newP2,
                roundCorrectAnswer = s.targetAnswer,
                revealedClues = questions[s.currentRound - 1].steps,
            )
        }
    }

    private fun matchesAnswer(
        input: String,
        target: String,
    ): Boolean {
        val norm = input.trim().lowercase()
        val t = target.trim().lowercase()
        return norm == t || t.split(" ").any { it == norm }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
