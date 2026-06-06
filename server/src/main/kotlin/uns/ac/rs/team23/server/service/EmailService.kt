package uns.ac.rs.team23.server.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import uns.ac.rs.team23.server.model.User

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.base-url}") private val baseUrl: String,
    @Value("\${spring.mail.username}") private val fromAddress: String,
) {
    fun sendVerificationEmail(user: User, token: String) {
        val link = "$baseUrl/api/auth/verify?token=$token"
        val message = SimpleMailMessage().apply {
            from = fromAddress
            setTo(user.email)
            subject = "Slagalica — confirm your registration"
            text = "Hello ${user.username},\n\nClick the link below to verify your account:\n$link\n\nThe link expires in 24 hours."
        }
        mailSender.send(message)
    }

    fun sendPasswordResetEmail(user: User, token: String) {
        val link = "$baseUrl/api/auth/reset-password?token=$token"
        val message = SimpleMailMessage().apply {
            from = fromAddress
            setTo(user.email)
            subject = "Slagalica — reset your password"
            text = "Hello ${user.username},\n\nClick the link below to reset your password:\n$link\n\nThe link expires in 24 hours.\n\nIf you did not request this, ignore this email."
        }
        mailSender.send(message)
    }
}
