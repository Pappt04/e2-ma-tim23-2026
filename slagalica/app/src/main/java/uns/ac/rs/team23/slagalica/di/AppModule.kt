package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.data.SessionStore
import uns.ac.rs.team23.slagalica.data.UserProfileStore
import uns.ac.rs.team23.slagalica.repository.AuthRepository
import uns.ac.rs.team23.slagalica.repository.LocalAuthRepository
import uns.ac.rs.team23.slagalica.viewmodels.AsocijacijeViewModel
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojViewModel
import uns.ac.rs.team23.slagalica.viewmodels.NotificationsViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SkockoViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SpojniceViewModel

val AppModule = module {
    // Data stores
    single { SessionStore(get()) }
    single { UserProfileStore(get()) }

    // Repositories
    single<AuthRepository> { LocalAuthRepository(get()) }

    // ViewModels
    viewModel { AuthViewModel(get(), get()) }

    viewModelOf(::NotificationsViewModel)
    viewModelOf(::LobbyViewModel)
    viewModelOf(::KorakPoKorakViewModel)
    viewModelOf(::MojBrojViewModel)
    viewModelOf(::SkockoViewModel)
    viewModelOf(::AsocijacijeViewModel)
    viewModelOf(::KoZnaZnaViewModel)
    viewModelOf(::SpojniceViewModel)
}
