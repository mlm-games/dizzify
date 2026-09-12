package app.dizzify.ui

import android.content.Intent

sealed class LauncherEvent {
    data class LaunchWidgetBindIntent(val intent: Intent) : LauncherEvent()
    data class ConfigureWidget(val widgetId: Int) : LauncherEvent()
    data object NavigateHome : LauncherEvent()
}
