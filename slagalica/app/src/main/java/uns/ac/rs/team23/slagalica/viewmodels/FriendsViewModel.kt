package uns.ac.rs.team23.slagalica.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.Friend
import uns.ac.rs.team23.slagalica.repository.FriendRepository
import uns.ac.rs.team23.slagalica.repository.RegionRepository

data class FriendsUiState(
    val friends: List<Friend> = emptyList(),
    val searchResults: List<Friend> = emptyList(),
    val previousTopRegions: List<String> = emptyList(),
    val info: String? = null,
)

class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val regionRepository: RegionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FriendsUiState())
    val ui: StateFlow<FriendsUiState> = _ui.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            friendRepository.observeFriends().collect { list ->
                _ui.update { it.copy(friends = list) }
            }
        }
        viewModelScope.launch {
            regionRepository.loadPreviousTopRegions()
                .onSuccess { tops -> _ui.update { it.copy(previousTopRegions = tops) } }
        }
    }

    fun onSearchChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            friendRepository.searchUsers(query)
                .onSuccess { results -> _ui.update { it.copy(searchResults = results) } }
        }
    }

    fun addFriendByUid(uid: String) {
        viewModelScope.launch {
            friendRepository.addFriendByUid(uid)
                .onSuccess {
                    _ui.update { it.copy(info = "Prijatelj dodat") }
                    onSearchChange(searchQuery)
                }
                .onFailure { e -> _ui.update { it.copy(info = e.message ?: "Greška") } }
        }
    }

    /** Used by the QR scanner result (`slagalica-friend:<username>`). */
    fun addFriendByUsername(username: String) {
        viewModelScope.launch {
            friendRepository.addFriendByUsername(username)
                .onSuccess { _ui.update { it.copy(info = "Prijatelj dodat: $username") } }
                .onFailure { e -> _ui.update { it.copy(info = e.message ?: "Greška") } }
        }
    }

    fun removeFriend(uid: String) {
        viewModelScope.launch { friendRepository.removeFriend(uid) }
    }

    fun clearInfo() = _ui.update { it.copy(info = null) }

    /** Frame rank (1/2/3) if the given region placed top-3 last cycle, else 0. */
    fun frameRankFor(region: String): Int {
        val idx = _ui.value.previousTopRegions.indexOf(region)
        return if (idx in 0..2) idx + 1 else 0
    }
}
