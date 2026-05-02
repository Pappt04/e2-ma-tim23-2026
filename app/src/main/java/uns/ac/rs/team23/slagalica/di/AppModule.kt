package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.Module
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.services.AuthService

val AppModule: Module =
    module {
        // Repositories
        // Services
        single { AuthService() }
        // ViewModels
    }
