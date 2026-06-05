package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.KorakPoKorakQuestion
import uns.ac.rs.team23.slagalica.models.MojBrojPuzzle
import uns.ac.rs.team23.slagalica.network.ApiService
import uns.ac.rs.team23.slagalica.network.parseErrorMessage

class RemoteGameRepository(private val api: ApiService) : GameRepository {

    override suspend fun getKorakPoKorakQuestion(): Result<KorakPoKorakQuestion> = runCatching {
        val response = api.getKorakPoKorakQuestion()
        if (response.isSuccessful) {
            val dto = response.body()!!
            KorakPoKorakQuestion(id = dto.id, clues = dto.clues, answer = dto.finalWord)
        } else {
            throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }

    override suspend fun getMojBrojPuzzle(): Result<MojBrojPuzzle> = runCatching {
        val response = api.generateMojBroj()
        if (response.isSuccessful) {
            val dto = response.body()!!
            MojBrojPuzzle(targetNumber = dto.targetNumber, numbers = dto.numbers)
        } else {
            throw Exception(parseErrorMessage(response.errorBody()?.string()))
        }
    }
}
