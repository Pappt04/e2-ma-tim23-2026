package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import uns.ac.rs.team23.server.dto.game.MojBrojResponse
import uns.ac.rs.team23.server.service.MojBrojService

@RestController
@RequestMapping("/api/games/moj-broj")
class MojBrojController(
    private val service: MojBrojService,
) {

    @GetMapping("/generate")
    fun generate(session: HttpSession): ResponseEntity<Any> {
        session.getAttribute("userId")
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        val puzzle = service.generate()
        return ResponseEntity.ok(puzzle)
    }

    @PostMapping("/evaluate")
    fun evaluate(
        @RequestBody body: Map<String, String>,
        session: HttpSession,
    ): ResponseEntity<Any> {
        session.getAttribute("userId")
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Not logged in"))

        val expression = body["expression"]
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Missing 'expression' field"))
        val targetStr = body["target"]
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Missing 'target' field"))

        return try {
            val target = targetStr.toInt()
            val (result, correct) = service.evaluate(expression, target)
            ResponseEntity.ok(mapOf("result" to result, "correct" to correct))
        } catch (e: NumberFormatException) {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid target number"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid expression")))
        }
    }
}
