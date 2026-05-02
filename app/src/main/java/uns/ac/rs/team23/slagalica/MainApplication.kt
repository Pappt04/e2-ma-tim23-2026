package uns.ac.rs.team23.slagalica

import android.app.Application
import uns.ac.rs.team23.slagalica.di.AppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin


class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MainApplication)
            modules(AppModule)
        }
    }

}