package uns.ac.rs.team23.server.service

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class MatchmakingService {

    // userId → time they joined the queue
    private val queue = ConcurrentHashMap<Long, LocalDateTime>()

    fun enqueue(userId: Long) {
        queue[userId] = LocalDateTime.now()
    }

    fun dequeue(userId: Long) {
        queue.remove(userId)
    }

    /** Returns an opponent's userId (oldest waiter that is not the caller), or null. */
    fun findOpponent(userId: Long): Long? {
        return queue.entries
            .filter { it.key != userId }
            .minByOrNull { it.value }
            ?.key
    }

    fun isWaiting(userId: Long): Boolean = queue.containsKey(userId)
}
