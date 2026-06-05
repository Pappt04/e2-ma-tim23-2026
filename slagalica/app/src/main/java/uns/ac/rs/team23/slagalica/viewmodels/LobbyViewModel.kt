package uns.ac.rs.team23.slagalica.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class LobbyState {
    data object Searching : LobbyState()

    data class OpponentFound(
        val opponentName: String,
    ) : LobbyState()

    data class YouAreReady(
        val opponentName: String,
    ) : LobbyState()

    data class Countdown(
        val opponentName: String,
        val seconds: Int,
    ) : LobbyState()

    data object Starting : LobbyState()
}

class LobbyViewModel : ViewModel() {
    private val _state = MutableStateFlow<LobbyState>(LobbyState.Searching)
    val state: StateFlow<LobbyState> = _state.asStateFlow()

    init {
        findOpponent()
    }

    private fun findOpponent() {
        _state.value = LobbyState.Searching
        viewModelScope.launch {
            delay(2000)
            _state.value = LobbyState.OpponentFound("Player_${Random.nextInt(100, 999)}")
        }
    }

    fun clickReady() {
        val opponent = (_state.value as? LobbyState.OpponentFound)?.opponentName ?: return
        _state.value = LobbyState.YouAreReady(opponent)
        viewModelScope.launch {
            delay(1500) // simulate opponent clicking ready
            for (i in 3 downTo 1) {
                _state.value = LobbyState.Countdown(opponent, i)
                delay(1000)
            }
            _state.value = LobbyState.Starting
        }
    }
}
