package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.LeaderboardEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class FirebaseLeaderboardRepository(
    private val firestore: FirebaseFirestore,
) : LeaderboardRepository {

    override suspend fun getWeekly(): Result<List<LeaderboardEntry>> = runCatching {
        loadRanked("weeklyCycleStars")
    }

    override suspend fun getMonthly(): Result<List<LeaderboardEntry>> = runCatching {
        loadRanked("cycleStars")
    }

    override fun weeklyDateRange(): String {
        val today = LocalDate.now()
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("d.M.yyyy")
        return "${start.format(fmt)} – ${end.format(fmt)}"
    }

    override fun monthlyDateRange(): String {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        val end = today.with(TemporalAdjusters.lastDayOfMonth())
        val fmt = DateTimeFormatter.ofPattern("d.M.yyyy")
        return "${start.format(fmt)} – ${end.format(fmt)}"
    }

    private suspend fun loadRanked(field: String): List<LeaderboardEntry> {
        val snap = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()
        return snap.documents
            .mapNotNull { doc ->
                val stars = (doc.getLong(field) ?: 0L).toInt()
                if (stars <= 0) return@mapNotNull null
                doc.getString("username")?.let { name ->
                    Triple(name, (doc.getLong("leagueLevel") ?: 0L).toInt(), stars)
                }
            }
            .sortedByDescending { it.third }
            .mapIndexed { index, (username, league, stars) ->
                LeaderboardEntry(
                    rank = index + 1,
                    username = username,
                    leagueLevel = league,
                    cycleStars = stars,
                )
            }
    }
}
