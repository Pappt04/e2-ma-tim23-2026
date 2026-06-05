package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uns.ac.rs.team23.server.model.Match
import uns.ac.rs.team23.server.model.MatchStatus

interface MatchRepository : JpaRepository<Match, Long> {
    @Query("SELECT m FROM Match m WHERE (m.player1.id = :userId OR m.player2.id = :userId) AND m.status IN :statuses ORDER BY m.createdAt DESC")
    fun findActiveByUserId(userId: Long, statuses: List<MatchStatus>): List<Match>
}
