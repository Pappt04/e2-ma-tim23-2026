package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.ChallengeStore
import uns.ac.rs.team23.slagalica.data.MatchStore
import uns.ac.rs.team23.slagalica.network.dto.ChallengeResponseDto
import uns.ac.rs.team23.slagalica.repository.ChallengeRepository
import uns.ac.rs.team23.slagalica.repository.MatchRepository

class ChallengeViewModel(
    private val challengeRepository: ChallengeRepository,
    private val matchRepository: MatchRepository,
) : ViewModel() {

    private val _challenges = MutableStateFlow<List<ChallengeResponseDto>>(emptyList())
    val challenges: StateFlow<List<ChallengeResponseDto>> = _challenges.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    var stakedStars by mutableIntStateOf(0)
        private set
    var stakedTokens by mutableIntStateOf(0)
        private set

    var region: String = ""
        private set

    /** Uid of the signed-in user, so the UI can tell which challenges it has joined/played. */
    val myUid: String? get() = matchRepository.currentUserId()

    fun init(region: String) {
        this.region = region
        refresh()
    }

    fun refresh() {
        if (region.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            challengeRepository.getChallenges(region)
                .onSuccess { _challenges.value = it }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun onStakedStarsChange(v: Int) { stakedStars = v.coerceIn(0, 10) }
    fun onStakedTokensChange(v: Int) { stakedTokens = v.coerceIn(0, 2) }

    fun createChallenge(onCreated: (ChallengeResponseDto) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            challengeRepository.createChallenge(region, stakedStars, stakedTokens)
                .onSuccess { c ->
                    refresh()
                    onCreated(c)
                }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun joinChallenge(challengeId: String, onJoined: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            challengeRepository.joinChallenge(challengeId)
                .onSuccess {
                    refresh()
                    onJoined()
                }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /**
     * Start a solo attempt: spin up a friendly single-player match the user hosts, point
     * [MatchStore]/[ChallengeStore] at it, then [onReady] (navigate into the game flow). When the
     * match completes, the Game route routes the scores back via [submitAttempt].
     */
    fun startAttempt(challenge: ChallengeResponseDto, username: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            matchRepository.startSoloMatch()
                .onSuccess { match ->
                    MatchStore.set(
                        id = match.id,
                        opponent = "Izazov",
                        friendly = true,
                        myUid = match.player1Id,
                        hostId = match.player1Id,
                        player1 = match.player1Username,
                        player2 = "Izazov",
                    )
                    ChallengeStore.set(challenge.id, challenge.region)
                    onReady()
                }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    /** Route a finished solo match's scores into the challenge and finalize if everyone is done. */
    fun submitAttempt(challengeId: String, matchId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            challengeRepository.submitChallengeAttempt(challengeId, matchId)
                .onFailure { _error.value = it.message }
            onDone()
        }
    }

    fun clearError() { _error.value = null }
}
