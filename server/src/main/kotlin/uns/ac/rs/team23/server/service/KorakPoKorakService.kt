package uns.ac.rs.team23.server.service

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import uns.ac.rs.team23.server.model.KorakPoKorakQuestion
import uns.ac.rs.team23.server.repository.KorakPoKorakQuestionRepository

@Service
class KorakPoKorakService(
    private val repository: KorakPoKorakQuestionRepository,
) {

    fun getRandomQuestion(): KorakPoKorakQuestion =
        repository.findRandom() ?: throw IllegalStateException("No questions in database")

    @PostConstruct
    fun seedIfEmpty() {
        if (repository.count() > 0) return

        val questions = listOf(
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Može biti zvezdana",
                    "Putuje kroz svemir",
                    "Ima kapsulu",
                    "NASA je razvija",
                    "Lansira se s rampe",
                    "Nosi astronaute",
                    "Leti u svemir",
                ),
                finalWord = "RAKETA",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Može biti električna",
                    "Ima žice",
                    "Gradi se od drveta ili metala",
                    "Ima pragove",
                    "Svira se prstima",
                    "Ima žice koje se trzaju",
                    "Muzički instrument sa žicama",
                ),
                finalWord = "GITARA",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Može biti nuklearna",
                    "Daje energiju gradu",
                    "Koristi turbine",
                    "Ima transformatore",
                    "Generatorska postrojenja",
                    "Proizvodi struju",
                    "Elektrana",
                ),
                finalWord = "ELEKTRANA",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Može biti zlatna",
                    "Nosi poruke",
                    "Leti visoko",
                    "Ima krila",
                    "Hrani se ribom",
                    "Ptica selica",
                    "Bela ptica nad morem",
                ),
                finalWord = "GALEB",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Može biti staklenička",
                    "Uzrokuje topljenje leda",
                    "CO2 je primer",
                    "Zadržava toplotu",
                    "U atmosferi",
                    "Utiče na klimu",
                    "Gas koji greje Zemlju",
                ),
                finalWord = "GAS",
            ),
        )

        repository.saveAll(questions)
    }
}
