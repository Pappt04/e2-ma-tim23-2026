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

/** Wrong pair from starter; opponent phase can see these as hints (optional). */
data class SpojniceWrongAttempt(
    val leftIndex: Int,
    val rightIndex: Int,
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
    val starterWrongAttempts: List<SpojniceWrongAttempt> = emptyList(),
    val selectedLeftIndex: Int? = null,
    val selectedRightIndex: Int? = null,
    val infoMessage: String = "",
)

class SpojniceViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        SpojniceState(
            pairs = samplePairsRound1(),
            rightOptions = samplePairsRound1().map { it.right }.shuffled(),
        ),
    )
    val state: StateFlow<SpojniceState> = _state.asStateFlow()

    private var opponentTimerJob: Job? = null

    fun startRound() {
        opponentTimerJob?.cancel()
        val round = _state.value.currentRound
        val pairs = if (round == 1) samplePairsRound1() else samplePairsRound2()
        val starterIsP1 = round == 1
        _state.update {
            it.copy(
                phase = SpojnicePhase.PLAYING_STARTER,
                starterIsPlayer1 = starterIsP1,
                secondsLeft = 30,
                pairs = pairs,
                rightOptions = pairs.map { p -> p.right }.shuffled(),
                correctLeftToRightIndex = emptyMap(),
                starterAttemptsUsedLeft = emptySet(),
                starterWrongAttempts = emptyList(),
                selectedLeftIndex = null,
                selectedRightIndex = null,
                infoMessage = if (starterIsP1) {
                    "Starter: connect each left term once. Only correct matches stay visible."
                } else {
                    "Starter: connect each left term once. Only correct matches stay visible."
                },
            )
        }
    }

    fun selectLeft(index: Int) {
        val s = _state.value
        if (s.phase != SpojnicePhase.PLAYING_STARTER && s.phase != SpojnicePhase.PLAYING_OPPONENT) return
        if (index in s.correctLeftToRightIndex.keys) return
        if (s.phase == SpojnicePhase.PLAYING_STARTER && index in s.starterAttemptsUsedLeft) return
        _state.update { it.copy(selectedLeftIndex = index) }
        tryMatch()
    }

    fun selectRight(index: Int) {
        val s = _state.value
        if (s.phase != SpojnicePhase.PLAYING_STARTER && s.phase != SpojnicePhase.PLAYING_OPPONENT) return
        if (s.correctLeftToRightIndex.values.contains(index)) return
        _state.update { it.copy(selectedRightIndex = index) }
        tryMatch()
    }

    private fun tryMatch() {
        val s = _state.value
        val li = s.selectedLeftIndex ?: return
        val ri = s.selectedRightIndex ?: return
        if (li in s.correctLeftToRightIndex.keys) return
        if (s.correctLeftToRightIndex.values.contains(ri)) return

        val expectedRight = s.pairs[li].right
        val pickedRight = s.rightOptions[ri]
        val correct = expectedRight == pickedRight

        when (s.phase) {
            SpojnicePhase.PLAYING_STARTER -> {
                if (li in s.starterAttemptsUsedLeft) return
                val starterIsP1 = s.starterIsPlayer1
                if (correct) {
                    val delta = 2
                    _state.update {
                        it.copy(
                            correctLeftToRightIndex = it.correctLeftToRightIndex + (li to ri),
                            selectedLeftIndex = null,
                            selectedRightIndex = null,
                            starterAttemptsUsedLeft = it.starterAttemptsUsedLeft + li,
                            player1Points = if (starterIsP1) it.player1Points + delta else it.player1Points,
                            player2Points = if (starterIsP1) it.player2Points else it.player2Points + delta,
                            infoMessage = "Correct: +2",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            starterAttemptsUsedLeft = it.starterAttemptsUsedLeft + li,
                            starterWrongAttempts = it.starterWrongAttempts + SpojniceWrongAttempt(li, ri),
                            selectedLeftIndex = null,
                            selectedRightIndex = null,
                            infoMessage = "Wrong — hidden until opponent turn.",
                        )
                    }
                }
                maybeFinishStarterPhase()
            }

            SpojnicePhase.PLAYING_OPPONENT -> {
                val opponentIsP1 = !s.starterIsPlayer1
                if (correct) {
                    val delta = 2
                    _state.update {
                        it.copy(
                            correctLeftToRightIndex = it.correctLeftToRightIndex + (li to ri),
                            selectedLeftIndex = null,
                            selectedRightIndex = null,
                            player1Points = if (opponentIsP1) it.player1Points + delta else it.player1Points,
                            player2Points = if (opponentIsP1) it.player2Points else it.player2Points + delta,
                            infoMessage = "Correct: +2",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            selectedLeftIndex = null,
                            selectedRightIndex = null,
                            infoMessage = "Wrong — try again.",
                        )
                    }
                }
                if (_state.value.correctLeftToRightIndex.size == s.pairs.size) {
                    finishRoundAfterPlay()
                }
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
        opponentTimerJob?.cancel()
        _state.update {
            it.copy(
                phase = SpojnicePhase.PLAYING_OPPONENT,
                secondsLeft = 30,
                selectedLeftIndex = null,
                selectedRightIndex = null,
                infoMessage = "Opponent: connect remaining pairs. You have 30 seconds.",
            )
        }
        opponentTimerJob = viewModelScope.launch {
            for (i in 29 downTo 0) {
                delay(1000)
                if (_state.value.phase != SpojnicePhase.PLAYING_OPPONENT) return@launch
                _state.update { it.copy(secondsLeft = i) }
            }
            finishRoundAfterPlay()
        }
    }

    private fun finishRoundAfterPlay() {
        opponentTimerJob?.cancel()
        val s = _state.value
        val nextPhase = if (s.currentRound == 1) SpojnicePhase.ROUND_END else SpojnicePhase.GAME_OVER
        _state.update {
            it.copy(
                phase = nextPhase,
                infoMessage = "Round ${it.currentRound} finished",
            )
        }
    }

    fun nextRound() {
        opponentTimerJob?.cancel()
        _state.update {
            it.copy(
                currentRound = 2,
                phase = SpojnicePhase.ROUND_INTRO,
                secondsLeft = 30,
                starterIsPlayer1 = false,
                pairs = samplePairsRound2(),
                rightOptions = samplePairsRound2().map { p -> p.right }.shuffled(),
                correctLeftToRightIndex = emptyMap(),
                starterAttemptsUsedLeft = emptySet(),
                starterWrongAttempts = emptyList(),
                selectedLeftIndex = null,
                selectedRightIndex = null,
                infoMessage = "",
            )
        }
    }

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

    override fun onCleared() {
        opponentTimerJob?.cancel()
        super.onCleared()
    }
}
