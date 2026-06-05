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
