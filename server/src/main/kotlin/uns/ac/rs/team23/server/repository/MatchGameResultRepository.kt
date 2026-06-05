package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.MatchGameResult

interface MatchGameResultRepository : JpaRepository<MatchGameResult, Long> {
    fun findByMatchIdAndGameIndex(matchId: Long, gameIndex: Int): MatchGameResult?
    fun findByMatchIdOrderByGameIndex(matchId: Long): List<MatchGameResult>
}
