package uns.ac.rs.team23.slagalica.data

object MatchStore {
    var matchId: String = ""
    var opponentUsername: String = ""
    var isFriendly: Boolean = false

    fun set(id: String, opponent: String, friendly: Boolean = false) {
        matchId = id
        opponentUsername = opponent
        isFriendly = friendly
    }

    fun clear() {
        matchId = ""
        opponentUsername = ""
        isFriendly = false
    }

    val isActive: Boolean get() = matchId.isNotBlank()
}
