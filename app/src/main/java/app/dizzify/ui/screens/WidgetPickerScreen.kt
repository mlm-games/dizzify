package app.dizzify.ui.screens

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dizzify.helper.BitmapUtils
import app.dizzify.ui.components.buttonPressFeedbackConstant
import app.dizzify.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetListItem(
    val appName: String,
    val appPackage: String,
    val widgets: List<AppWidgetProviderInfo>
)

@Composable
fun WidgetPickerScreen(
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val widgetManager = AppWidgetManager.getInstance(context)

    var widgetList by remember { mutableStateOf<List<WidgetListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        isLoading = true
        widgetList = loadInstalledWidgets(context, widgetManager)
        isLoading = false
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            backFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WidgetPickerHeader(
                onDismiss = onDismiss,
                widgetCount = widgetList.sumOf { it.widgets.size },
                backFocusRequester = backFocusRequester
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LauncherColors.AccentBlue)
                    }
                }
                widgetList.isEmpty() -> {
                    EmptyWidgetsState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    WidgetList(
                        widgetList = widgetList,
                        onWidgetSelected = onWidgetSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerHeader(
    onDismiss: () -> Unit,
    widgetCount: Int,
    backFocusRequester: FocusRequester
) {
    var backFocused by remember { mutableStateOf(false) }
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LauncherSpacing.screenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (backFocused) LauncherColors.AccentBlue.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .then(
                    if (backFocused) Modifier.border(
                        2.dp, LauncherColors.AccentBlue, RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .focusRequester(backFocusRequester)
                .onFocusChanged { state ->
                    backFocused = state.isFocused
                    if (state.isFocused) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        onDismiss()
                        true
                    } else false
                }
                .focusable()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (backFocused) LauncherColors.AccentBlue else Color.White
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Add Widget",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Text(
                text = "$widgetCount widgets available",
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }
    }
}

@Composable
private fun WidgetList(
    widgetList: List<WidgetListItem>,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LauncherSpacing.screenPadding,
            end = LauncherSpacing.screenPadding,
            bottom = LauncherSpacing.xxxl
        ),
        verticalArrangement = Arrangement.spacedBy(LauncherSpacing.sm)
    ) {
        widgetList.forEach { group ->
            item(key = "header_${group.appPackage}") {
                Text(
                    text = group.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = LauncherColors.AccentBlue,
                    modifier = Modifier.padding(vertical = LauncherSpacing.md)
                )
            }

            itemsIndexed(
                items = group.widgets,
                key = { _, info -> info.provider.flattenToString() }
            ) { index, widgetInfo ->
                WidgetInfoItem(
                    context = context,
                    widgetInfo = widgetInfo,
                    onClick = { onWidgetSelected(widgetInfo) }
                )
            }

            // Spacer between app groups
            item(key = "spacer_${group.appPackage}") {
                Spacer(modifier = Modifier.height(LauncherSpacing.md))
            }
        }
    }
}

@Composable
private fun WidgetInfoItem(
    context: Context,
    widgetInfo: AppWidgetProviderInfo,
    onClick: () -> Unit
) {
    val pm = context.packageManager
    val view = LocalView.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var previewImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(widgetInfo) {
        previewImage = loadWidgetPreview(context, widgetInfo)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "item_scale"
    )

    val label = remember(widgetInfo) { widgetInfo.loadLabel(pm) }
    val sizeText = remember(widgetInfo) {
        "${widgetInfo.minWidth / 80}×${widgetInfo.minHeight / 80} cells"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) LauncherColors.DarkSurfaceVariant
                else LauncherColors.DarkSurface
            )
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = LauncherColors.AccentBlue,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    view.performHapticFeedback(buttonPressFeedbackConstant())
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            val imageBitmap = previewImage?.asImageBitmap()
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Widget preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = LauncherColors.TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(LauncherSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) Color.White else LauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = LauncherColors.TextSecondary
            )

            // If available
            widgetInfo.loadDescription(context)?.let { desc ->
                Text(
                    text = desc.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Configuration indicator
        if (widgetInfo.configure != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = LauncherColors.AccentBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "Configurable",
                    style = MaterialTheme.typography.labelSmall,
                    color = LauncherColors.AccentBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyWidgetsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = LauncherColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(LauncherSpacing.lg))

            Text(
                text = "No Widgets Available",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Text(
                text = "Install apps with widgets to see them here",
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }
    }
}

private suspend fun loadInstalledWidgets(
    context: Context,
    widgetManager: AppWidgetManager
): List<WidgetListItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    widgetManager.installedProviders
        .groupBy { it.provider.packageName }
        .mapNotNull { (packageName, widgets) ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                WidgetListItem(appName, packageName, widgets.sortedBy { it.loadLabel(pm) })
            } catch (_: Exception) {
                null
            }
        }
        .sortedBy { it.appName }
}

private suspend fun loadWidgetPreview(
    context: Context,
    widgetInfo: AppWidgetProviderInfo
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    try {
        val drawable = widgetInfo.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
            ?: widgetInfo.loadIcon(context, context.resources.displayMetrics.densityDpi)
        BitmapUtils.drawableToBitmap(drawable, defaultSize = 160)
    } catch (e: Exception) {
        null
    }
}