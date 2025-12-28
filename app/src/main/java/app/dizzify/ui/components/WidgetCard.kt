package app.dizzify.ui.components

import android.appwidget.AppWidgetHostView
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.dizzify.data.HomeItem
import app.dizzify.ui.theme.*

@Composable
fun WidgetCard(
    widget: HomeItem.Widget,
    hostView: AppWidgetHostView?,
    onRemove: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    height: Dp = 180.dp,
    isEditMode: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isFocused && isEditMode -> 1.02f
            isFocused -> 1.0f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "widget_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 4.dp,
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "widget_elevation"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused && isEditMode) 1f else if (isFocused) 0.5f else 0f,
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "widget_border"
    )

    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.DarkCardBackground.copy(alpha = 0.8f))
            .then(
                if (borderAlpha > 0f) {
                    Modifier.border(
                        width = 2.dp,
                        color = if (isEditMode) LauncherColors.AccentOrange.copy(alpha = borderAlpha)
                        else Color.White.copy(alpha = borderAlpha),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && isEditMode) {
                    when (event.key) {
                        Key.Menu, Key.Delete -> {
                            onRemove()
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onConfigure()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        if (hostView != null) {
            AndroidView(
                factory = { hostView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                update = { view ->
                    // Disable touch in edit mode
                    view.isClickable = !isEditMode
                    view.isFocusable = !isEditMode
                }
            )
        } else {
            // Placeholder when widget isn't loaded
            WidgetPlaceholder(
                packageName = widget.packageName,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Edit mode overlay
        if (isEditMode && isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = onConfigure,
                        modifier = Modifier
                            .size(48.dp)
                            .background(LauncherColors.AccentBlue, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configure",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .background(LauncherColors.Error, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPlaceholder(
    packageName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(LauncherColors.DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = LauncherColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = packageName.substringAfterLast('.'),
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }
    }
}

@Composable
fun AddWidgetCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "add_scale"
    )

    Box(
        modifier = modifier
            .size(160.dp, 120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) LauncherColors.AccentBlue.copy(alpha = 0.2f)
                else LauncherColors.DarkSurfaceVariant
            )
            .border(
                width = 2.dp,
                color = if (isFocused) LauncherColors.AccentBlue else LauncherColors.DarkSurfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = "Add Widget",
                modifier = Modifier.size(32.dp),
                tint = if (isFocused) LauncherColors.AccentBlue else LauncherColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add Widget",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) Color.White else LauncherColors.TextSecondary
            )
        }
    }
}