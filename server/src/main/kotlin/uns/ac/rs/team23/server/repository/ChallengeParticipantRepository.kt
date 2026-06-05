package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.ChallengeParticipant

interface ChallengeParticipantRepository : JpaRepository<ChallengeParticipant, Long> {
    fun findByChallengeId(challengeId: Long): List<ChallengeParticipant>
    fun findByChallengeIdAndUserId(challengeId: Long, userId: Long): ChallengeParticipant?
    fun countByChallengeId(challengeId: Long): Long
    fun existsByChallengeIdAndUserId(challengeId: Long, userId: Long): Boolean
}
