package uns.ac.rs.team23.slagalica.repository

import kotlinx.coroutines.flow.Flow
import uns.ac.rs.team23.slagalica.models.Friend

interface FriendRepository {
    fun currentUserId(): String?

    /** Search registered (non-guest) users by username prefix. Excludes self and existing friends. */
    suspend fun searchUsers(prefix: String): Result<List<Friend>>

    /** Resolve a username to a uid (via the public usernames collection) and add as a friend. */
    suspend fun addFriendByUsername(username: String): Result<Unit>

    suspend fun addFriendByUid(uid: String): Result<Unit>

    suspend fun removeFriend(uid: String): Result<Unit>

    /** Live list of friends, hydrated with their stars/league/avatar/monthly rank/presence. */
    fun observeFriends(): Flow<List<Friend>>
}
