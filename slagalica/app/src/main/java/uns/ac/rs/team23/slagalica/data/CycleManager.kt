package uns.ac.rs.team23.slagalica.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.models.Regions
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import uns.ac.rs.team23.slagalica.repository.FirestoreNotificationWriter
import java.time.LocalDate
import kotlin.math.floor

/**
 * Client-side monthly-cycle bookkeeping. Firebase Cloud Functions need the paid
 * Blaze plan, so the rollover runs on the client instead: the first client past
 * a month boundary claims the rollover via a guarded transaction and finalizes
 * the cycle (top-3 region frames, 30% star penalty for unranked players, reset
 * of cycleStars). It is fully automatic, driven by the real calendar month.
 */
class CycleManager(
    private val firestore: FirebaseFirestore,
) {
    /** Top-N monthly players are considered "ranked" and exempt from the penalty. */
    private val rankedCutoff = 10

    private fun currentMonthlyId(): String = LocalDate.now().toString().substring(0, 7) // YYYY-MM

    /** ISO week id, e.g. 2026-W25 */
    private fun currentWeeklyId(): String {
        val now = LocalDate.now()
        val week = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = now.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
        return "$year-W$week"
    }

    private fun cyclesRef() = firestore.collection("meta").document("cycles")

    /**
     * Runs the rollover once when the stored month is older than the current month.
     * On a brand-new database (no stored month) it only bootstraps the marker and
     * does not finalize anything.
     */
    suspend fun maybeRollover(): Result<Boolean> = runCatching {
        val monthlyRolled = maybeMonthlyRollover()
        maybeWeeklyRollover()
        monthlyRolled
    }

    private suspend fun maybeMonthlyRollover(): Boolean {
        val current = currentMonthlyId()
        val shouldFinalize = firestore.runTransaction { tx ->
            val doc = tx.get(cyclesRef())
            val stored = doc.getString("currentMonthlyId")
            if (stored == current) return@runTransaction false
            tx.set(
                cyclesRef(),
                mapOf("currentMonthlyId" to current, "processing" to true),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            // Only close a cycle if a previous (older) month was actually recorded.
            stored != null
        }.await()

        return if (shouldFinalize == true) {
            finalizeCycle(current)
            true
        } else {
            false
        }
    }

    /** Reset weeklyCycleStars when the ISO week changes (leaderboard cycle). */
    private suspend fun maybeWeeklyRollover() {
        val currentWeek = currentWeeklyId()
        val shouldReset = firestore.runTransaction { tx ->
            val doc = tx.get(cyclesRef())
            val stored = doc.getString("currentWeeklyId")
            if (stored == currentWeek) return@runTransaction false
            tx.set(
                cyclesRef(),
                mapOf("currentWeeklyId" to currentWeek),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            stored != null
        }.await()
        if (shouldReset != true) return

        val users = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()
            .documents

        awardCycleTokens(users, field = "weeklyCycleStars", weekly = true)

        users.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.update(doc.reference, "weeklyCycleStars", 0) }
            batch.commit().await()
        }
    }

    private suspend fun finalizeCycle(currentMonthlyId: String) {
        val users = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()
            .documents

        // 1) Region totals -> top 3 regions (only regions that actually scored).
        val regionTotals = users.groupBy { it.getString("region") ?: "" }
            .mapValues { (_, docs) -> docs.sumOf { (it.getLong("cycleStars") ?: 0L).toInt() } }
        val topRegions = Regions.ALL
            .map { it.id to (regionTotals[it.id] ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        // 2) Ranked players this cycle = top N by cycleStars (only those who played).
        val rankedUids = users
            .map { it.id to (it.getLong("cycleStars") ?: 0L) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(rankedCutoff)
            .map { it.first }
            .toSet()

        awardCycleTokens(users, field = "cycleStars", weekly = false)

        // 3) Per-user writes: 30% penalty for the unranked, reset cycleStars for all.
        users.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val updates = mutableMapOf<String, Any>("cycleStars" to 0)
                if (doc.id !in rankedUids) {
                    val stars = (doc.getLong("stars") ?: 0L).toInt()
                    if (stars > 0) {
                        val penalized = floor(stars * 0.7).toInt()
                        updates["stars"] = penalized
                        updates["leagueLevel"] = leagueLevelForStars(penalized)
                    }
                }
                batch.update(doc.reference, updates)
            }
            batch.commit().await()
        }

        // 4) Region placement counters (1st/2nd/3rd) for the just-finished cycle.
        val placementBatch = firestore.batch()
        topRegions.getOrNull(0)?.let {
            placementBatch.set(firestore.collection("regions").document(it),
                mapOf("firsts" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
        }
        topRegions.getOrNull(1)?.let {
            placementBatch.set(firestore.collection("regions").document(it),
                mapOf("seconds" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
        }
        topRegions.getOrNull(2)?.let {
            placementBatch.set(firestore.collection("regions").document(it),
                mapOf("thirds" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
        }
        placementBatch.commit().await()

        // 5) Publish the finished cycle's result.
        cyclesRef().set(
            mapOf(
                "currentMonthlyId" to currentMonthlyId,
                "previousMonthlyTopRegions" to topRegions,
                "processing" to false,
                "processedAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    private suspend fun awardCycleTokens(
        users: List<com.google.firebase.firestore.DocumentSnapshot>,
        field: String,
        weekly: Boolean,
    ) {
        val ranked = users
            .map { it.id to (it.getLong(field) ?: 0L).toInt() }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(10)

        ranked.forEachIndexed { index, (uid, _) ->
            val rank = index + 1
            val tokens = tokenRewardForRank(rank, weekly)
            if (tokens <= 0) return@forEachIndexed
            val userRef = firestore.collection("users").document(uid)
            firestore.runTransaction { tx ->
                val snap = tx.get(userRef)
                val current = (snap.getLong("tokens") ?: 0L).toInt()
                tx.update(
                    userRef,
                    mapOf(
                        "tokens" to current + tokens,
                        "pendingRewardTokens" to tokens,
                        "pendingRewardRank" to rank,
                        "pendingRewardPeriod" to if (weekly) "weekly" else "monthly",
                        "pendingRewardShown" to false,
                    ),
                )
            }.await()
            val periodLabel = if (weekly) "weekly" else "monthly"
            FirestoreNotificationWriter.push(
                firestore,
                uid,
                Notification(
                    id = "reward_${if (weekly) "w" else "m"}_${System.currentTimeMillis()}",
                    title = "Leaderboard reward!",
                    message = "You earned $tokens tokens on the $periodLabel leaderboard (#$rank).",
                    type = NotificationType.REWARD,
                ),
            )
        }
    }

    private fun tokenRewardForRank(rank: Int, weekly: Boolean): Int = when (rank) {
        1 -> if (weekly) 5 else 10
        2 -> if (weekly) 3 else 6
        3 -> if (weekly) 2 else 4
        in 4..10 -> if (weekly) 1 else 2
        else -> 0
    }
}
