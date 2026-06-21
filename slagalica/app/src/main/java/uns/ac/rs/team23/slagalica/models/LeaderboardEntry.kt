package uns.ac.rs.team23.slagalica.models

data class LeaderboardEntry(
    val rank: Int,
    val username: String,
    val leagueLevel: Int,
    val cycleStars: Int,
)
