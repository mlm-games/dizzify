package app.dizzify.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.helper.AppLaunchResolver
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
    context: AppOptionContext = AppOptionContext.FromApps(isHidden),
    onOpenTv: (() -> Unit)? = null,
    onOpenMobile: (() -> Unit)? = null,
    launchMode: AppLaunchMode = AppLaunchMode.AUTO,
    onLaunchModeChange: ((AppLaunchMode) -> Unit)? = null,
    onRenameRequest: ((String) -> Unit)? = null,
    onToggleHome: (() -> Unit)? = null,
    isOnHome: Boolean = false,
) {
    val androidContext = LocalContext.current

    val options = remember(app, isFavorite, isHidden, isOnHome, context, launchMode) {
        buildList {
            add(AppOption(
                id = "open",
                label = "Open",
                icon = Icons.Filled.PlayArrow,
                iconTint = LauncherColors.AccentBlue,
                action = onOpen
            ))

            if (app.supportsBoth) {
                if (onOpenTv != null) {
                    add(AppOption(
                        id = "open_tv",
                        label = "Open TV version",
                        icon = Icons.Filled.Tv,
                        iconTint = LauncherColors.AccentTeal,
                        action = onOpenTv
                    ))
                }
                if (onOpenMobile != null) {
                    add(AppOption(
                        id = "open_mobile",
                        label = "Open phone version",
                        icon = Icons.Filled.Smartphone,
                        iconTint = LauncherColors.AccentPurple,
                        action = onOpenMobile
                    ))
                }
                if (onLaunchModeChange != null) {
                    val next = when (launchMode) {
                        AppLaunchMode.AUTO -> AppLaunchMode.TV
                        AppLaunchMode.TV -> AppLaunchMode.MOBILE
                        AppLaunchMode.MOBILE -> AppLaunchMode.AUTO
                    }
                    val label = when (launchMode) {
                        AppLaunchMode.TV -> "Default: TV version"
                        AppLaunchMode.MOBILE -> "Default: Phone version"
                        AppLaunchMode.AUTO -> "Default: Auto"
                    }
                    add(AppOption(
                        id = "launch_mode",
                        label = "$label (tap to change)",
                        icon = Icons.Outlined.Tune,
                        iconTint = LauncherColors.TextSecondary,
                        action = { onLaunchModeChange(next) }
                    ))
                }
            }

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

            if (onToggleHome != null &&
                (context is AppOptionContext.FromApps || context is AppOptionContext.FromGames)
            ) {
                add(AppOption(
                    id = "home",
                    label = if (isOnHome) "Remove from Home" else "Add to Home",
                    icon = if (isOnHome) Icons.Filled.Home else Icons.Outlined.Home,
                    iconTint = if (isOnHome) LauncherColors.Error else LauncherColors.AccentTeal,
                    action = onToggleHome
                ))
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

            if (onRenameRequest != null) {
                add(AppOption(
                    id = "rename",
                    label = "Rename",
                    icon = Icons.Outlined.Edit,
                    iconTint = LauncherColors.TextSecondary,
                    action = { onRenameRequest(app.appLabel) }
                ))
            }

            add(AppOption(
                id = "store",
                label = "Open in Play Store",
                icon = Icons.Outlined.Shop,
                iconTint = LauncherColors.TextSecondary,
                action = { AppLaunchResolver.openInPlayStore(androidContext, app.appPackage) }
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

/**
 * Owns the options sheet plus the rename dialog so that selecting "Rename" (which closes the
 * sheet) does not unmount the state the rename dialog lives in. Call [AppOptionsHost] once per
 * screen; it stays composed for the whole screen lifetime.
 */
@Stable
class AppOptionsState {
    var app by mutableStateOf<AppModel?>(null)
        private set
    var isVisible by mutableStateOf(false)
        private set
    var renaming by mutableStateOf<RenameTarget?>(null)
        private set

    fun open(app: AppModel) {
        this.app = app
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
        app = null
    }

    fun requestRename(initial: String) {
        val current = app ?: return
        renaming = RenameTarget(current, initial)
        dismiss()
    }

    fun cancelRename() {
        renaming = null
    }

    fun commitRename(newName: String?, onRename: (AppModel, String?) -> Unit) {
        val target = renaming ?: return
        renaming = null
        onRename(target.app, newName)
    }
}

data class RenameTarget(val app: AppModel, val initialText: String)

@Composable
fun rememberAppOptionsState(): AppOptionsState = remember { AppOptionsState() }

/**
 * Call this unconditionally, once per screen. It must not be wrapped in a
 * `state.app?.let {}` by the caller: dismissing the sheet clears [AppOptionsState.app], and the
 * rename dialog that follows has to outlive that.
 */
@Composable
fun AppOptionsHost(
    state: AppOptionsState,
    onOpen: (AppModel) -> Unit,
    onToggleHidden: (AppModel) -> Unit,
    onToggleFavorite: (AppModel) -> Unit = {},
    isFavorite: (AppModel) -> Boolean = { false },
    isHidden: (AppModel) -> Boolean = { false },
    contextFor: (AppModel) -> AppOptionContext = { AppOptionContext.FromApps(it.isHidden) },
    onOpenTv: ((AppModel) -> Unit)? = null,
    onOpenMobile: ((AppModel) -> Unit)? = null,
    launchModeFor: (AppModel) -> AppLaunchMode = { AppLaunchMode.AUTO },
    onLaunchModeChange: ((AppModel, AppLaunchMode) -> Unit)? = null,
    onRename: ((AppModel, String?) -> Unit)? = null,
    onToggleHome: ((AppModel) -> Unit)? = null,
    isOnHome: (AppModel) -> Boolean = { false },
) {
    state.app?.let { app ->
        AppOptionsSheet(
            app = app,
            isVisible = state.isVisible,
            onDismiss = state::dismiss,
            onOpen = { onOpen(app) },
            onToggleHidden = { onToggleHidden(app) },
            onToggleFavorite = { onToggleFavorite(app) },
            isFavorite = isFavorite(app),
            isHidden = isHidden(app),
            context = contextFor(app),
            onOpenTv = onOpenTv?.let { cb -> { cb(app) } },
            onOpenMobile = onOpenMobile?.let { cb -> { cb(app) } },
            launchMode = launchModeFor(app),
            onLaunchModeChange = onLaunchModeChange?.let { cb -> { mode -> cb(app, mode) } },
            onRenameRequest = onRename?.let { { initial -> state.requestRename(initial) } },
            onToggleHome = onToggleHome?.let { cb -> { cb(app) } },
            isOnHome = isOnHome(app)
        )
    }

    state.renaming?.let { target ->
        RenameAppDialog(
            initialText = target.initialText,
            packageName = target.app.appPackage,
            onConfirm = { newName -> state.commitRename(newName, onRename ?: { _, _ -> }) },
            onDismiss = state::cancelRename
        )
    }
}

@Composable
private fun RenameAppDialog(
    initialText: String,
    packageName: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // A Dialog window does not resize for the IME. Inset the whole dialog and centre it in
        // what is left, then scroll the contents, so Save/Cancel never sit under the keyboard.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = LauncherColors.DarkSurface,
                modifier = Modifier.width(400.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(LauncherSpacing.lg)
                ) {
                    Text(
                        text = "Rename app",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(LauncherSpacing.sm))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text(packageName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(LauncherSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(text.trim().ifEmpty { null }) }) { Text("Save") }
                    }
                }
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
    val focusRequesters = remember(options) { options.map { FocusRequester() } }
    val view = LocalView.current

    LaunchedEffect(options) {
        delay(100)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight(0.85f)
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
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
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
            .tvPointerClick(onClick = onAction)
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
