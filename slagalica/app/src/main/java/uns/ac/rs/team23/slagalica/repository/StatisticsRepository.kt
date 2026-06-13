package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.PlayerStatistics

interface StatisticsRepository {
    /** Computes the signed-in player's statistics from their completed match history. */
    suspend fun getPlayerStatistics(): Result<PlayerStatistics>
}
