package uns.ac.rs.team23.server

import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ServerApplication

fun main(args: Array<String>) {
    val dotenv = dotenv {
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }
    dotenv.entries().forEach { entry ->
        if (System.getenv(entry.key) == null) {
            System.setProperty(entry.key, entry.value)
        }
    }
    runApplication<ServerApplication>(*args)
}
