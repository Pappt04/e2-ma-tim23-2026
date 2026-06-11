package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.EmailVerificationToken

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, Long> {
    fun findByToken(token: String): EmailVerificationToken?
    fun deleteByUserId(userId: Long)
}
