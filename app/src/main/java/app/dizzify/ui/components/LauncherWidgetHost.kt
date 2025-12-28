package app.dizzify.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class LauncherWidgetHost(
    private val context: Context
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)

    private val hostViews = mutableMapOf<Int, AppWidgetHostView>()

    fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start widget host", e)
        }
    }

    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop widget host", e)
        }
    }

    fun allocateWidgetId(): Int {
        return appWidgetHost.allocateAppWidgetId()
    }

    fun deleteWidgetId(widgetId: Int) {
        hostViews.remove(widgetId)?.let { view ->
            try {
                appWidgetHost.deleteAppWidgetId(widgetId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting widget $widgetId", e)
            }
        }
    }

    fun getAvailableWidgets(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders
    }

    fun getWidgetInfo(widgetId: Int): AppWidgetProviderInfo? {
        return try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting widget info for $widgetId", e)
            null
        }
    }

    fun createHostView(widgetId: Int): AppWidgetHostView? {
        return try {
            hostViews.getOrPut(widgetId) {
                val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    ?: throw IllegalStateException("No widget info for $widgetId")
                appWidgetHost.createView(context, widgetId, info).apply {
                    setAppWidget(widgetId, info)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create host view for widget $widgetId", e)
            null
        }
    }

    fun getHostView(widgetId: Int): AppWidgetHostView? = hostViews[widgetId]

    fun bindWidget(widgetId: Int, providerInfo: AppWidgetProviderInfo): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                widgetId,
                providerInfo.provider
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind widget", e)
            false
        }
    }

    fun needsConfiguration(widgetId: Int): Boolean {
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return false
        return info.configure != null
    }

    fun createConfigurationIntent(widgetId: Int): Intent? {
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return null
        if (info.configure == null) return null

        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
    }

    fun updateWidgetSize(widgetId: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        appWidgetManager.updateAppWidgetOptions(widgetId, options)
    }

    companion object {
        private const val TAG = "LauncherWidgetHost"
        private const val APPWIDGET_HOST_ID = 1024
    }
}