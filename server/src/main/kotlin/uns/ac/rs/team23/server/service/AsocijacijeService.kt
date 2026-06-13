package uns.ac.rs.team23.server.service

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import uns.ac.rs.team23.server.model.AsocijacijeQuestion
import uns.ac.rs.team23.server.repository.AsocijacijeQuestionRepository

@Service
class AsocijacijeService(
    private val repository: AsocijacijeQuestionRepository,
) {

    fun getRandomQuestion(): AsocijacijeQuestion =
        repository.findRandom() ?: throw IllegalStateException("No asocijacije questions in database")

    @PostConstruct
    fun seedIfEmpty() {
        if (repository.count() > 0) return

        val questions = listOf(
            AsocijacijeQuestion(
                col0Words = mutableListOf("FUDBAL", "TRPEZARIJA", "SVEDSKA", "NOGA"),
                col0Answer = "STO",
                col1Words = mutableListOf("NAUKA", "FORMULA", "ANALIZA", "GIMNASTIKA"),
                col1Answer = "MATEMATIKA",
                col2Words = mutableListOf("ARMIJA", "ROK", "DOM", "POLICIJA"),
                col2Answer = "VOJSKA",
                col3Words = mutableListOf("RUKAVICE", "KAPA", "IGLA", "KARDIO"),
                col3Answer = "HIRURG",
                finalAnswer = "OPERACIJA",
            ),
            AsocijacijeQuestion(
                col0Words = mutableListOf("GOL", "STADION", "LOPTA", "MREZA"),
                col0Answer = "FUDBAL",
                col1Words = mutableListOf("KOS", "PARKET", "DRIBLING", "TROJKA"),
                col1Answer = "KOSARKA",
                col2Words = mutableListOf("AS", "SERVIS", "TEREN", "RIM"),
                col2Answer = "TENIS",
                col3Words = mutableListOf("BAZEN", "KROL", "DELFIN", "STAFETA"),
                col3Answer = "PLIVANJE",
                finalAnswer = "SPORT",
            ),
            AsocijacijeQuestion(
                col0Words = mutableListOf("MUZIKA", "ZVEZDA", "SCENA", "MIKROFON"),
                col0Answer = "PEVAC",
                col1Words = mutableListOf("PLATNO", "GLUMA", "KADAR", "REZIJA"),
                col1Answer = "FILM",
                col2Words = mutableListOf("BOJA", "KIST", "PLATNO", "GALERIJA"),
                col2Answer = "SLIKAR",
                col3Words = mutableListOf("TEKST", "IZDANJE", "POGLAVLJE", "BIBLIOTEKA"),
                col3Answer = "PISAC",
                finalAnswer = "UMETNOST",
            ),
            AsocijacijeQuestion(
                col0Words = mutableListOf("MORE", "SUNCOBRAN", "PESAK", "TALAS"),
                col0Answer = "PLAZA",
                col1Words = mutableListOf("AVION", "KARTA", "PRTLJAG", "DESTINACIJA"),
                col1Answer = "PUT",
                col2Words = mutableListOf("HOTEL", "SOBA", "RECEPIJA", "NOCENJE"),
                col2Answer = "SMESTAJ",
                col3Words = mutableListOf("PASOSH", "VIZA", "GRANICA", "CARINA"),
                col3Answer = "PUTOVANJE",
                finalAnswer = "ODMOR",
            ),
        )

        repository.saveAll(questions)
    }
}
