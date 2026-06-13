package uns.ac.rs.team23.slagalica.models

/** Aggregated performance for a single game type, computed from a player's match history. */
data class GameTypeStatistic(
    val gameType: String,
    val displayName: String,
    val gamesPlayed: Int,
    val totalPoints: Int,
    val bestPoints: Int,
) {
    val averagePoints: Double
        get() = if (gamesPlayed == 0) 0.0 else totalPoints.toDouble() / gamesPlayed
}

/**
 * Per-game detail counters required by the spec (correct/incorrect ratios, per-step/per-attempt
 * distributions, etc.). Each game accumulates these in Firestore as the player plays ranked matches.
 */
data class GameDetailStats(
    val koCorrect: Int = 0,
    val koIncorrect: Int = 0,
    val mbFound: Int = 0,
    val mbRounds: Int = 0,
    val korakSteps: List<Int> = List(7) { 0 },
    val korakSolved: Int = 0,
    val asoSolved: Int = 0,
    val asoUnsolved: Int = 0,
    val skockoAttempts: List<Int> = List(6) { 0 },
    val spojniceConnected: Int = 0,
    val spojniceTotal: Int = 0,
)

/** A player's overall statistics, derived from completed matches in Firestore. */
data class PlayerStatistics(
    val totalMatches: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val totalPoints: Int,
    val bestMatchScore: Int,
    val perGame: List<GameTypeStatistic>,
    val detail: GameDetailStats = GameDetailStats(),
) {
    val winRate: Double
        get() = if (totalMatches == 0) 0.0 else wins * 100.0 / totalMatches

    val lossRate: Double
        get() = if (totalMatches == 0) 0.0 else losses * 100.0 / totalMatches

    val averagePointsPerMatch: Double
        get() = if (totalMatches == 0) 0.0 else totalPoints.toDouble() / totalMatches

    val hasData: Boolean
        get() = totalMatches > 0

    companion object {
        val EMPTY = PlayerStatistics(
            totalMatches = 0,
            wins = 0,
            losses = 0,
            draws = 0,
            totalPoints = 0,
            bestMatchScore = 0,
            perGame = emptyList(),
        )
    }
}
