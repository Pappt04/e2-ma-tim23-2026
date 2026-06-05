package uns.ac.rs.team23.server.model

import jakarta.persistence.*

@Entity
@Table(name = "korak_po_korak_questions")
class KorakPoKorakQuestion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 7 clues ordered from hardest (index 0) to easiest (index 6)
    @ElementCollection
    @CollectionTable(name = "korak_po_korak_clues", joinColumns = [JoinColumn(name = "question_id")])
    @OrderColumn(name = "clue_order")
    @Column(name = "clue")
    val clues: MutableList<String> = mutableListOf(),

    @Column(nullable = false)
    val finalWord: String = "",
)
