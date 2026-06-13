package uns.ac.rs.team23.slagalica.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MatchStore {
    var matchId: String = ""
    var opponentUsername: String = ""
    var isFriendly: Boolean = false

    /** Uid of this device's user. */
    var myUid: String = ""

    /** Uid of the match host (= player1Id), the authoritative referee for every game. */
    var hostId: String = ""

    /** This device referees the match when it is player1. */
    val isHost: Boolean get() = myUid.isNotBlank() && myUid == hostId

    var currentGameIndex by mutableStateOf(0)

    fun set(
        id: String,
        opponent: String,
        friendly: Boolean = false,
        myUid: String = "",
        hostId: String = "",
    ) {
        matchId = id
        opponentUsername = opponent
        isFriendly = friendly
        this.myUid = myUid
        this.hostId = hostId
        currentGameIndex = 0
    }

    fun clear() {
        matchId = ""
        opponentUsername = ""
        isFriendly = false
        myUid = ""
        hostId = ""
        currentGameIndex = 0
    }

    val isActive: Boolean get() = matchId.isNotBlank()
}
