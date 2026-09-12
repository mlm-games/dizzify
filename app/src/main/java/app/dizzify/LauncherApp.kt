package app.dizzify

import android.app.Application
import app.dizzify.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

class LauncherApp : Application() {
    /** Process-wide IO scope for repository work. Never leaks an Activity context. */
    lateinit var applicationScope: CoroutineScope
        private set

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Logger.setMinSeverity(Severity.Debug)
        }

        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startKoin {
            androidContext(this@LauncherApp)
            modules(appModule)
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        stopKoin()
        super.onTerminate()
    }
}
