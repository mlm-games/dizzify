package app.dizzify.settings

import app.dizzify.data.AppLaunchMode
import io.github.mlmgames.settings.core.SettingsRepository

suspend fun SettingsRepository<LauncherState>.toggleHidden(appKey: String) {
    update { cur ->
        val next = cur.hiddenApps.toMutableSet()
        if (!next.add(appKey)) next.remove(appKey)
        cur.copy(hiddenApps = next)
    }
}

suspend fun SettingsRepository<LauncherState>.setCustomName(appKey: String, newName: String?) {
    update { cur ->
        val map = cur.renamedApps.toMutableMap()
        if (newName.isNullOrBlank()) map.remove(appKey) else map[appKey] = newName
        cur.copy(renamedApps = map)
    }
}

suspend fun SettingsRepository<LauncherState>.markLaunched(appKey: String, now: Long = System.currentTimeMillis()) {
    update { cur ->
        val m = cur.recentAppHistory.toMutableMap()
        m[appKey] = now

        // optional pruning
        if (m.size > 100) {
            m.entries.sortedBy { it.value }.take(20).forEach { m.remove(it.key) }
        }

        cur.copy(recentAppHistory = m)
    }
}

suspend fun SettingsRepository<LauncherState>.setHomeLayout(layout: app.dizzify.data.HomeLayout) {
    update { it.copy(homeLayout = layout) }
}

suspend fun SettingsRepository<LauncherState>.setAppLaunchMode(appKey: String, mode: String?) {
    update { cur ->
        val map = cur.appLaunchModes.toMutableMap()
        if (mode.isNullOrBlank() || mode == AppLaunchMode.AUTO.name) {
            map.remove(appKey)
        } else {
            map[appKey] = mode
        }
        cur.copy(appLaunchModes = map)
    }
}
