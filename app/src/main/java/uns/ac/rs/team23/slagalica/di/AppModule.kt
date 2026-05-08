package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.data.SessionStore
import uns.ac.rs.team23.slagalica.services.AuthService
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.NotificationsViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SkockoViewModel
import uns.ac.rs.team23.slagalica.viewmodels.AsocijacijeViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SpojniceViewModel

val AppModule =
    module {
        // Services
        single { AuthService() }
        single { SessionStore(get()) }

        // ViewModels
        viewModelOf(::AuthViewModel)

        viewModelOf(constructor = ::NotificationsViewModel)
        viewModel { AuthViewModel(get()) }
        viewModelOf(::LobbyViewModel)
        viewModelOf(::KorakPoKorakViewModel)
        viewModelOf(::MojBrojViewModel)
        viewModelOf(::SkockoViewModel)
        viewModelOf(::AsocijacijeViewModel)
        viewModelOf(::KoZnaZnaViewModel)
        viewModelOf(::SpojniceViewModel)
    }
