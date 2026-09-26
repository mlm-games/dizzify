package app.dizzify.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember {
        SidebarDestinations.associate { it.route to FocusRequester() }
    }

    Box(
        modifier = modifier
            .width(LauncherSpacing.sidebarWidth)
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
            SidebarClock()

            Spacer(modifier = Modifier.height(LauncherSpacing.sm))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                items(
                    count = SidebarDestinations.size,
                    key = { SidebarDestinations[it].route }
                ) { index ->
                    val destination = SidebarDestinations[index]
                    SidebarItem(
                        destination = destination,
                        isSelected = currentDestination == destination,
                        onClick = { onDestinationSelected(destination) },
                        focusRequester = focusRequesters.getValue(destination.route)
                    )
                }
            }

            SidebarQuickActions()
        }
    }
}

@Composable
private fun SidebarClock() {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // The rail only fits "HH:" per line, so the minutes wrap below the colon.
    val timeText = timeFormat.format(Date(currentTime)).replace(":", ":\n")

    Text(
        text = timeText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LauncherSpacing.md),
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        color = Color.White
    )
}

@Composable
private fun SidebarItem(
    destination: SidebarDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
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

    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = LauncherSpacing.sm)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (isFocused || isSelected) Modifier.border(
                    width = 2.dp,
                    color = LauncherColors.AccentBlue.copy(alpha = 0.5f),
                    shape = shape
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .tvPointerClick(onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected || isFocused) destination.iconSelected else destination.iconUnselected,
                contentDescription = destination.label,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(4.dp)
                        .background(LauncherColors.AccentBlue, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SidebarQuickActions() {
    val context = LocalContext.current

    // IconButton's 48dp minimum would put three of them at 144dp in an 80dp rail, so the
    // quick actions are plain 26dp targets.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickAction(
            icon = Icons.Outlined.VideoSettings,
            contentDescription = "Settings",
            onClick = { SettingsShortcuts.openDeviceSettings(context) }
        )
        QuickAction(
            icon = Icons.Outlined.Wifi,
            contentDescription = "Network",
            onClick = { SettingsShortcuts.openWifi(context) }
        )
        QuickAction(
            icon = Icons.Outlined.Bluetooth,
            contentDescription = "Bluetooth",
            onClick = { SettingsShortcuts.openBluetooth(context) }
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (isFocused) LauncherColors.AccentBlue.copy(alpha = 0.3f) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (isFocused) LauncherColors.AccentBlue else LauncherColors.TextSecondary
        )
    }
}
