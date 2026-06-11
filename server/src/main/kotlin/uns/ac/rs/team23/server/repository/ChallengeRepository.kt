package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.Challenge
import uns.ac.rs.team23.server.model.ChallengeStatus

interface ChallengeRepository : JpaRepository<Challenge, Long> {
    fun findByRegionAndStatusOrderByCreatedAtDesc(region: String, status: ChallengeStatus): List<Challenge>
    fun findByRegionOrderByCreatedAtDesc(region: String): List<Challenge>
}
