package app.dizzify.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dizzify.data.AppModel
import app.dizzify.helper.getUserHandleFromString
import app.dizzify.helper.openAppInfo
import app.dizzify.helper.uninstall
import app.dizzify.ui.theme.*
import kotlinx.coroutines.delay

data class AppOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val iconTint: Color = LauncherColors.TextPrimary,
    val isDestructive: Boolean = false,
    val action: () -> Unit
)

sealed class AppOptionContext {
    data class FromHome(
        val isFavorite: Boolean,
        val onToggleFavorite: () -> Unit
    ) : AppOptionContext()

    data class FromApps(
        val isHidden: Boolean
    ) : AppOptionContext()

    data class FromGames(
        val isHidden: Boolean
    ) : AppOptionContext()

    data class FromHidden(
        val placeholder: Unit = Unit
    ) : AppOptionContext()

    data class FromRecent(
        val onClearFromRecent: () -> Unit
    ) : AppOptionContext()
}

@Composable
fun AppOptionsSheet(
    app: AppModel,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    isFavorite: Boolean = false,
    isHidden: Boolean = false,
    context: AppOptionContext = AppOptionContext.FromApps(isHidden)
) {
    val androidContext = LocalContext.current

    val options = remember(app, isFavorite, isHidden, context) {
        buildList {
            add(AppOption(
                id = "open",
                label = "Open",
                icon = Icons.Filled.PlayArrow,
                iconTint = LauncherColors.AccentBlue,
                action = onOpen
            ))

            when (context) {
                is AppOptionContext.FromHome -> {
                    add(AppOption(
                        id = "favorite",
                        label = "Remove from Favorites",
                        icon = Icons.Filled.Favorite,
                        iconTint = LauncherColors.Error,
                        action = context.onToggleFavorite
                    ))
                }

                is AppOptionContext.FromApps, is AppOptionContext.FromGames -> {
                    add(AppOption(
                        id = "favorite",
                        label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        iconTint = if (isFavorite) LauncherColors.Error else LauncherColors.AccentOrange,
                        action = onToggleFavorite
                    ))
                }

                is AppOptionContext.FromRecent -> {
                    add(AppOption(
                        id = "clear_recent",
                        label = "Remove from Recent",
                        icon = Icons.Outlined.History,
                        iconTint = LauncherColors.TextSecondary,
                        action = context.onClearFromRecent
                    ))

                    add(AppOption(
                        id = "favorite",
                        label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        iconTint = if (isFavorite) LauncherColors.Error else LauncherColors.AccentOrange,
                        action = onToggleFavorite
                    ))
                }

                is AppOptionContext.FromHidden -> { }
            }

            add(AppOption(
                id = "info",
                label = "App Info",
                icon = Icons.Outlined.Info,
                action = {
                    val user = getUserHandleFromString(androidContext, app.userString)
                    openAppInfo(androidContext, user, app.appPackage)
                }
            ))

            when (context) {
                is AppOptionContext.FromHidden -> {
                    add(AppOption(
                        id = "unhide",
                        label = "Unhide",
                        icon = Icons.Filled.Visibility,
                        iconTint = LauncherColors.AccentTeal,
                        action = onToggleHidden
                    ))
                }

                is AppOptionContext.FromApps, is AppOptionContext.FromGames, is AppOptionContext.FromRecent -> {
                    add(AppOption(
                        id = "hide",
                        label = "Hide",
                        icon = Icons.Outlined.VisibilityOff,
                        action = onToggleHidden
                    ))
                }

                else -> {}
            }

            add(AppOption(
                id = "uninstall",
                label = "Uninstall",
                icon = Icons.Outlined.Delete,
                iconTint = LauncherColors.Error,
                isDestructive = true,
                action = { androidContext.uninstall(app.appPackage) }
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
                AppOptionsContent(
                    app = app,
                    options = options,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun AppOptionsContent(
    app: AppModel,
    options: List<AppOption>,
    onDismiss: () -> Unit
) {
    val focusRequesters = remember { options.map { FocusRequester() } }
    val view = LocalView.current

    // Request focus after a short delay to avoid consuming the key event that opened the dialog
    LaunchedEffect(Unit) {
        delay(100)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(420.dp)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LauncherSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                app = app,
                size = LauncherCardSizes.appIconLarge,
                showShadow = true
            )

            Spacer(modifier = Modifier.width(LauncherSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = app.appPackage,
                    style = MaterialTheme.typography.bodySmall,
                    color = LauncherColors.TextTertiary,
                    maxLines = 1
                )
            }
        }

        HorizontalDivider(
            color = LauncherColors.DarkSurfaceVariant,
            modifier = Modifier.padding(bottom = LauncherSpacing.md)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(LauncherSpacing.xs)
        ) {
            options.forEachIndexed { index, option ->
                OptionItem(
                    option = option,
                    focusRequester = focusRequesters[index],
                    onAction = {
                        view.performHapticFeedback(buttonPressFeedbackConstant())
                        option.action()
                        onDismiss()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(LauncherSpacing.md))

        Text(
            text = "Hold SELECT for options • Press BACK to close",
            style = MaterialTheme.typography.labelSmall,
            color = LauncherColors.TextTertiary
        )
    }
}

@Composable
private fun OptionItem(
    option: AppOption,
    focusRequester: FocusRequester,
    onAction: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableLongStateOf(0L) }
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
                val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (isSelectKey && pressStartTime == 0L) {
                            pressStartTime = System.currentTimeMillis()
                        }
                        isSelectKey
                    }
                    KeyEventType.KeyUp -> {
                        if (isSelectKey) {
                            val duration = System.currentTimeMillis() - pressStartTime
                            pressStartTime = 0L
                            // Only trigger if it was a real press (duration > 0)
                            if (duration > 0) {
                                onAction()
                            }
                        }
                        isSelectKey
                    }
                    else -> false
                }
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