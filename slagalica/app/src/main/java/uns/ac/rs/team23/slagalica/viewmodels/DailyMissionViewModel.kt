package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.DailyMissionsState
import uns.ac.rs.team23.slagalica.repository.DailyMissionRepository

data class DailyMissionUiState(
    val missions: DailyMissionsState = DailyMissionsState(),
    val loading: Boolean = true,
)

class DailyMissionViewModel(
    private val repository: DailyMissionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyMissionUiState())
    val state: StateFlow<DailyMissionUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.getMissions()
                .onSuccess { _state.value = DailyMissionUiState(it, loading = false) }
                .onFailure { _state.value = _state.value.copy(loading = false) }
        }
    }

    /** Credit match-based missions after a normal (ranked/friendly) match completes. */
    fun onMatchFinished(matchId: String) {
        viewModelScope.launch {
            repository.onMatchFinished(matchId)
            refresh()
        }
    }
}
