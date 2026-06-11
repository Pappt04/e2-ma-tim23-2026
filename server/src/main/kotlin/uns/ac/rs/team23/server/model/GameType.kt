package uns.ac.rs.team23.server.model

enum class GameType {
    KO_ZNA_ZNA, SPOJNICE, ASOCIJACIJE, SKOCKO, KORAK_PO_KORAK, MOJ_BROJ;

    companion object {
        val MATCH_ORDER = listOf(KO_ZNA_ZNA, SPOJNICE, ASOCIJACIJE, SKOCKO, KORAK_PO_KORAK, MOJ_BROJ)
    }
}
