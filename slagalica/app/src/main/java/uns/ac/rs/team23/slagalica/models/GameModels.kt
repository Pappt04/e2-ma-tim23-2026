package uns.ac.rs.team23.slagalica.models

data class KorakPoKorakQuestion(
    val id: String,
    val clues: List<String>,
    val answer: String,
)

data class MojBrojPuzzle(
    val targetNumber: Int,
    val numbers: List<Int>,
)

data class AsocijacijeColumnData(
    val words: List<String>,
    val answer: String,
)

data class AsocijacijeQuestion(
    val id: String,
    val columns: List<AsocijacijeColumnData>,
    val finalAnswer: String,
)
