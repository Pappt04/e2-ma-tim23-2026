package uns.ac.rs.team23.slagalica.data

object MatchStore {
    var matchId: Long = -1L
    var opponentUsername: String = ""
    var isFriendly: Boolean = false

    fun set(id: Long, opponent: String, friendly: Boolean = false) {
        matchId = id
        opponentUsername = opponent
        isFriendly = friendly
    }

    fun clear() {
        matchId = -1L
        opponentUsername = ""
        isFriendly = false
    }

    val isActive: Boolean get() = matchId > 0
}
