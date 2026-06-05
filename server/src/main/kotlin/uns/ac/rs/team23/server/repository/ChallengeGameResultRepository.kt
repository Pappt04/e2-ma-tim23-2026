package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.ChallengeGameResult
import uns.ac.rs.team23.server.model.GameType

interface ChallengeGameResultRepository : JpaRepository<ChallengeGameResult, Long> {
    fun findByParticipantId(participantId: Long): List<ChallengeGameResult>
    fun existsByParticipantIdAndGameType(participantId: Long, gameType: GameType): Boolean
}
