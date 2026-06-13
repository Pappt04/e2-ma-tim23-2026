package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uns.ac.rs.team23.server.dto.game.AsocijacijeResponse
import uns.ac.rs.team23.server.service.AsocijacijeService

@RestController
@RequestMapping("/api/games/asocijacije")
class AsocijacijeController(
    private val service: AsocijacijeService,
) {

    @GetMapping("/question")
    fun getQuestion(session: HttpSession): ResponseEntity<Any> {
        session.getAttribute("userId")
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        return try {
            val question = service.getRandomQuestion()
            ResponseEntity.ok(AsocijacijeResponse.from(question))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to e.message))
        }
    }
}
