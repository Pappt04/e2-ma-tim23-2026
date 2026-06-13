package uns.ac.rs.team23.server.model

import jakarta.persistence.*

@Entity
@Table(name = "asocijacije_questions")
class AsocijacijeQuestion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ElementCollection
    @CollectionTable(name = "asoc_col0_words", joinColumns = [JoinColumn(name = "question_id")])
    @OrderColumn(name = "word_order")
    @Column(name = "word")
    val col0Words: MutableList<String> = mutableListOf(),
    val col0Answer: String = "",

    @ElementCollection
    @CollectionTable(name = "asoc_col1_words", joinColumns = [JoinColumn(name = "question_id")])
    @OrderColumn(name = "word_order")
    @Column(name = "word")
    val col1Words: MutableList<String> = mutableListOf(),
    val col1Answer: String = "",

    @ElementCollection
    @CollectionTable(name = "asoc_col2_words", joinColumns = [JoinColumn(name = "question_id")])
    @OrderColumn(name = "word_order")
    @Column(name = "word")
    val col2Words: MutableList<String> = mutableListOf(),
    val col2Answer: String = "",

    @ElementCollection
    @CollectionTable(name = "asoc_col3_words", joinColumns = [JoinColumn(name = "question_id")])
    @OrderColumn(name = "word_order")
    @Column(name = "word")
    val col3Words: MutableList<String> = mutableListOf(),
    val col3Answer: String = "",

    @Column(nullable = false)
    val finalAnswer: String = "",
)
