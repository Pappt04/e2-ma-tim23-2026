package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.services.AuthService
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.NotificationsViewModel

val AppModule =
    module {
        // Repositories

        // Services
        single { AuthService() }

        // ViewModels
        viewModelOf(::AuthViewModel)

        viewModelOf(constructor = ::NotificationsViewModel)
    }
