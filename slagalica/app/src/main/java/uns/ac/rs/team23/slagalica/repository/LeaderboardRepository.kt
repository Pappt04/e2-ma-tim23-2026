package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.LeaderboardEntry

interface LeaderboardRepository {
    suspend fun getWeekly(): Result<List<LeaderboardEntry>>
    suspend fun getMonthly(): Result<List<LeaderboardEntry>>
    fun weeklyDateRange(): String
    fun monthlyDateRange(): String
}
