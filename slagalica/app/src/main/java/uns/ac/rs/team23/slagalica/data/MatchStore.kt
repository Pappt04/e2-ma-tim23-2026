package uns.ac.rs.team23.slagalica.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MatchStore {
    var matchId: String = ""
    var opponentUsername: String = ""
    var isFriendly: Boolean = false
    var currentGameIndex by mutableStateOf(0)

    fun set(id: String, opponent: String, friendly: Boolean = false) {
        matchId = id
        opponentUsername = opponent
        isFriendly = friendly
        currentGameIndex = 0
    }

    fun clear() {
        matchId = ""
        opponentUsername = ""
        isFriendly = false
        currentGameIndex = 0
    }

    val isActive: Boolean get() = matchId.isNotBlank()
}
