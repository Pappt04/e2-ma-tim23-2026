package uns.ac.rs.team23.server.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uns.ac.rs.team23.server.dto.auth.RegisterRequest
import uns.ac.rs.team23.server.model.EmailVerificationToken
import uns.ac.rs.team23.server.model.User
import uns.ac.rs.team23.server.repository.EmailVerificationTokenRepository
import uns.ac.rs.team23.server.repository.UserRepository
import java.time.LocalDate
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val tokenRepository: EmailVerificationTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
) {

    @Transactional
    fun register(req: RegisterRequest): User {
        if (userRepository.existsByEmail(req.email))
            throw IllegalArgumentException("Email already in use")
        if (userRepository.existsByUsername(req.username))
            throw IllegalArgumentException("Username already taken")

        val user = userRepository.save(
            User(
                username = req.username,
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password)!!,
                region = req.region,
                tokens = 5,
            )
        )

        val verificationToken = UUID.randomUUID().toString()
        tokenRepository.save(EmailVerificationToken(token = verificationToken, user = user))
        emailService.sendVerificationEmail(user, verificationToken)

        return user
    }

    @Transactional
    fun verifyEmail(token: String): Boolean {
        val record = tokenRepository.findByToken(token) ?: return false
        if (record.isExpired()) return false

        record.user.isEmailVerified = true
        userRepository.save(record.user)
        tokenRepository.delete(record)
        return true
    }

    @Transactional
    fun login(emailOrUsername: String, password: String): User {
        val user = userRepository.findByEmailOrUsername(emailOrUsername, emailOrUsername)
            ?: throw IllegalArgumentException("Invalid credentials")

        if (!user.isEmailVerified)
            throw IllegalStateException("Email not verified")

        if (!passwordEncoder.matches(password, user.passwordHash))
            throw IllegalArgumentException("Invalid credentials")

        grantDailyTokensIfNeeded(user)
        return userRepository.save(user)
    }

    @Transactional
    fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        if (!passwordEncoder.matches(oldPassword, user.passwordHash))
            throw IllegalArgumentException("Old password is incorrect")

        user.passwordHash = passwordEncoder.encode(newPassword)!!
        userRepository.save(user)
    }

    fun findById(userId: Long): User =
        userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

    @Transactional
    fun sendPasswordReset(email: String) {
        val user = userRepository.findByEmail(email) ?: return
        val token = UUID.randomUUID().toString()
        tokenRepository.save(EmailVerificationToken(token = token, user = user))
        emailService.sendPasswordResetEmail(user, token)
    }

    @Transactional
    fun resetPasswordWithToken(token: String, newPassword: String): Boolean {
        val record = tokenRepository.findByToken(token) ?: return false
        if (record.isExpired()) return false
        record.user.passwordHash = passwordEncoder.encode(newPassword)!!
        userRepository.save(record.user)
        tokenRepository.delete(record)
        return true
    }

    private fun grantDailyTokensIfNeeded(user: User) {
        val today = LocalDate.now()
        if (user.lastTokenGranted.isBefore(today)) {
            val dailyGrant = 5 + user.leagueLevel
            user.tokens += dailyGrant
            user.lastTokenGranted = today
        }
    }
}
