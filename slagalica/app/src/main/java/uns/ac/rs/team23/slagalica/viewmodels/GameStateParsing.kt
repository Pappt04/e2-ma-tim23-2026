package uns.ac.rs.team23.slagalica.viewmodels

import uns.ac.rs.team23.slagalica.network.dto.GameStateDto

fun longOrNull(v: Any?): Long? = when (v) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    else -> null
}

fun effectiveDeadline(gs: GameStateDto, payload: Map<String, Any?>): Long =
    gs.deadlineAt.takeIf { it > 0 } ?: longOrNull(payload["deadline"]) ?: 0L

fun secsLeft(deadline: Long, max: Int, now: Long = System.currentTimeMillis()): Int =
    if (deadline <= 0) max else (((deadline - now) + 999) / 1000).toInt().coerceIn(0, max)
