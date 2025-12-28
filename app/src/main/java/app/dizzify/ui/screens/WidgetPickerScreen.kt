package app.dizzify.ui.screens

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem as TvListItem
import app.dizzify.helper.BitmapUtils
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
    val dims = LocalLauncherDimens.current
    val context = LocalContext.current
    val widgetManager = AppWidgetManager.getInstance(context)

    var widgetList by remember { mutableStateOf<List<WidgetListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        widgetList = loadInstalledWidgets(context, widgetManager)
        isLoading = false
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
                modifier = Modifier.padding(dims.screenPadding)
            )

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LauncherColors.AccentBlue)
                }
                widgetList.isEmpty() -> EmptyWidgetsState(modifier = Modifier.fillMaxSize())
                else -> WidgetList(
                    widgetList = widgetList,
                    onWidgetSelected = onWidgetSelected,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WidgetPickerHeader(
    onDismiss: () -> Unit,
    widgetCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.DarkSurface)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
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
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = LocalLauncherDimens.current
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dims.screenPadding,
            end = dims.screenPadding,
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
            ) { _, widgetInfo ->
                WidgetInfoItem(
                    context = context,
                    widgetInfo = widgetInfo,
                    onClick = { onWidgetSelected(widgetInfo) }
                )
            }

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

    var previewImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(widgetInfo) {
        previewImage = loadWidgetPreview(context, widgetInfo)
    }

    val label = remember(widgetInfo) { widgetInfo.loadLabel(pm) }
    val sizeText = remember(widgetInfo) { "${widgetInfo.minWidth / 80}×${widgetInfo.minHeight / 80} cells" }
    val desc = remember(widgetInfo) { widgetInfo.loadDescription(context)?.toString() }

    TvListItem(
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
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
        },
        headlineContent = {
            Text(
                text = label.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(text = sizeText, style = MaterialTheme.typography.bodySmall, color = LauncherColors.TextSecondary)
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = LauncherColors.TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
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
    )
}

@Composable
private fun EmptyWidgetsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = LauncherColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(LauncherSpacing.lg))
            Text(text = "No Widgets Available", style = MaterialTheme.typography.headlineMedium, color = Color.White)
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
    } catch (_: Exception) {
        null
    }
}