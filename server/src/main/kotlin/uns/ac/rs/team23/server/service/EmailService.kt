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
) {
    fun sendVerificationEmail(user: User, token: String) {
        val link = "$baseUrl/api/auth/verify?token=$token"
        val message = SimpleMailMessage().apply {
            setTo(user.email)
            subject = "Slagalica — potvrdi registraciju"
            text = "Zdravo ${user.username},\n\nKlikni na link ispod da potvrdiš svoj nalog:\n$link\n\nLink ističe za 24 sata."
        }
        mailSender.send(message)
    }
}
