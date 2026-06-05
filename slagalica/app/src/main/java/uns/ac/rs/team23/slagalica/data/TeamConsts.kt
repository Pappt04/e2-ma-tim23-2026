package uns.ac.rs.team23.slagalica.data

object TeamConsts {
    data class TeamMember(
        val name: String,
        val surname: String,
        val index: String,
    )

    val teamName = "Team23"

    val members =
        listOf(
            TeamMember(
                "Tamas",
                "Papp",
                "RA-4-2022",
            ),
            TeamMember(
                "Dorottya",
                "Apro",
                "RA-118-2022",
            ),
            TeamMember(
                "Marko",
                "Minic",
                "RA-217-2022",
            ),
        )
}
