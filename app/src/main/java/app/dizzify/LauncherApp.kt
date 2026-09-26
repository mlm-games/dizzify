package app.dizzify

import android.app.Application
import app.dizzify.di.appModule
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class LauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Logger.setMinSeverity(Severity.Debug)
        }

        startKoin {
            androidContext(this@LauncherApp)
            modules(appModule)
        }
    }

    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
