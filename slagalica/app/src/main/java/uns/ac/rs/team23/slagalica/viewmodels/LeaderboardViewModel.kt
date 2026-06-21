package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.models.LEAGUE_NAMES
import uns.ac.rs.team23.slagalica.models.LeaderboardEntry
import uns.ac.rs.team23.slagalica.repository.LeaderboardRepository

enum class LeaderboardPeriod { WEEKLY, MONTHLY }

data class LeaderboardUiState(
    val period: LeaderboardPeriod = LeaderboardPeriod.MONTHLY,
    val entries: List<LeaderboardEntry> = emptyList(),
    val dateRange: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LeaderboardUiState())
    val ui: StateFlow<LeaderboardUiState> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(120_000) // spec: auto-refresh every 2 minutes
                refresh(silent = true)
            }
        }
    }

    fun setPeriod(period: LeaderboardPeriod) {
        if (_ui.value.period == period) return
        _ui.value = _ui.value.copy(period = period, loading = true)
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _ui.value = _ui.value.copy(loading = true, error = null)
            val period = _ui.value.period
            val result = when (period) {
                LeaderboardPeriod.WEEKLY -> repository.getWeekly()
                LeaderboardPeriod.MONTHLY -> repository.getMonthly()
            }
            result
                .onSuccess { entries ->
                    _ui.value = _ui.value.copy(
                        entries = entries,
                        dateRange = when (period) {
                            LeaderboardPeriod.WEEKLY -> repository.weeklyDateRange()
                            LeaderboardPeriod.MONTHLY -> repository.monthlyDateRange()
                        },
                        loading = false,
                        error = null,
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun leagueName(level: Int): String =
        LEAGUE_NAMES.getOrElse(level) { "League $level" }
}
