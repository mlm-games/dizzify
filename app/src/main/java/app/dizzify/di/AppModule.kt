package app.dizzify.di

import app.dizzify.LauncherViewModel
import app.dizzify.data.repository.AppRepository
import app.dizzify.helper.IconCache
import app.dizzify.helper.PermissionManager
import app.dizzify.helper.PrivateSpaceHelper
import app.dizzify.helper.iconpack.IconPackManager
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherSettingsSchema
import app.dizzify.settings.LauncherStateSchema
import app.dizzify.ui.components.LauncherWidgetHost
import app.dizzify.ui.components.snackbar.SnackbarManager
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {

    // Application-scoped IO supervisor for repository work. Never leaks an Activity context.
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single { LauncherWidgetHost(androidContext()) }

    single { SnackbarManager() }

    // Singletons so icon caches actually hit instead of being rebuilt per loadApps() call.
    single { IconCache(androidApplication()) }
    single { IconPackManager(androidApplication()) }
    single { PrivateSpaceHelper(androidApplication()) }
    single { PermissionManager(androidApplication()) }


    single {
        createSettingsDataStore(
            context = androidContext(),
            name = "launcher_settings"
        )
    }

    single {
        createSettingsDataStore(
            context = androidContext(),
            name = "launcher_state"
        )
    }

    single(named("settings")) { SettingsRepository(get(), LauncherSettingsSchema) }
    single(named("state")) { SettingsRepository(get(), LauncherStateSchema) }


    single {
        AppRepository(
            context = androidApplication(),
            settingsRepo = get(named("settings")),
            stateRepo = get(named("state")),
            iconCache = get(),
            privateSpaceHelper = get(),
            coroutineScope = get<CoroutineScope>()
        )
    }

    viewModel {
        LauncherViewModel(
            app = androidApplication(),
            settingsRepo = get(named("settings")),
            stateRepo = get(named("state")),
            appRepository = get()
        )
    }
}
