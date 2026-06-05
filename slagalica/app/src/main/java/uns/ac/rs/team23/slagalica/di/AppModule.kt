package uns.ac.rs.team23.slagalica.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.data.SessionStore
import uns.ac.rs.team23.slagalica.network.RetrofitClient
import uns.ac.rs.team23.slagalica.repository.AuthRepository
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.repository.RemoteAuthRepository
import uns.ac.rs.team23.slagalica.repository.RemoteGameRepository
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

    // Network
    single { RetrofitClient(get()) }

    // Repositories
    single<AuthRepository> { RemoteAuthRepository(get<RetrofitClient>().api, get<RetrofitClient>().cookieJar) }
    single<GameRepository> { RemoteGameRepository(get<RetrofitClient>().api) }

    // ViewModels
    viewModel { AuthViewModel(get(), get()) }
    viewModel { KorakPoKorakViewModel(get()) }
    viewModel { MojBrojViewModel(get()) }

    viewModelOf(::NotificationsViewModel)
    viewModelOf(::LobbyViewModel)
    viewModelOf(::SkockoViewModel)
    viewModelOf(::AsocijacijeViewModel)
    viewModelOf(::KoZnaZnaViewModel)
    viewModelOf(::SpojniceViewModel)
}
