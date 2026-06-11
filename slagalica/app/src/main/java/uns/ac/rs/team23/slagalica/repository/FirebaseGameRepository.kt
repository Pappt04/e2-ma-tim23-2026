package uns.ac.rs.team23.slagalica.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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
