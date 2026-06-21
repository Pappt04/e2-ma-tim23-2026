package uns.ac.rs.team23.slagalica.models

/**
 * Static description of one Serbian region. Kept free of any map-library types
 * (plain lat/lng doubles) so it can live in the model layer. The [id] matches
 * the region string chosen at registration (see RegisterComponent.REGIONS).
 */
data class RegionInfo(
    val id: String,
    val displayName: String,
    val icon: String,
    val centerLat: Double,
    val centerLng: Double,
    /** Random spread (in degrees) used to scatter player points inside the region. */
    val spread: Double,
)

object Regions {
    val ALL = listOf(
        RegionInfo("Beograd", "Beograd", "🏙️", 44.8125, 20.4612, 0.12),
        RegionInfo("Vojvodina", "Vojvodina", "🌻", 45.40, 19.85, 0.55),
        RegionInfo("Šumadija i Zapadna Srbija", "Šumadija i Zap. Srbija", "⛰️", 43.85, 20.00, 0.60),
        RegionInfo("Južna i Istočna Srbija", "Južna i Ist. Srbija", "🌲", 43.30, 22.00, 0.65),
        RegionInfo("Kosovo i Metohija", "Kosovo i Metohija", "🏞️", 42.60, 21.00, 0.35),
    )

    fun byId(id: String): RegionInfo? = ALL.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = ALL.indexOfFirst { it.id == id }
}
