package app.dizzify.di

import app.dizzify.LauncherViewModel
import app.dizzify.data.repository.AppRepository
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherSettingsSchema
import app.dizzify.settings.LauncherStateSchema
import app.dizzify.ui.components.snackbar.SnackbarManager
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.datastore.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {

    single {
        // Run once on app start (safe to fire-and-forget)
        val repo: SettingsRepository<LauncherSettings> = get()
        // if using manager, call manager.initOnce()
        repo
    }


    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { SnackbarManager() }


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
            context = androidContext(),
            settingsRepo = get(named("settings")),
            stateRepo = get(named("state")),
            coroutineScope = getKoin().get()
        )
    }

    viewModel {
        LauncherViewModel(
            app = androidContext().applicationContext as android.app.Application,
            settingsRepo = get(named("settings")),
            stateRepo = get(named("state"))
        )
    }
}