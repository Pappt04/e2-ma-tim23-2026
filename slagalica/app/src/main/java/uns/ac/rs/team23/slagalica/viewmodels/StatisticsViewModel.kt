package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.PlayerStatistics
import uns.ac.rs.team23.slagalica.repository.StatisticsRepository

sealed class StatisticsUiState {
    data object Loading : StatisticsUiState()
    data class Success(val statistics: PlayerStatistics) : StatisticsUiState()
    data class Error(val message: String) : StatisticsUiState()
}

class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = StatisticsUiState.Loading
        viewModelScope.launch {
            statisticsRepository.getPlayerStatistics()
                .onSuccess { _uiState.value = StatisticsUiState.Success(it) }
                .onFailure {
                    _uiState.value = StatisticsUiState.Error(
                        it.message ?: "Failed to load statistics",
                    )
                }
        }
    }
}
