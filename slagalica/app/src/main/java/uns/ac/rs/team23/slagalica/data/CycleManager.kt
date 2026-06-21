package uns.ac.rs.team23.slagalica.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.Regions
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import java.time.LocalDate
import kotlin.math.floor

/**
 * Client-side monthly-cycle bookkeeping. Firebase Cloud Functions need the paid
 * Blaze plan, so the rollover runs on the client instead: the first client past
 * a month boundary claims the rollover via a guarded transaction and finalizes
 * the cycle (top-3 region frames, 30% star penalty for unranked players, reset
 * of cycleStars). [forceRollover] lets a demo trigger it on demand.
 */
class CycleManager(
    private val firestore: FirebaseFirestore,
) {
    /** Top-N monthly players are considered "ranked" and exempt from the penalty. */
    private val rankedCutoff = 10

    private fun currentMonthlyId(): String = LocalDate.now().toString().substring(0, 7) // YYYY-MM

    private fun cyclesRef() = firestore.collection("meta").document("cycles")

    /** Runs the rollover once if the stored month is older than the current month. */
    suspend fun maybeRollover(): Result<Boolean> = runCatching {
        val current = currentMonthlyId()
        val claimed = firestore.runTransaction { tx ->
            val doc = tx.get(cyclesRef())
            val stored = doc.getString("currentMonthlyId")
            if (stored == current) return@runTransaction false
            tx.set(
                cyclesRef(),
                mapOf("currentMonthlyId" to current, "processing" to true),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            true
        }.await()

        if (claimed == true) {
            finalizeCycle(current)
            true
        } else {
            false
        }
    }

    /** Demo helper: finalize the current cycle immediately regardless of the date. */
    suspend fun forceRollover(): Result<Unit> = runCatching {
        finalizeCycle(currentMonthlyId())
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
}
