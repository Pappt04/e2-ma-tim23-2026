package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.RegionPlayerPoint
import uns.ac.rs.team23.slagalica.models.RegionStanding
import uns.ac.rs.team23.slagalica.models.RegionStats
import uns.ac.rs.team23.slagalica.models.Regions
import java.util.Random

class FirebaseRegionRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : RegionRepository {

    private val onlineWindowMs = 120_000L

    override suspend fun loadStandings(): Result<List<RegionStanding>> = runCatching {
        val users = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()

        val byRegion = users.documents.groupBy { it.getString("region") ?: "" }
        Regions.ALL.map { info ->
            val docs = byRegion[info.id].orEmpty()
            RegionStanding(
                region = info,
                totalCycleStars = docs.sumOf { (it.getLong("cycleStars") ?: 0L).toInt() },
                playerCount = docs.size,
            )
        }.sortedByDescending { it.totalCycleStars }
    }

    override suspend fun loadPlayerPoints(): Result<List<RegionPlayerPoint>> = runCatching {
        val users = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .get()
            .await()

        users.documents.mapNotNull { doc ->
            val region = doc.getString("region") ?: return@mapNotNull null
            val info = Regions.byId(region) ?: return@mapNotNull null
            val (lat, lng) = pointFor(doc.id, info.centerLat, info.centerLng, info.spread)
            RegionPlayerPoint(
                uid = doc.id,
                username = doc.getString("username") ?: "",
                regionId = region,
                lat = lat,
                lng = lng,
                avatarIndex = (doc.getLong("avatarIndex") ?: 0L).toInt(),
            )
        }
    }

    override suspend fun loadRegionStats(regionId: String): Result<RegionStats> = runCatching {
        val users = firestore.collection("users")
            .whereEqualTo("isGuest", false)
            .whereEqualTo("region", regionId)
            .get()
            .await()
        val now = System.currentTimeMillis()
        val active = users.documents.count { now - (it.getLong("onlineAt") ?: 0L) < onlineWindowMs }

        val regionDoc = firestore.collection("regions").document(regionId).get().await()
        RegionStats(
            regionId = regionId,
            totalPlayers = users.size(),
            activePlayers = active,
            firsts = (regionDoc.getLong("firsts") ?: 0L).toInt(),
            seconds = (regionDoc.getLong("seconds") ?: 0L).toInt(),
            thirds = (regionDoc.getLong("thirds") ?: 0L).toInt(),
        )
    }

    override suspend fun loadPreviousTopRegions(): Result<List<String>> = runCatching {
        val doc = firestore.collection("meta").document("cycles").get().await()
        @Suppress("UNCHECKED_CAST")
        (doc.get("previousMonthlyTopRegions") as? List<String>) ?: emptyList()
    }

    override suspend fun myRegion(): String {
        val uid = auth.currentUser?.uid ?: return ""
        return runCatching {
            firestore.collection("users").document(uid).get().await().getString("region") ?: ""
        }.getOrDefault("")
    }

    /** Stable pseudo-random point inside a region, seeded by uid so it never jumps between loads. */
    private fun pointFor(uid: String, centerLat: Double, centerLng: Double, spread: Double): Pair<Double, Double> {
        val rnd = Random(uid.hashCode().toLong())
        val lat = centerLat + (rnd.nextDouble() * 2 - 1) * spread
        val lng = centerLng + (rnd.nextDouble() * 2 - 1) * spread
        return lat to lng
    }
}
