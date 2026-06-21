package uns.ac.rs.team23.slagalica.models

/** One row of the regional monthly leaderboard. */
data class RegionStanding(
    val region: RegionInfo,
    val totalCycleStars: Int,
    val playerCount: Int,
)

/** Aggregated statistics shown when a region is tapped. */
data class RegionStats(
    val regionId: String,
    val totalPlayers: Int,
    val activePlayers: Int,
    val firsts: Int,
    val seconds: Int,
    val thirds: Int,
)

/** A single player's point on the region map. */
data class RegionPlayerPoint(
    val uid: String,
    val username: String,
    val regionId: String,
    val lat: Double,
    val lng: Double,
    val avatarIndex: Int,
)
