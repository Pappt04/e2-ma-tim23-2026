package uns.ac.rs.team23.slagalica.models

data class KorakPoKorakQuestion(
    val id: Long,
    val clues: List<String>,
    val answer: String,
)

data class MojBrojPuzzle(
    val targetNumber: Int,
    val numbers: List<Int>,
)
