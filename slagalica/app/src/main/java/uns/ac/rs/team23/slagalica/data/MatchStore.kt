package uns.ac.rs.team23.slagalica.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MatchStore {
    var matchId: String = ""
    var opponentUsername: String = ""
    /** Match player1 (host) display name — same on both devices. */
    var player1Username: String = ""
    /** Match player2 (guest) display name — same on both devices. */
    var player2Username: String = ""
    var isFriendly: Boolean = false

    /** Uid of this device's user. */
    var myUid: String = ""

    /** Uid of the match host (= player1Id), the authoritative referee for every game. */
    var hostId: String = ""

    var player1Id: String = ""
    var player2Id: String = ""

    /** Set when the opponent leaves; remaining player continues the match. */
    var abandonedById: String = ""

    val opponentAbandoned: Boolean
        get() = abandonedById.isNotBlank() && abandonedById != myUid

    /** This device referees the match when it is player1. */
    val isHost: Boolean get() = myUid.isNotBlank() && myUid == hostId

    var currentGameIndex by mutableStateOf(0)

    fun set(
        id: String,
        opponent: String,
        friendly: Boolean = false,
        myUid: String = "",
        hostId: String = "",
        player1: String = "",
        player2: String = "",
        player1Id: String = "",
        player2Id: String = "",
    ) {
        matchId = id
        opponentUsername = opponent
        player1Username = player1
        player2Username = player2
        isFriendly = friendly
        this.myUid = myUid
        this.hostId = hostId
        this.player1Id = player1Id.ifBlank { hostId }
        this.player2Id = player2Id
        abandonedById = ""
        currentGameIndex = 0
    }

    fun clear() {
        matchId = ""
        opponentUsername = ""
        player1Username = ""
        player2Username = ""
        isFriendly = false
        myUid = ""
        hostId = ""
        player1Id = ""
        player2Id = ""
        abandonedById = ""
        currentGameIndex = 0
    }

    val isActive: Boolean get() = matchId.isNotBlank()
}
