package uns.ac.rs.team23.server.dto.match

data class StartMatchRequest(
    // "RANDOM" or "FRIEND"
    val type: String,
    val friendId: Long? = null,
    val friendly: Boolean = false,
)
