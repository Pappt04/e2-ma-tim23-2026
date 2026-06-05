package uns.ac.rs.team23.slagalica.network.dto

data class KorakPoKorakQuestionDto(
    val id: Long,
    val clues: List<String>,
    val finalWord: String,
)

data class MojBrojPuzzleDto(
    val targetNumber: Int,
    val numbers: List<Int>,
)
