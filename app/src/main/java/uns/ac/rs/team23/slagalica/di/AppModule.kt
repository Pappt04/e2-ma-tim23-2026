package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.data.SessionStore
import uns.ac.rs.team23.slagalica.services.AuthService
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel

val AppModule = module {
    // Services
    single { AuthService() }
    single { SessionStore(get()) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModelOf(::LobbyViewModel)
}
