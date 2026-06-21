package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.RegionPlayerPoint
import uns.ac.rs.team23.slagalica.models.RegionStanding
import uns.ac.rs.team23.slagalica.models.RegionStats

interface RegionRepository {
    /** Regional monthly leaderboard: all regions ordered by summed cycleStars. */
    suspend fun loadStandings(): Result<List<RegionStanding>>

    /** One map point per registered (non-guest) player, scattered inside their region. */
    suspend fun loadPlayerPoints(): Result<List<RegionPlayerPoint>>

    suspend fun loadRegionStats(regionId: String): Result<RegionStats>

    /** Region ids that placed 1st/2nd/3rd in the previous monthly cycle. */
    suspend fun loadPreviousTopRegions(): Result<List<String>>

    /** The current user's region id ("" if none / guest). */
    suspend fun myRegion(): String
}
