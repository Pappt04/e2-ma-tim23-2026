package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import uns.ac.rs.team23.server.dto.challenge.CreateChallengeRequest
import uns.ac.rs.team23.server.dto.challenge.SubmitChallengeGameRequest
import uns.ac.rs.team23.server.service.ChallengeService

@RestController
@RequestMapping("/api/challenges")
class ChallengeController(private val challengeService: ChallengeService) {

    @PostMapping
    fun create(@RequestBody req: CreateChallengeRequest, session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.status(HttpStatus.CREATED)
                .body(challengeService.createChallenge(userId, req.region, req.stakedStars, req.stakedTokens))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @GetMapping("/region/{region}")
    fun listByRegion(@PathVariable region: String, session: HttpSession): ResponseEntity<Any> {
        session.userId() ?: return unauthorized()
        return ResponseEntity.ok(challengeService.getChallengesInRegion(region))
    }

    @GetMapping("/{challengeId}")
    fun get(@PathVariable challengeId: Long, session: HttpSession): ResponseEntity<Any> {
        session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(challengeService.getChallenge(challengeId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(e.message))
        }
    }

    @PostMapping("/{challengeId}/join")
    fun join(@PathVariable challengeId: Long, session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(challengeService.joinChallenge(userId, challengeId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @PostMapping("/{challengeId}/submit")
    fun submitGame(
        @PathVariable challengeId: Long,
        @RequestBody req: SubmitChallengeGameRequest,
        session: HttpSession,
    ): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(challengeService.submitGameScore(userId, challengeId, req.gameType, req.score))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    private fun HttpSession.userId() = getAttribute("userId") as? Long
    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body<Any>(mapOf("error" to "Not logged in"))
    private fun error(msg: String?): Any = mapOf("error" to (msg ?: "Error"))
}
