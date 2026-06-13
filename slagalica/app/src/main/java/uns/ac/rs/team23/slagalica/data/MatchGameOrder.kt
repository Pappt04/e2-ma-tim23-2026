package uns.ac.rs.team23.slagalica.data

object MatchGameOrder {
    const val KO_ZNA_ZNA = 0
    const val SPOJNICE = 1
    const val ASOCIJACIJE = 2
    const val SKOCKO = 3
    const val KORAK_PO_KORAK = 4
    const val MOJ_BROJ = 5

    val displayNames = listOf(
        "Ko zna zna",
        "Spojnice",
        "Asocijacije",
        "Skočko",
        "Korak po korak",
        "Moj broj",
    )

    val firebaseTypes = listOf(
        "KO_ZNA_ZNA",
        "SPOJNICE",
        "ASOCIJACIJE",
        "SKOCKO",
        "KORAK_PO_KORAK",
        "MOJ_BROJ",
    )

    fun displayName(index: Int): String = displayNames.getOrElse(index) { "Game ${index + 1}" }
}
