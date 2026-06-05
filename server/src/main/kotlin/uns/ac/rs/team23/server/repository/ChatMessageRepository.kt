package uns.ac.rs.team23.server.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.ChatMessage

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findByRegionOrderBySentAtDesc(region: String, pageable: Pageable): List<ChatMessage>
}
