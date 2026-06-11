package uns.ac.rs.team23.slagalica.models

data class UserProfile(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val region: String = "",
    val tokens: Int = 5,
    val stars: Int = 0,
    val leagueLevel: Int = 0,
    val avatarIndex: Int = 0,
    val isEmailVerified: Boolean = false,
    val passwordHash: String = "",
)

val LEAGUE_NAMES = listOf(
    "Nulta liga",
    "Prva liga",
    "Druga liga",
    "Treća liga",
    "Četvrta liga",
    "Peta liga",
)

val LEAGUE_STAR_THRESHOLDS = listOf(0, 100, 200, 400, 800, 1600)

fun leagueLevelForStars(stars: Int): Int {
    for (i in LEAGUE_STAR_THRESHOLDS.indices.reversed()) {
        if (stars >= LEAGUE_STAR_THRESHOLDS[i]) return i
    }
    return 0
}

fun dailyTokensForLeague(leagueLevel: Int): Int = 5 + leagueLevel
