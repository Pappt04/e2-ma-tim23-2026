package uns.ac.rs.team23.server.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    var username: String = "",

    @Column(unique = true, nullable = false)
    var email: String = "",

    @Column(nullable = false)
    var passwordHash: String = "",

    @Column(nullable = false)
    var region: String = "",

    var isEmailVerified: Boolean = false,

    var tokens: Int = 5,

    var stars: Int = 0,

    // Cumulative stars ever earned (never decreases) — used for token milestone conversion
    var totalStarsEarned: Int = 0,

    var leagueLevel: Int = 0,

    var avatarIndex: Int = 0,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    var lastTokenGranted: LocalDate = LocalDate.now(),
)
