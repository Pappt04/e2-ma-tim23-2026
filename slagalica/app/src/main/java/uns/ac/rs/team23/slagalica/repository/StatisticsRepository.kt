package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.PlayerStatistics

interface StatisticsRepository {
    /** Computes the signed-in player's statistics from their completed match history. */
    suspend fun getPlayerStatistics(): Result<PlayerStatistics>

    /**
     * Atomically adds the given per-game counters to the signed-in player's stats document
     * (`users/{uid}/stats/{gameType}`). Used by the games to record correct/incorrect answers,
     * per-step/per-attempt solves, etc. No-op for guests with no uid.
     */
    suspend fun recordGameStats(gameType: String, increments: Map<String, Long>): Result<Unit>
}
