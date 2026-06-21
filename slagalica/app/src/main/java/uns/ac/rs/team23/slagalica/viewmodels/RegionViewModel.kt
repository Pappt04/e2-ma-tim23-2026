package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uns.ac.rs.team23.slagalica.data.CycleManager
import uns.ac.rs.team23.slagalica.models.RegionPlayerPoint
import uns.ac.rs.team23.slagalica.models.RegionStanding
import uns.ac.rs.team23.slagalica.models.RegionStats
import uns.ac.rs.team23.slagalica.repository.RegionRepository

data class RegionUiState(
    val standings: List<RegionStanding> = emptyList(),
    val playerPoints: List<RegionPlayerPoint> = emptyList(),
    val previousTopRegions: List<String> = emptyList(),
    val myRegion: String = "",
    val selectedStats: RegionStats? = null,
    val loading: Boolean = true,
    val info: String? = null,
)

class RegionViewModel(
    private val regionRepository: RegionRepository,
    private val cycleManager: CycleManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(RegionUiState())
    val ui: StateFlow<RegionUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Finalize any past monthly cycle before showing fresh standings.
            cycleManager.maybeRollover()
            reload()
        }
    }

    private suspend fun reload() {
        val standings = regionRepository.loadStandings().getOrDefault(emptyList())
        val points = regionRepository.loadPlayerPoints().getOrDefault(emptyList())
        val tops = regionRepository.loadPreviousTopRegions().getOrDefault(emptyList())
        val mine = regionRepository.myRegion()
        _ui.update {
            it.copy(
                standings = standings,
                playerPoints = points,
                previousTopRegions = tops,
                myRegion = mine,
                loading = false,
            )
        }
    }

    fun selectRegion(regionId: String) {
        viewModelScope.launch {
            regionRepository.loadRegionStats(regionId)
                .onSuccess { stats -> _ui.update { it.copy(selectedStats = stats) } }
        }
    }

    fun clearSelection() = _ui.update { it.copy(selectedStats = null) }

    fun clearInfo() = _ui.update { it.copy(info = null) }

    /** Demo trigger: finalize the current cycle now (frames, 30% penalty, reset). */
    fun forceEndCycle() {
        viewModelScope.launch {
            cycleManager.forceRollover()
                .onSuccess {
                    _ui.update { it.copy(info = "Ciklus zatvoren — okviri i kazne primenjeni") }
                    reload()
                }
                .onFailure { e -> _ui.update { it.copy(info = e.message ?: "Greška") } }
        }
    }

    fun frameRankFor(region: String): Int {
        val idx = _ui.value.previousTopRegions.indexOf(region)
        return if (idx in 0..2) idx + 1 else 0
    }
}
