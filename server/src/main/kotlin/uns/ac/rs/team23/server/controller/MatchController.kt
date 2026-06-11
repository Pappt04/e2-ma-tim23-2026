package uns.ac.rs.team23.server.controller

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import uns.ac.rs.team23.server.dto.match.GameResultRequest
import uns.ac.rs.team23.server.dto.match.MatchInviteResponse
import uns.ac.rs.team23.server.dto.match.StartMatchRequest
import uns.ac.rs.team23.server.service.MatchService

@RestController
@RequestMapping("/api/matches")
class MatchController(private val matchService: MatchService) {

    @PostMapping("/start")
    fun startMatch(@RequestBody req: StartMatchRequest, session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            when (req.type.uppercase()) {
                "RANDOM" -> ResponseEntity.ok(matchService.startRandomMatch(userId, req.friendly))
                "FRIEND" -> {
                    val friendId = req.friendId ?: return ResponseEntity.badRequest().body(error("friendId required"))
                    ResponseEntity.ok(matchService.sendInvite(userId, friendId, req.friendly))
                }
                else -> ResponseEntity.badRequest().body(error("type must be RANDOM or FRIEND"))
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @GetMapping("/current")
    fun getCurrent(session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        val match = matchService.getCurrentMatch(userId)
        return if (match != null) ResponseEntity.ok(match)
        else ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @GetMapping("/{matchId}")
    fun getMatch(@PathVariable matchId: Long, session: HttpSession): ResponseEntity<Any> {
        session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(matchService.getMatch(matchId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(e.message))
        }
    }

    @PostMapping("/{matchId}/submit")
    fun submitGame(
        @PathVariable matchId: Long,
        @RequestBody req: GameResultRequest,
        session: HttpSession,
    ): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(matchService.submitGameResult(matchId, userId, req.score))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @PostMapping("/{matchId}/abandon")
    fun abandon(@PathVariable matchId: Long, session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(matchService.abandonMatch(matchId, userId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @DeleteMapping("/queue")
    fun cancelQueue(session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        matchService.cancelQueue(userId)
        return ResponseEntity.ok(mapOf("message" to "Removed from queue"))
    }

    @GetMapping("/invites/pending")
    fun pendingInvites(session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        val invites = matchService.getPendingInvites(userId).map { MatchInviteResponse.from(it) }
        return ResponseEntity.ok(invites)
    }

    @PostMapping("/invites/{inviteId}/respond")
    fun respondToInvite(
        @PathVariable inviteId: Long,
        @RequestParam accept: Boolean,
        session: HttpSession,
    ): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            ResponseEntity.ok(matchService.respondToInvite(inviteId, userId, accept))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    @DeleteMapping("/invites/{inviteId}")
    fun cancelInvite(@PathVariable inviteId: Long, session: HttpSession): ResponseEntity<Any> {
        val userId = session.userId() ?: return unauthorized()
        return try {
            matchService.cancelInvite(inviteId, userId)
            ResponseEntity.ok(mapOf("message" to "Invite cancelled"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(error(e.message))
        }
    }

    private fun HttpSession.userId() = getAttribute("userId") as? Long
    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body<Any>(mapOf("error" to "Not logged in"))
    private fun error(msg: String?): Any = mapOf("error" to (msg ?: "Error"))
}
