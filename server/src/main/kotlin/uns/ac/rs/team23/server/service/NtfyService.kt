package uns.ac.rs.team23.server.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class NtfyService(
    @Value("\${ntfy.url}") private val ntfyUrl: String,
) {
    private val client = RestClient.create()

    fun notify(userId: Long, title: String, message: String, tags: String = "bell") {
        try {
            client.post()
                .uri("$ntfyUrl/slagalica-user-$userId")
                .header("Title", title)
                .header("Tags", tags)
                .header("Priority", "default")
                .body(message)
                .retrieve()
                .toBodilessEntity()
        } catch (_: Exception) {
            // Don't let notification failures break core logic
        }
    }
}
