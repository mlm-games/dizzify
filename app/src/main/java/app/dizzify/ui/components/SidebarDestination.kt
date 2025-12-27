package app.dizzify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.platform.SettingsShortcuts
import app.dizzify.ui.theme.LauncherAnimation
import app.dizzify.ui.theme.LauncherColors
import app.dizzify.ui.theme.LauncherSpacing
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SidebarDestination(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
) {
    data object Home : SidebarDestination(
        route = "home",
        label = "Home",
        iconSelected = Icons.Filled.Home,
        iconUnselected = Icons.Outlined.Home
    )

    data object Apps : SidebarDestination(
        route = "apps",
        label = "Apps",
        iconSelected = Icons.Filled.Apps,
        iconUnselected = Icons.Filled.Apps
    )

    data object Games : SidebarDestination(
        route = "games",
        label = "Games",
        iconSelected = Icons.Filled.SportsEsports,
        iconUnselected = Icons.Outlined.SportsEsports
    )

    data object Hidden : SidebarDestination(
        route = "hidden",
        label = "Hidden",
        iconSelected = Icons.Filled.VisibilityOff,
        iconUnselected = Icons.Outlined.VisibilityOff
    )

    data object Settings : SidebarDestination(
        route = "settings",
        label = "Settings",
        iconSelected = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings
    )
}

/**
 * IMPORTANT: keep this list as a stable top-level value
 * to prevent weird recomposition/state slot behavior.
 */
val SidebarDestinations: List<SidebarDestination> = listOf(
    SidebarDestination.Home,
    SidebarDestination.Apps,
    SidebarDestination.Games,
    SidebarDestination.Hidden,
    SidebarDestination.Settings
)

@Composable
fun LauncherSidebar(
    currentDestination: SidebarDestination,
    onDestinationSelected: (SidebarDestination) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val focusRequesters = remember {
        SidebarDestinations.associateBy({ it.route }, { FocusRequester() })
    }

    var anyItemFocused by remember { mutableStateOf(false) }

    LaunchedEffect(anyItemFocused) {
        onExpandedChange(anyItemFocused)
    }

    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) LauncherSpacing.sidebarExpandedWidth else LauncherSpacing.sidebarWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sidebar_width"
    )

    Box(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        LauncherColors.DarkBackground.copy(alpha = 0.95f),
                        LauncherColors.DarkBackground.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = LauncherSpacing.lg)
        ) {
            SidebarClock(isExpanded = isExpanded)

            Spacer(modifier = Modifier.height(LauncherSpacing.xxl))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LauncherSpacing.sm)
            ) {
                SidebarDestinations.forEach { destination ->
                    val fr = focusRequesters[destination.route] ?: FocusRequester()
                    SidebarItem(
                        destination = destination,
                        isSelected = currentDestination == destination,
                        isExpanded = isExpanded,
                        onClick = { onDestinationSelected(destination) },
                        focusRequester = fr,
                        onFocusedChanged = { focused ->
                            anyItemFocused = focused || anyItemFocused
                            if (!focused) {
//                                LaunchedEffect(Unit) {
//                                    delay(80)
                                    anyItemFocused = false
//                                }
                            }
                        }
                    )
                }
            }

            SidebarQuickActions(isExpanded = isExpanded)
        }
    }
}

@Composable
private fun SidebarClock(isExpanded: Boolean) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherSpacing.md),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(
            text = timeFormat.format(Date(currentTime)),
            style = if (isExpanded) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
            color = Color.White
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = dateFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.bodyLarge,
                color = LauncherColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SidebarItem(
    destination: SidebarDestination?,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusedChanged: (Boolean) -> Unit,
) {
    if (destination == null) return

    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> LauncherColors.AccentBlue.copy(alpha = 0.3f)
            isSelected -> LauncherColors.AccentBlue.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "bg_color"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused || isSelected -> LauncherColors.AccentBlue
            else -> LauncherColors.TextSecondary
        },
        label = "icon_color"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "item_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherSpacing.sm)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = LauncherColors.AccentBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                onFocusedChanged(state.isFocused)
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected || isFocused) destination.iconSelected else destination.iconUnselected,
            contentDescription = destination.label,
            modifier = Modifier.size(28.dp),
            tint = iconColor
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row {
                Spacer(modifier = Modifier.width(LauncherSpacing.md))
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused || isSelected) Color.White else LauncherColors.TextSecondary
                )
            }
        }

        if (isSelected && !isExpanded) {
            Spacer(modifier = Modifier.width(LauncherSpacing.xs))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(LauncherColors.AccentBlue, CircleShape)
            )
        }
    }
}

@Composable
private fun SidebarQuickActions(isExpanded: Boolean) {

    var showPowerMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherSpacing.md),
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { showPowerMenu = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoSettings,
                contentDescription = "Settings",
                tint = LauncherColors.TextSecondary
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row {
                Spacer(modifier = Modifier.width(LauncherSpacing.sm))

                IconButton(
                    onClick = { SettingsShortcuts.openWifi(context) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = "Network",
                        tint = LauncherColors.TextSecondary
                    )
                }

                IconButton(
                    onClick = { SettingsShortcuts.openBluetooth(context) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bluetooth,
                        contentDescription = "Bluetooth",
                        tint = LauncherColors.TextSecondary
                    )
                }
            }
        }

        if (showPowerMenu) {
//            AlertDialog(
//                onDismissRequest = { showPowerMenu = false },
//                title = { Text("Power") },
//                text = { Text("Choose an action") },
////                confirmButton = {
////                    TextButton(onClick = {
////                        showPowerMenu = false
////                        // lock / sleep may need accessibility?
////                    }) { Text("Sleep") }
////                },
//                dismissButton = {
//                    TextButton(onClick = {
                        showPowerMenu = false
                        SettingsShortcuts.openDeviceSettings(context)
//                    }) { Text("Settings") }
//                }
//            )
        }
    }
}