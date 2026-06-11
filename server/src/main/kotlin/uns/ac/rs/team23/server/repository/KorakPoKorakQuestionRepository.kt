package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uns.ac.rs.team23.server.model.KorakPoKorakQuestion

interface KorakPoKorakQuestionRepository : JpaRepository<KorakPoKorakQuestion, Long> {
    @Query("SELECT q FROM KorakPoKorakQuestion q ORDER BY FUNCTION('RANDOM') LIMIT 1")
    fun findRandom(): KorakPoKorakQuestion?
}
