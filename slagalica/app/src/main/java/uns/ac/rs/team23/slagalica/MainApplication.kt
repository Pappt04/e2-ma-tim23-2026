package uns.ac.rs.team23.slagalica

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uns.ac.rs.team23.slagalica.di.AppModule
import uns.ac.rs.team23.slagalica.services.LocalNotificationDispatcher

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MainApplication)
            modules(AppModule)
        }
        LocalNotificationDispatcher.ensureChannels(this)
    }
}

