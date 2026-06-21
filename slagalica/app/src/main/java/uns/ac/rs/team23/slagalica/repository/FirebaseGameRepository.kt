package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uns.ac.rs.team23.slagalica.models.AsocijacijeColumnData
import uns.ac.rs.team23.slagalica.models.AsocijacijeQuestion
import uns.ac.rs.team23.slagalica.models.KorakPoKorakQuestion
import uns.ac.rs.team23.slagalica.models.MojBrojPuzzle
import kotlin.random.Random

class FirebaseGameRepository(
    private val firestore: FirebaseFirestore,
) : GameRepository {

    override suspend fun getKorakPoKorakQuestion(): Result<KorakPoKorakQuestion> = runCatching {
        val snapshot = runCatching {
            firestore.collection("korakPoKorakQuestions").get().await()
        }.getOrNull()
        if (snapshot != null && !snapshot.isEmpty) {
            val doc = snapshot.documents.random()
            @Suppress("UNCHECKED_CAST")
            val clues = doc.get("clues") as? List<String> ?: emptyList()
            val finalWord = doc.getString("finalWord") ?: doc.getString("answer")
            if (clues.isNotEmpty() && !finalWord.isNullOrBlank()) {
                return@runCatching KorakPoKorakQuestion(id = doc.id, clues = clues, answer = finalWord)
            }
        }
        bundledKorakQuestions().random()
    }

    private fun bundledKorakQuestions(): List<KorakPoKorakQuestion> = listOf(
        KorakPoKorakQuestion(
            id = "bundled-1",
            clues = listOf("Country", "City", "River", "Bridge", "Europe"),
            answer = "Belgrade",
        ),
        KorakPoKorakQuestion(
            id = "bundled-2",
            clues = listOf("Planet", "Red", "Neighbor", "Earth", "Space"),
            answer = "Mars",
        ),
        KorakPoKorakQuestion(
            id = "bundled-3",
            clues = listOf("Sport", "Net", "Ball", "Goal", "Team"),
            answer = "Football",
        ),
    )

    override suspend fun getMojBrojPuzzle(): Result<MojBrojPuzzle> = runCatching {
        generateMojBroj()
    }

    override suspend fun getAsocijacijeQuestion(): Result<AsocijacijeQuestion> = runCatching {
        val col = firestore.collection("asocijacijeQuestions")
        val snapshot = col.get().await()
        if (snapshot.isEmpty) {
            seedAsocijacije()
            val seeded = col.get().await()
            if (seeded.isEmpty) throw Exception("No associations available")
            return@runCatching parseAsocijacije(seeded.documents.random())
        }
        parseAsocijacije(snapshot.documents.random())
    }

    private fun parseAsocijacije(doc: com.google.firebase.firestore.DocumentSnapshot): AsocijacijeQuestion {
        @Suppress("UNCHECKED_CAST")
        fun colWords(key: String) = doc.get(key) as? List<String> ?: emptyList()
        fun colAnswer(key: String) = doc.getString(key) ?: ""

        val columns = listOf(
            AsocijacijeColumnData(colWords("col0Words"), colAnswer("col0Answer")),
            AsocijacijeColumnData(colWords("col1Words"), colAnswer("col1Answer")),
            AsocijacijeColumnData(colWords("col2Words"), colAnswer("col2Answer")),
            AsocijacijeColumnData(colWords("col3Words"), colAnswer("col3Answer")),
        )
        val finalAnswer = doc.getString("finalAnswer") ?: throw Exception("Invalid association format")
        return AsocijacijeQuestion(id = doc.id, columns = columns, finalAnswer = finalAnswer)
    }

    private suspend fun seedAsocijacije() {
        val col = firestore.collection("asocijacijeQuestions")
        val questions = listOf(
            mapOf(
                "col0Words" to listOf("FOOTBALL", "DINING", "SWEDISH", "LEG"),
                "col0Answer" to "TABLE",
                "col1Words" to listOf("SCIENCE", "FORMULA", "ANALYSIS", "GYMNASTICS"),
                "col1Answer" to "MATH",
                "col2Words" to listOf("ARMY", "ROCK", "HOME", "POLICE"),
                "col2Answer" to "MILITARY",
                "col3Words" to listOf("GLOVES", "CAP", "NEEDLE", "CARDIO"),
                "col3Answer" to "SURGEON",
                "finalAnswer" to "OPERATION",
            ),
            mapOf(
                "col0Words" to listOf("GOAL", "STADIUM", "BALL", "NET"),
                "col0Answer" to "FOOTBALL",
                "col1Words" to listOf("HOOP", "COURT", "DRIBBLE", "THREE"),
                "col1Answer" to "BASKETBALL",
                "col2Words" to listOf("ACE", "SERVE", "COURT", "RIM"),
                "col2Answer" to "TENNIS",
                "col3Words" to listOf("POOL", "CRAWL", "DOLPHIN", "RELAY"),
                "col3Answer" to "SWIMMING",
                "finalAnswer" to "SPORT",
            ),
            mapOf(
                "col0Words" to listOf("MUSIC", "STAR", "STAGE", "MICROPHONE"),
                "col0Answer" to "SINGER",
                "col1Words" to listOf("CANVAS", "ACTING", "FRAME", "DIRECTOR"),
                "col1Answer" to "FILM",
                "col2Words" to listOf("PAINT", "BRUSH", "CANVAS", "GALLERY"),
                "col2Answer" to "PAINTER",
                "col3Words" to listOf("TEXT", "EDITION", "CHAPTER", "LIBRARY"),
                "col3Answer" to "WRITER",
                "finalAnswer" to "ART",
            ),
            mapOf(
                "col0Words" to listOf("SEA", "UMBRELLA", "SAND", "WAVE"),
                "col0Answer" to "BEACH",
                "col1Words" to listOf("PLANE", "TICKET", "LUGGAGE", "DESTINATION"),
                "col1Answer" to "TRIP",
                "col2Words" to listOf("HOTEL", "ROOM", "RECEPTION", "STAY"),
                "col2Answer" to "LODGING",
                "col3Words" to listOf("PASSPORT", "VISA", "BORDER", "CUSTOMS"),
                "col3Answer" to "TRAVEL",
                "finalAnswer" to "VACATION",
            ),
        )
        questions.forEach { col.add(it).await() }
    }

    private fun generateMojBroj(): MojBrojPuzzle {
        val singleDigits = (1..9).toMutableList()
        singleDigits.shuffle()
        val four = singleDigits.take(4)

        val mediums = listOf(10, 15, 20)
        val medium = mediums[Random.nextInt(mediums.size)]

        val larges = listOf(25, 50, 75, 100)
        val large = larges[Random.nextInt(larges.size)]

        val numbers = (four + medium + large).shuffled()
        val target = Random.nextInt(100, 1000)

        return MojBrojPuzzle(targetNumber = target, numbers = numbers)
    }
}
