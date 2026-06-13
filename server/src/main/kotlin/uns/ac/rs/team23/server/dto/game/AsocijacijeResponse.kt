package uns.ac.rs.team23.server.dto.game

import uns.ac.rs.team23.server.model.AsocijacijeQuestion

data class AsocijacijeColumnDto(
    val words: List<String>,
    val answer: String,
)

data class AsocijacijeResponse(
    val id: Long,
    val columns: List<AsocijacijeColumnDto>,
    val finalAnswer: String,
) {
    companion object {
        fun from(q: AsocijacijeQuestion) = AsocijacijeResponse(
            id = q.id,
            columns = listOf(
                AsocijacijeColumnDto(q.col0Words, q.col0Answer),
                AsocijacijeColumnDto(q.col1Words, q.col1Answer),
                AsocijacijeColumnDto(q.col2Words, q.col2Answer),
                AsocijacijeColumnDto(q.col3Words, q.col3Answer),
            ),
            finalAnswer = q.finalAnswer,
        )
    }
}
