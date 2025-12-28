package app.dizzify.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.dizzify.ui.theme.LauncherColors

data class WidgetSizeData(
    val width: Int,
    val height: Int,
    val minWidthDp: Dp,
    val maxWidthDp: Dp,
    val minHeightDp: Dp,
    val maxHeightDp: Dp
)

@Composable
fun WidgetHostViewContainer(
    modifier: Modifier = Modifier,
    appWidgetId: Int,
    providerInfo: AppWidgetProviderInfo,
    appWidgetHost: AppWidgetHost,
    widgetSizeData: WidgetSizeData,
    isEditMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelect: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val widgetManager = AppWidgetManager.getInstance(context)
    
    var errorLoading by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    if (errorLoading) {
        WidgetErrorPlaceholder(
            modifier = modifier,
            message = "Widget failed to load"
        )
        return
    }

    val widgetView = remember(appWidgetId) {
        try {
            appWidgetHost.createView(context, appWidgetId, providerInfo)?.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetSizeData.minWidthDp.value.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widgetSizeData.maxWidthDp.value.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetSizeData.minHeightDp.value.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, widgetSizeData.maxHeightDp.value.toInt())
                }
                widgetManager.updateAppWidgetOptions(appWidgetId, options)
            }
        } catch (e: Exception) {
            Log.e("WidgetHost", "Error creating widget view for ID $appWidgetId", e)
            errorLoading = true
            null
        }
    }

    if (widgetView != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = if (isEditMode) 3.dp else 2.dp,
                            color = if (isEditMode) LauncherColors.AccentOrange else Color.White.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (state.isFocused) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Menu -> {
                                onLongPress()
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (isEditMode) {
                                    onSelect()
                                    true
                                } else {
                                    // Let widget handle it
                                    false
                                }
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
        ) {
            AndroidView(
                factory = { widgetView },
                modifier = Modifier.fillMaxSize(),
                update = { widget ->
                    // In edit mode, disable widget touch handling
                    widget.isClickable = !isEditMode
                    widget.isFocusable = !isEditMode
                    widget.isFocusableInTouchMode = !isEditMode
                    
                    // Disable all child views in edit mode
                    if (isEditMode) {
                        disableViewHierarchy(widget)
                    } else {
                        enableViewHierarchy(widget)
                    }
                }
            )
            
            // Edit mode overlay
            if (isEditMode && isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
    } else {
        WidgetErrorPlaceholder(
            modifier = modifier,
            message = "Widget unavailable"
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            widgetView?.apply {
                if (parent != null) {
                    (parent as? ViewGroup)?.removeView(this)
                }
            }
        }
    }
}

private fun disableViewHierarchy(view: android.view.View) {
    view.isEnabled = false
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            disableViewHierarchy(view.getChildAt(i))
        }
    }
}

private fun enableViewHierarchy(view: android.view.View) {
    view.isEnabled = true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            enableViewHierarchy(view.getChildAt(i))
        }
    }
}

@Composable
private fun WidgetErrorPlaceholder(
    modifier: Modifier = Modifier,
    message: String
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LauncherColors.TextTertiary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }
    }
}