package uns.ac.rs.team23.server.dto.game

import uns.ac.rs.team23.server.model.KorakPoKorakQuestion

data class KorakPoKorakResponse(
    val id: Long,
    val clues: List<String>,
    val finalWord: String,
) {
    companion object {
        fun from(q: KorakPoKorakQuestion) = KorakPoKorakResponse(
            id = q.id,
            clues = q.clues.toList(),
            finalWord = q.finalWord,
        )
    }
}
