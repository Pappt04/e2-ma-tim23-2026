package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import uns.ac.rs.team23.server.dto.auth.ChangePasswordRequest
import uns.ac.rs.team23.server.dto.auth.LoginRequest
import uns.ac.rs.team23.server.dto.auth.RegisterRequest
import uns.ac.rs.team23.server.dto.auth.UserResponse
import uns.ac.rs.team23.server.service.UserService

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
) {

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest): ResponseEntity<Map<String, String>> {
        return try {
            userService.register(req)
            ResponseEntity.status(HttpStatus.CREATED)
                .body(mapOf("message" to "Registration successful. Check your email to verify your account."))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/verify")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<Map<String, String>> {
        val success = userService.verifyEmail(token)
        return if (success) {
            ResponseEntity.ok(mapOf("message" to "Email verified successfully. You can now log in."))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid or expired verification token."))
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest, request: HttpServletRequest): ResponseEntity<Any> {
        return try {
            val user = userService.login(req.emailOrUsername, req.password)
            val session = request.getSession(true)
            session.setAttribute("userId", user.id)
            ResponseEntity.ok(UserResponse.from(user))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "Forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Invalid credentials"))
        }
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<Map<String, String>> {
        session.invalidate()
        return ResponseEntity.ok(mapOf("message" to "Logged out"))
    }

    @PostMapping("/change-password")
    fun changePassword(
        @RequestBody req: ChangePasswordRequest,
        session: HttpSession,
    ): ResponseEntity<Map<String, String>> {
        val userId = session.getAttribute("userId") as? Long
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        return try {
            userService.changePassword(userId, req.oldPassword, req.newPassword)
            ResponseEntity.ok(mapOf("message" to "Password changed successfully"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody body: Map<String, String>): ResponseEntity<Map<String, String>> {
        val email = body["email"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "Email required"))
        runCatching { userService.sendPasswordReset(email) }
        return ResponseEntity.ok(mapOf("message" to "If that email is registered, a reset link has been sent."))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@RequestBody body: Map<String, String>): ResponseEntity<Map<String, String>> {
        val token = body["token"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "Token required"))
        val newPassword = body["newPassword"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "New password required"))
        return if (userService.resetPasswordWithToken(token, newPassword)) {
            ResponseEntity.ok(mapOf("message" to "Password reset successfully."))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid or expired token."))
        }
    }

    @GetMapping("/reset-password")
    fun resetPasswordForm(@RequestParam token: String): ResponseEntity<String> {
        return ResponseEntity.ok("""
            <html><body>
            <h2>Slagalica — Reset Password</h2>
            <form method="POST" action="/api/auth/reset-password">
                <input type="hidden" name="token" value="$token"/>
                <label>New password: <input type="password" name="newPassword" required minlength="6"/></label><br/>
                <button type="submit">Reset</button>
            </form>
            </body></html>
        """.trimIndent())
    }

    @GetMapping("/me")
    fun me(session: HttpSession): ResponseEntity<Any> {
        val userId = session.getAttribute("userId") as? Long
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        return try {
            val user = userService.findById(userId)
            ResponseEntity.ok(UserResponse.from(user))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "User not found"))
        }
    }
}
