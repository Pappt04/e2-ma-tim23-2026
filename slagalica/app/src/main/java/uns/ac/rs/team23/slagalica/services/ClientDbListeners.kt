package uns.ac.rs.team23.slagalica.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.data.CycleManager
import uns.ac.rs.team23.slagalica.models.Regions
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.models.UserProfile
import uns.ac.rs.team23.slagalica.repository.AuthRepository
import uns.ac.rs.team23.slagalica.repository.toUserProfile

/** Cycle-end reward surfaced as a dialog (spec: nagrada animacija). */
data class PendingRewardEvent(
    val tokens: Int,
    val rank: Int,
    val weekly: Boolean,
)

/**
 * Listens to Firestore changes and runs periodic housekeeping (cycle rollover, daily tokens,
 * chat pruning, notification dispatch).
 */
class ClientDbListeners(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val cycleManager: CycleManager,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registrations = mutableListOf<ListenerRegistration>()
    private var periodicJob: Job? = null
    private val inviteExpiryJobs = mutableMapOf<String, Job>()
    private var activeUid: String? = null

    private val _pendingReward = MutableSharedFlow<PendingRewardEvent>(extraBufferCapacity = 4)
    val pendingReward: SharedFlow<PendingRewardEvent> = _pendingReward.asSharedFlow()

    /** Live tokens / stars / league from Firestore `users/{uid}` (all screens stay in sync). */
    private val _liveProfile = MutableStateFlow<UserProfile?>(null)
    val liveProfile: StateFlow<UserProfile?> = _liveProfile.asStateFlow()

    fun start(uid: String) {
        if (uid == activeUid) return
        stop()
        activeUid = uid
        attachNotificationListener(uid)
        attachMatchInviteListener(uid)
        attachUserDocListener(uid)
        startPeriodicTasks()
    }

    fun stop() {
        activeUid = null
        _liveProfile.value = null
        registrations.forEach { it.remove() }
        registrations.clear()
        inviteExpiryJobs.values.forEach { it.cancel() }
        inviteExpiryJobs.clear()
        periodicJob?.cancel()
        periodicJob = null
    }

    /** Incoming notification docs (chat, invites written by peers). */
    private fun attachNotificationListener(uid: String) {
        var initialLoadDone = false
        val reg = firestore.collection("users").document(uid).collection("notifications")
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                // Skip the first emission (existing notifications) so we only push genuinely new ones.
                if (!initialLoadDone) {
                    initialLoadDone = true
                    return@addSnapshotListener
                }
                snapshot.documentChanges.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    val typeStr = doc.getString("type") ?: return@forEach
                    val type = runCatching { NotificationType.valueOf(typeStr) }
                        .getOrElse { NotificationType.OTHER }
                    if (doc.getBoolean("suppressPush") == true) return@forEach
                    val notification = Notification(
                        id = doc.getString("id") ?: doc.id,
                        title = doc.getString("title") ?: "",
                        message = doc.getString("message") ?: "",
                        type = type,
                        isRead = doc.getBoolean("isRead") ?: false,
                        inviteId = doc.getString("inviteId"),
                        createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
                    )
                    LocalNotificationDispatcher.show(context, notification)
                }
            }
        registrations.add(reg)
    }

    /** Real-time match invites (replaces FCM invite push + polling). */
    private fun attachMatchInviteListener(uid: String) {
        val reg = firestore.collection("matchInvites")
            .whereEqualTo("inviteeId", uid)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED &&
                        change.type != DocumentChange.Type.MODIFIED
                    ) return@forEach
                    val doc = change.document
                    if (doc.getString("status") != "PENDING") return@forEach
                    val inviteId = doc.id
                    val inviter = doc.getString("inviterUsername") ?: "Friend"
                    val friendly = doc.getBoolean("isFriendly") ?: true
                    val notification = Notification(
                        id = "invite_$inviteId",
                        title = "Match invite",
                        message = "$inviter invites you to a ${if (friendly) "friendly" else "ranked"} match",
                        type = NotificationType.INVITE,
                        inviteId = inviteId,
                    )
                    LocalNotificationDispatcher.show(context, notification)
                    scheduleInviteExpiry(inviteId)
                }
            }
        registrations.add(reg)
    }

    /** Live profile + cycle reward flags on the same user document. */
    private fun attachUserDocListener(uid: String) {
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener

                _liveProfile.value = snap.toUserProfile(
                    uid = uid,
                    isEmailVerified = auth.currentUser?.isEmailVerified == true,
                )

                val rewardTokens = (snap.getLong("pendingRewardTokens") ?: 0L).toInt()
                if (rewardTokens <= 0) return@addSnapshotListener
                if (snap.getBoolean("pendingRewardShown") == true) return@addSnapshotListener
                val rank = (snap.getLong("pendingRewardRank") ?: 0L).toInt()
                val weekly = snap.getString("pendingRewardPeriod") == "weekly"
                scope.launch {
                    firestore.collection("users").document(uid)
                        .update(
                            mapOf(
                                "pendingRewardShown" to true,
                                "pendingRewardTokens" to 0,
                            ),
                        )
                        .await()
                    _pendingReward.emit(PendingRewardEvent(tokens = rewardTokens, rank = rank, weekly = weekly))
                    authRepository.refreshDailyTokensIfNeeded()
                }
            }
        registrations.add(reg)
    }

    private fun scheduleInviteExpiry(inviteId: String) {
        inviteExpiryJobs[inviteId]?.cancel()
        inviteExpiryJobs[inviteId] = scope.launch {
            delay(10_000)
            runCatching {
                val ref = firestore.collection("matchInvites").document(inviteId)
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.getString("status") == "PENDING") {
                        tx.update(ref, "status", "EXPIRED")
                    }
                }.await()
            }
            inviteExpiryJobs.remove(inviteId)
        }
    }

    private fun startPeriodicTasks() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (activeUid != null) {
                runCatching { cycleManager.maybeRollover() }
                runCatching { authRepository.refreshDailyTokensIfNeeded() }
                runCatching { pruneOldChatMessages() }
                delay(60_000)
            }
        }
    }

    /** Keeps chat collections bounded (replaces scheduled Cloud Function). */
    private suspend fun pruneOldChatMessages() {
        for (region in Regions.ALL) {
            val msgs = firestore.collection("chat").document(region.id)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .get()
                .await()
            if (msgs.size() <= 200) continue
            val toDelete = msgs.documents.drop(200)
            toDelete.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
    }
}
