package uns.ac.rs.team23.slagalica.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uns.ac.rs.team23.slagalica.data.CycleManager
import uns.ac.rs.team23.slagalica.repository.AuthRepository
import uns.ac.rs.team23.slagalica.repository.ChallengeRepository
import uns.ac.rs.team23.slagalica.repository.ChatRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseAuthRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseChallengeRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseChatRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseFriendRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseGameRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseMatchRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseNotificationRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseRegionRepository
import uns.ac.rs.team23.slagalica.repository.FirebaseStatisticsRepository
import uns.ac.rs.team23.slagalica.repository.FriendRepository
import uns.ac.rs.team23.slagalica.repository.GameRepository
import uns.ac.rs.team23.slagalica.repository.MatchRepository
import uns.ac.rs.team23.slagalica.repository.NotificationRepository
import uns.ac.rs.team23.slagalica.repository.RegionRepository
import uns.ac.rs.team23.slagalica.repository.StatisticsRepository
import uns.ac.rs.team23.slagalica.viewmodels.AsocijacijeViewModel
import uns.ac.rs.team23.slagalica.viewmodels.AuthViewModel
import uns.ac.rs.team23.slagalica.viewmodels.ChallengeViewModel
import uns.ac.rs.team23.slagalica.viewmodels.ChatViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KorakPoKorakViewModel
import uns.ac.rs.team23.slagalica.viewmodels.KoZnaZnaViewModel
import uns.ac.rs.team23.slagalica.viewmodels.LobbyViewModel
import uns.ac.rs.team23.slagalica.viewmodels.MojBrojViewModel
import uns.ac.rs.team23.slagalica.viewmodels.FriendsViewModel
import uns.ac.rs.team23.slagalica.viewmodels.NotificationsViewModel
import uns.ac.rs.team23.slagalica.viewmodels.RegionViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SkockoViewModel
import uns.ac.rs.team23.slagalica.viewmodels.SpojniceViewModel
import uns.ac.rs.team23.slagalica.viewmodels.StatisticsViewModel

val AppModule = module {
    // Firebase singletons
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }

    // Repositories
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<GameRepository> { FirebaseGameRepository(get()) }
    single<MatchRepository> { FirebaseMatchRepository(get(), get()) }
    single<ChatRepository> { FirebaseChatRepository(get(), get()) }
    single<ChallengeRepository> { FirebaseChallengeRepository(get(), get()) }
    single<NotificationRepository> { FirebaseNotificationRepository(get(), get()) }
    single<StatisticsRepository> { FirebaseStatisticsRepository(get(), get()) }
    single<FriendRepository> { FirebaseFriendRepository(get(), get()) }
    single<RegionRepository> { FirebaseRegionRepository(get(), get()) }
    single { CycleManager(get()) }

    // ViewModels
    viewModel { AuthViewModel(get(), get(), get()) }
    viewModel { FriendsViewModel(get(), get()) }
    viewModel { RegionViewModel(get(), get()) }
    viewModel { KorakPoKorakViewModel(get(), get(), get()) }
    viewModel { MojBrojViewModel(get(), get(), get()) }
    viewModel { LobbyViewModel(get()) }
    viewModelOf(::ChatViewModel)
    viewModelOf(::ChallengeViewModel)
    viewModel { NotificationsViewModel(get(), get()) }
    viewModel { SkockoViewModel(get(), get()) }
    viewModel { AsocijacijeViewModel(get(), get(), get()) }
    viewModel { KoZnaZnaViewModel(get(), get()) }
    viewModel { SpojniceViewModel(get(), get()) }
    viewModel { StatisticsViewModel(get()) }
}
