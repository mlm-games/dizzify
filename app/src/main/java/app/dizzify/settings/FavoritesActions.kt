package app.dizzify.settings

import io.github.mlmgames.settings.core.SettingsRepository

suspend fun SettingsRepository<LauncherState>.toggleFavorite(appKey: String) {
    update { state ->
        if (appKey in state.favoriteApps) {
            state.copy(favoriteApps = state.favoriteApps - appKey)
        } else {
            state.copy(favoriteApps = state.favoriteApps + appKey)
        }
    }
}

fun LauncherState.isFavorite(appKey: String): Boolean {
    return appKey in favoriteApps
}
