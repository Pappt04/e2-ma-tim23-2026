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
        val snapshot = firestore.collection("korakPoKorakQuestions").get().await()
        if (snapshot.isEmpty) throw Exception("Nema dostupnih pitanja")
        val doc = snapshot.documents.random()
        @Suppress("UNCHECKED_CAST")
        val clues = doc.get("clues") as? List<String> ?: emptyList()
        val finalWord = doc.getString("finalWord") ?: throw Exception("Neispravan format pitanja")
        KorakPoKorakQuestion(id = doc.id, clues = clues, answer = finalWord)
    }

    override suspend fun getMojBrojPuzzle(): Result<MojBrojPuzzle> = runCatching {
        generateMojBroj()
    }

    override suspend fun getAsocijacijeQuestion(): Result<AsocijacijeQuestion> = runCatching {
        val col = firestore.collection("asocijacijeQuestions")
        val snapshot = col.get().await()
        if (snapshot.isEmpty) {
            seedAsocijacije()
            val seeded = col.get().await()
            if (seeded.isEmpty) throw Exception("Nema dostupnih asocijacija")
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
        val finalAnswer = doc.getString("finalAnswer") ?: throw Exception("Neispravan format asocijacije")
        return AsocijacijeQuestion(id = doc.id, columns = columns, finalAnswer = finalAnswer)
    }

    private suspend fun seedAsocijacije() {
        val col = firestore.collection("asocijacijeQuestions")
        val questions = listOf(
            mapOf(
                "col0Words" to listOf("FUDBAL", "TRPEZARIJA", "SVEDSKA", "NOGA"),
                "col0Answer" to "STO",
                "col1Words" to listOf("NAUKA", "FORMULA", "ANALIZA", "GIMNASTIKA"),
                "col1Answer" to "MATEMATIKA",
                "col2Words" to listOf("ARMIJA", "ROK", "DOM", "POLICIJA"),
                "col2Answer" to "VOJSKA",
                "col3Words" to listOf("RUKAVICE", "KAPA", "IGLA", "KARDIO"),
                "col3Answer" to "HIRURG",
                "finalAnswer" to "OPERACIJA",
            ),
            mapOf(
                "col0Words" to listOf("GOL", "STADION", "LOPTA", "MREZA"),
                "col0Answer" to "FUDBAL",
                "col1Words" to listOf("KOS", "PARKET", "DRIBLING", "TROJKA"),
                "col1Answer" to "KOSARKA",
                "col2Words" to listOf("AS", "SERVIS", "TEREN", "RIM"),
                "col2Answer" to "TENIS",
                "col3Words" to listOf("BAZEN", "KROL", "DELFIN", "STAFETA"),
                "col3Answer" to "PLIVANJE",
                "finalAnswer" to "SPORT",
            ),
            mapOf(
                "col0Words" to listOf("MUZIKA", "ZVEZDA", "SCENA", "MIKROFON"),
                "col0Answer" to "PEVAC",
                "col1Words" to listOf("PLATNO", "GLUMA", "KADAR", "REZIJA"),
                "col1Answer" to "FILM",
                "col2Words" to listOf("BOJA", "KIST", "PLATNO", "GALERIJA"),
                "col2Answer" to "SLIKAR",
                "col3Words" to listOf("TEKST", "IZDANJE", "POGLAVLJE", "BIBLIOTEKA"),
                "col3Answer" to "PISAC",
                "finalAnswer" to "UMETNOST",
            ),
            mapOf(
                "col0Words" to listOf("MORE", "SUNCOBRAN", "PESAK", "TALAS"),
                "col0Answer" to "PLAZA",
                "col1Words" to listOf("AVION", "KARTA", "PRTLJAG", "DESTINACIJA"),
                "col1Answer" to "PUT",
                "col2Words" to listOf("HOTEL", "SOBA", "RECEPCIJA", "NOCENJE"),
                "col2Answer" to "SMESTAJ",
                "col3Words" to listOf("PASOSH", "VIZA", "GRANICA", "CARINA"),
                "col3Answer" to "PUTOVANJE",
                "finalAnswer" to "ODMOR",
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
