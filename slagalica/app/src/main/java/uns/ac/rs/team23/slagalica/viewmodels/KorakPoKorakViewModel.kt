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
import uns.ac.rs.team23.slagalica.models.KorakPoKorakQuestion
import uns.ac.rs.team23.slagalica.repository.GameRepository

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
)

class KorakPoKorakViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(KorakPoKorakState())
    val state: StateFlow<KorakPoKorakState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var loadedQuestion: KorakPoKorakQuestion? = null

    init {
        preloadQuestion()
    }

    private fun preloadQuestion() {
        viewModelScope.launch {
            gameRepository.getKorakPoKorakQuestion()
                .onSuccess { loadedQuestion = it }
                .onFailure { _state.update { s -> s.copy(errorMessage = it.message) } }
        }
    }

    fun beginRound() {
        val question = loadedQuestion
        if (question == null) {
            _state.update { it.copy(phase = KorakPhase.Loading) }
            viewModelScope.launch {
                gameRepository.getKorakPoKorakQuestion()
                    .onSuccess { q ->
                        loadedQuestion = q
                        startTurn(q)
                    }
                    .onFailure { err ->
                        _state.update { it.copy(phase = KorakPhase.RoundIntro, errorMessage = err.message) }
                    }
            }
            return
        }
        startTurn(question)
    }

    private fun startTurn(question: KorakPoKorakQuestion) {
        _state.update {
            it.copy(
                phase = KorakPhase.PlayerTurn,
                targetAnswer = question.answer,
                allClues = question.clues,
                currentStep = 1,
                revealedClues = listOf(question.clues[0]),
                timeLeft = 10,
                currentAnswer = "",
                showWrongFeedback = false,
                errorMessage = null,
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
        val points = if (s.phase == KorakPhase.OpponentChance) {
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
                targetAnswer = "",
                currentAnswer = "",
                revealedClues = emptyList(),
                allClues = emptyList(),
                showWrongFeedback = false,
                errorMessage = null,
            )
        }
        loadedQuestion = null
        preloadQuestion()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
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
                if (nextStep <= s.allClues.size) {
                    _state.update {
                        it.copy(
                            currentStep = nextStep,
                            revealedClues = s.allClues.take(nextStep),
                            timeLeft = 10,
                            showWrongFeedback = false,
                        )
                    }
                    startTimer()
                } else {
                    _state.update {
                        it.copy(phase = KorakPhase.OpponentChance, timeLeft = 10, showWrongFeedback = false)
                    }
                    startTimer()
                }
            }
            KorakPhase.OpponentChance -> finishRound(0, scoredByOpponent = false)
            else -> {}
        }
    }

    private fun finishRound(points: Int, scoredByOpponent: Boolean) {
        timerJob?.cancel()
        val s = _state.value
        val activeIsP1 = s.currentRound == 1

        val (newP1, newP2) = when {
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
                revealedClues = s.allClues,
            )
        }
    }

    private fun matchesAnswer(input: String, target: String): Boolean {
        val norm = input.trim().lowercase()
        val t = target.trim().lowercase()
        return norm == t || t.split(" ").any { it == norm }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
