package uns.ac.rs.team23.server.repository

import org.springframework.data.jpa.repository.JpaRepository
import uns.ac.rs.team23.server.model.User

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByUsername(username: String): User?
    fun findByEmailOrUsername(email: String, username: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByUsername(username: String): Boolean
    fun findAllByRegion(region: String): List<User>
}
