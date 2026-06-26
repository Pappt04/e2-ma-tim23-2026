package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.DailyMissionType
import uns.ac.rs.team23.slagalica.models.DailyMissionsState
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.models.leagueLevelForStars
import java.time.LocalDate

private const val STARS_PER_MISSION = 3
private const val ALL_BONUS_STARS = 3
private const val ALL_BONUS_TOKENS = 2

/**
 * Daily missions (spec 12). Progress is stored on the user document under `dailyMissions`
 * (with the calendar day it belongs to); a new day lazily resets it — there is no background job,
 * the reset happens the first time the player reads or completes a mission after 00:00.
 */
class FirebaseDailyMissionRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : DailyMissionRepository {

    private val users get() = firestore.collection("users")
    private val matches get() = firestore.collection("matches")

    override fun currentUserId(): String? = auth.currentUser?.uid

    private fun today(): String = LocalDate.now().toString() // YYYY-MM-DD

    override suspend fun getMissions(): Result<DailyMissionsState> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val u = users.document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val dm = u.get("dailyMissions") as? Map<String, Any?>
        if (dm == null || dm["date"] != today()) {
            DailyMissionsState() // a new day — show a clean slate
        } else {
            DailyMissionsState(
                winMatch = dm["winMatch"] == true,
                sendChat = dm["sendChat"] == true,
                friendlyMatch = dm["friendlyMatch"] == true,
                winTournament = dm["winTournament"] == true,
                allBonusClaimed = dm["allClaimed"] == true,
            )
        }
    }

    override suspend fun completeMission(type: DailyMissionType): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val userRef = users.document(uid)
        val today = today()
        var newlyCompleted = false
        var bonusGranted = false
        firestore.runTransaction { tx ->
            newlyCompleted = false
            bonusGranted = false
            val u = tx.get(userRef)
            @Suppress("UNCHECKED_CAST")
            val stored = (u.get("dailyMissions") as? Map<String, Any?>) ?: emptyMap()
            val sameDay = stored["date"] == today

            // Today's flags (reset to false on a new day).
            val flags = linkedMapOf(
                "winMatch" to (sameDay && stored["winMatch"] == true),
                "sendChat" to (sameDay && stored["sendChat"] == true),
                "friendlyMatch" to (sameDay && stored["friendlyMatch"] == true),
                "winTournament" to (sameDay && stored["winTournament"] == true),
            )
            if (flags[type.key] == true) return@runTransaction // already done today — idempotent
            flags[type.key] = true
            newlyCompleted = true

            val allClaimedBefore = sameDay && stored["allClaimed"] == true
            val nowAllComplete = flags.values.all { it }
            var starDelta = STARS_PER_MISSION
            var tokenReward = 0
            var allClaimed = allClaimedBefore
            if (nowAllComplete && !allClaimedBefore) {
                starDelta += ALL_BONUS_STARS
                tokenReward += ALL_BONUS_TOKENS
                allClaimed = true
                bonusGranted = true
            }

            // Earned stars feed cycle/weekly/total + league + the per-50 token bonus (spec 3.d/4).
            val oldStars = (u.getLong("stars") ?: 0L).toInt()
            val newStars = (oldStars + starDelta).coerceAtLeast(0)
            val oldCycle = (u.getLong("cycleStars") ?: 0L).toInt()
            val oldWeekly = (u.getLong("weeklyCycleStars") ?: 0L).toInt()
            val oldTotal = (u.getLong("totalStarsEarned") ?: 0L).toInt()
            val newTotal = oldTotal + starDelta
            val tokenBonus = newTotal / 50 - oldTotal / 50
            val oldTokens = (u.getLong("tokens") ?: 0L).toInt()

            tx.update(
                userRef,
                mapOf(
                    "stars" to newStars,
                    "cycleStars" to oldCycle + starDelta,
                    "weeklyCycleStars" to oldWeekly + starDelta,
                    "totalStarsEarned" to newTotal,
                    "tokens" to oldTokens + tokenBonus + tokenReward,
                    "leagueLevel" to leagueLevelForStars(newStars),
                    "dailyMissions" to mapOf(
                        "date" to today,
                        "winMatch" to flags["winMatch"],
                        "sendChat" to flags["sendChat"],
                        "friendlyMatch" to flags["friendlyMatch"],
                        "winTournament" to flags["winTournament"],
                        "allClaimed" to allClaimed,
                    ),
                ),
            )
        }.await()

        // Notify (system + in-app history) when a mission is freshly completed (spec 11 — rewards).
        if (newlyCompleted) {
            val bonusSuffix = if (bonusGranted) "  ·  All 4 done! +2 🎟 +3 ⭐ bonus" else ""
            runCatching {
                FirestoreNotificationWriter.push(
                    firestore,
                    uid,
                    Notification(
                        id = "mission_${type.key}_$today",
                        title = "Daily task complete 🎁",
                        message = "${missionTitle(type)} — +3 ⭐$bonusSuffix",
                        type = NotificationType.REWARD,
                    ),
                )
            }
        }
    }

    private fun missionTitle(type: DailyMissionType): String = when (type) {
        DailyMissionType.WIN_MATCH -> "Win a match"
        DailyMissionType.SEND_CHAT -> "Send a chat message"
        DailyMissionType.FRIENDLY_MATCH -> "Play a friendly match"
        DailyMissionType.WIN_TOURNAMENT -> "Win a tournament match"
    }

    override suspend fun onMatchFinished(matchId: String): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        if (matchId.isBlank()) return@runCatching
        val m = matches.document(matchId).get().await()
        if (m.getString("status") != "COMPLETED") return@runCatching
        if (m.getString("tournamentId") != null) return@runCatching      // tournament wins handled elsewhere
        val p1 = m.getString("player1Id")
        val p2 = m.getString("player2Id")
        if (uid != p1 && uid != p2) return@runCatching                   // not a participant
        if (p1 == null || p2 == null) return@runCatching                 // solo challenge attempt — ignore

        val isFriendly = m.getBoolean("isFriendly") ?: false
        if (isFriendly) {
            completeMission(DailyMissionType.FRIENDLY_MATCH)             // "play a friendly match"
        } else if (m.getString("winnerId") == uid) {
            completeMission(DailyMissionType.WIN_MATCH)                  // "win a (ranked) match"
        }
    }
}
