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
                    "Can be stellar",
                    "Travels through space",
                    "Has a capsule",
                    "NASA develops it",
                    "Launches from a pad",
                    "Carries astronauts",
                    "Flies into space",
                ),
                finalWord = "ROCKET",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Can be electric",
                    "Has strings",
                    "Made of wood or metal",
                    "Has frets",
                    "Played with fingers",
                    "Has strings that are plucked",
                    "String musical instrument",
                ),
                finalWord = "GUITAR",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Can be nuclear",
                    "Powers a city",
                    "Uses turbines",
                    "Has transformers",
                    "Generator facility",
                    "Produces electricity",
                    "Power station",
                ),
                finalWord = "POWER PLANT",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Can be golden",
                    "Carries messages",
                    "Flies high",
                    "Has wings",
                    "Feeds on fish",
                    "Migratory bird",
                    "White bird over the sea",
                ),
                finalWord = "SEAGULL",
            ),
            KorakPoKorakQuestion(
                clues = mutableListOf(
                    "Can be greenhouse",
                    "Causes ice to melt",
                    "CO2 is an example",
                    "Retains heat",
                    "Found in the atmosphere",
                    "Affects the climate",
                    "Gas that warms the Earth",
                ),
                finalWord = "GAS",
            ),
        )

        repository.saveAll(questions)
    }
}
