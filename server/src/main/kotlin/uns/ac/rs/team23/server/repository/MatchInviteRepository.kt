package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.InviteStatus
import uns.ac.rs.team23.server.model.MatchInvite

import java.time.LocalDateTime

interface MatchInviteRepository : JpaRepository<MatchInvite, Long> {
    fun findByInviteeIdAndStatus(inviteeId: Long, status: InviteStatus): List<MatchInvite>
    fun findByInviterIdAndStatus(inviterId: Long, status: InviteStatus): List<MatchInvite>
    fun findByStatusAndExpiresAtBefore(status: InviteStatus, time: LocalDateTime): List<MatchInvite>
}
