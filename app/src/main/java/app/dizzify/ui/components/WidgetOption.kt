package app.dizzify.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dizzify.data.HomeItem
import app.dizzify.ui.theme.*

data class WidgetOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val iconTint: Color = LauncherColors.TextPrimary,
    val isDestructive: Boolean = false,
    val action: () -> Unit
)

@Composable
fun WidgetOptionsSheet(
    widget: HomeItem.Widget,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
    onResize: (rowSpan: Int, columnSpan: Int) -> Unit
) {
    var showResizeDialog by remember { mutableStateOf(false) }

    val options = remember(widget) {
        buildList {
            add(WidgetOption(
                id = "configure",
                label = "Configure Widget",
                icon = Icons.Outlined.Settings,
                iconTint = LauncherColors.AccentBlue,
                action = onConfigure
            ))

            add(WidgetOption(
                id = "resize",
                label = "Resize Widget",
                icon = Icons.Outlined.AspectRatio,
                iconTint = LauncherColors.AccentTeal,
                action = { showResizeDialog = true }
            ))

            add(WidgetOption(
                id = "remove",
                label = "Remove Widget",
                icon = Icons.Outlined.Delete,
                iconTint = LauncherColors.Error,
                isDestructive = true,
                action = onRemove
            ))
        }
    }

    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            onDismiss()
                            true
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                if (showResizeDialog) {
                    WidgetResizeDialog(
                        currentRowSpan = widget.rowSpan,
                        currentColumnSpan = widget.columnSpan,
                        onResize = { rows, cols ->
                            onResize(rows, cols)
                            showResizeDialog = false
                            onDismiss()
                        },
                        onDismiss = { showResizeDialog = false }
                    )
                } else {
                    WidgetOptionsContent(
                        widget = widget,
                        options = options,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetOptionsContent(
    widget: HomeItem.Widget,
    options: List<WidgetOption>,
    onDismiss: () -> Unit
) {
    val focusRequesters = remember { options.map { FocusRequester() } }
    val view = LocalView.current

    LaunchedEffect(Unit) {
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(380.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LauncherColors.DarkSurface,
                        LauncherColors.DarkBackground
                    )
                )
            )
            .padding(LauncherSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LauncherSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.AccentBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = LauncherColors.AccentBlue
                )
            }

            Spacer(modifier = Modifier.width(LauncherSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Widget Options",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = widget.packageName.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextTertiary
                )
            }
        }

        HorizontalDivider(
            color = LauncherColors.DarkSurfaceVariant,
            modifier = Modifier.padding(bottom = LauncherSpacing.md)
        )

        // Options
        Column(
            verticalArrangement = Arrangement.spacedBy(LauncherSpacing.xs)
        ) {
            options.forEachIndexed { index, option ->
                WidgetOptionItem(
                    option = option,
                    focusRequester = focusRequesters[index],
                    onAction = {
                        view.performHapticFeedback(buttonPressFeedbackConstant())
                        option.action()
                        if (option.id != "resize") {
                            onDismiss()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(LauncherSpacing.md))

        Text(
            text = "Press BACK to close",
            style = MaterialTheme.typography.labelSmall,
            color = LauncherColors.TextTertiary
        )
    }
}

@Composable
private fun WidgetOptionItem(
    option: WidgetOption,
    focusRequester: FocusRequester,
    onAction: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val view = LocalView.current

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused && option.isDestructive -> LauncherColors.Error.copy(alpha = 0.2f)
            isFocused -> LauncherColors.AccentBlue.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        label = "option_bg"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "option_scale"
    )

    LaunchedEffect(isFocused) {
        if (isFocused) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = if (option.isDestructive)
                        LauncherColors.Error.copy(alpha = 0.5f)
                    else
                        LauncherColors.AccentBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onAction()
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(option.iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = option.iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(LauncherSpacing.md))

        Text(
            text = option.label,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                option.isDestructive && isFocused -> LauncherColors.Error
                isFocused -> Color.White
                else -> LauncherColors.TextPrimary
            }
        )
    }
}

@Composable
private fun WidgetResizeDialog(
    currentRowSpan: Int,
    currentColumnSpan: Int,
    onResize: (rowSpan: Int, columnSpan: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var rowSpan by remember { mutableIntStateOf(currentRowSpan) }
    var columnSpan by remember { mutableIntStateOf(currentColumnSpan) }

    val rowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rowFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(340.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(LauncherColors.DarkSurface)
            .padding(LauncherSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Resize Widget",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        // Row span selector
        SizeSelector(
            label = "Height",
            value = rowSpan,
            range = 1..4,
            onValueChange = { rowSpan = it },
            focusRequester = rowFocusRequester
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.md))

        // Column span selector
        SizeSelector(
            label = "Width",
            value = columnSpan,
            range = 1..4,
            onValueChange = { columnSpan = it }
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        // Preview
        Box(
            modifier = Modifier
                .size(
                    width = (columnSpan * 60).dp,
                    height = (rowSpan * 40).dp
                )
                .clip(RoundedCornerShape(8.dp))
                .background(LauncherColors.AccentBlue.copy(alpha = 0.2f))
                .border(2.dp, LauncherColors.AccentBlue, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${columnSpan}×${rowSpan}",
                style = MaterialTheme.typography.titleMedium,
                color = LauncherColors.AccentBlue
            )
        }

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.md)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = { onResize(rowSpan, columnSpan) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LauncherColors.AccentBlue
                )
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun SizeSelector(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) LauncherColors.DarkSurfaceVariant
                else Color.Transparent
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (value > range.first) {
                                onValueChange(value - 1)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (value < range.last) {
                                onValueChange(value + 1)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) Color.White else LauncherColors.TextSecondary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.sm)
        ) {
            IconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (value > range.first) LauncherColors.AccentBlue else LauncherColors.TextTertiary
                )
            }

            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.width(32.dp)
            )

            IconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = if (value < range.last) LauncherColors.AccentBlue else LauncherColors.TextTertiary
                )
            }
        }
    }
}


fun buttonPressFeedbackConstant() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    HapticFeedbackConstants.CONFIRM
} else {
    HapticFeedbackConstants.VIRTUAL_KEY
}