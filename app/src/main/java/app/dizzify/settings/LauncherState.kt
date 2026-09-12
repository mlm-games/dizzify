package app.dizzify.settings

import app.dizzify.data.AppLaunchMode
import app.dizzify.data.HomeLayout
import io.github.mlmgames.settings.core.annotations.Persisted
import io.github.mlmgames.settings.core.annotations.SchemaVersion
import io.github.mlmgames.settings.core.annotations.Serialized
import kotlinx.serialization.Serializable

@SchemaVersion(version = 3) // v3: appLaunchModes values became AppLaunchMode enums
@Serializable
data class LauncherState(
    @Persisted(key = "hidden_apps")
    val hiddenApps: Set<String> = emptySet(),

    @Persisted(key = "renamed_apps")
    val renamedApps: Map<String, String> = emptyMap(),

    @Persisted(key = "recent_app_history")
    val recentAppHistory: Map<String, Long> = emptyMap(),

    @Persisted(key = "home_layout")
    @Serialized
    val homeLayout: HomeLayout = HomeLayout(),

    @Persisted(key = "favorite_apps")
    val favoriteApps: Set<String> = emptySet(),

    @Persisted(key = "app_launch_modes")
    @Serialized
    val appLaunchModes: Map<String, AppLaunchMode> = emptyMap(),
)
