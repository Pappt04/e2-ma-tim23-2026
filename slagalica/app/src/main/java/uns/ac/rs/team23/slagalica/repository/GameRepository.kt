package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.AsocijacijeQuestion
import uns.ac.rs.team23.slagalica.models.KorakPoKorakQuestion
import uns.ac.rs.team23.slagalica.models.MojBrojPuzzle

interface GameRepository {
    suspend fun getKorakPoKorakQuestion(): Result<KorakPoKorakQuestion>
    suspend fun getMojBrojPuzzle(): Result<MojBrojPuzzle>
    suspend fun getAsocijacijeQuestion(): Result<AsocijacijeQuestion>
}
