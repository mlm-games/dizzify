package app.dizzify.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppModel
import app.dizzify.ui.theme.*

enum class CardStyle {
    STANDARD,
    COMPACT,
    BANNER,
    MINIMAL
}

data class CardVisualConfig(
    val shape: RoundedCornerShape,
    val useGlow: Boolean,
    val focusedElevation: Dp,
    val unfocusedElevation: Dp,
    val focusScale: Float,
    val pressScale: Float
)

@Composable
private fun rememberCardVisualConfig(
    style: CardStyle,
    hasBannerContent: Boolean
): CardVisualConfig {
    return remember(style, hasBannerContent) {
        when (style) {
            CardStyle.BANNER if hasBannerContent -> CardVisualConfig(
                shape = RoundedCornerShape(8.dp),
                useGlow = false,
                focusedElevation = 12.dp,
                unfocusedElevation = 2.dp,
                focusScale = 1.03f,
                pressScale = 0.98f
            )
            CardStyle.BANNER -> CardVisualConfig(
                shape = RoundedCornerShape(12.dp),
                useGlow = true,
                focusedElevation = 16.dp,
                unfocusedElevation = 4.dp,
                focusScale = 1.05f,
                pressScale = 0.97f
            )
            CardStyle.COMPACT -> CardVisualConfig(
                shape = RoundedCornerShape(12.dp),
                useGlow = true,
                focusedElevation = 20.dp,
                unfocusedElevation = 4.dp,
                focusScale = 1.08f,
                pressScale = 0.95f
            )
            CardStyle.MINIMAL -> CardVisualConfig(
                shape = RoundedCornerShape(16.dp),
                useGlow = true,
                focusedElevation = 16.dp,
                unfocusedElevation = 2.dp,
                focusScale = 1.1f,
                pressScale = 0.95f
            )
            else -> CardVisualConfig(
                shape = RoundedCornerShape(20.dp),
                useGlow = true,
                focusedElevation = 24.dp,
                unfocusedElevation = 4.dp,
                focusScale = 1.08f,
                pressScale = 0.95f
            )
        }
    }
}

private const val LONG_PRESS_THRESHOLD_MS = 500L

@Composable
fun AppCard(
    app: AppModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.STANDARD,
    focusRequester: FocusRequester = remember { FocusRequester() },
    showNewBadge: Boolean = app.isNew
) {
    var isFocused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Simple time-based long press detection
    var pressStartTime by remember { mutableLongStateOf(0L) }

    val hasBannerContent = style == CardStyle.BANNER && app.hasBanner && app.appIcon != null
    val visualConfig = rememberCardVisualConfig(style, hasBannerContent)

    val cardSize = when (style) {
        CardStyle.STANDARD -> Modifier.size(LauncherCardSizes.appCardWidth, LauncherCardSizes.appCardHeight)
        CardStyle.COMPACT -> Modifier.size(120.dp, 150.dp)
        CardStyle.BANNER -> Modifier.size(LauncherCardSizes.bannerCardWidth, LauncherCardSizes.bannerCardHeight)
        CardStyle.MINIMAL -> Modifier.size(LauncherCardSizes.smallCardSize + 20.dp)
    }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> visualConfig.pressScale
            isFocused -> visualConfig.focusScale
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isFocused) visualConfig.focusedElevation else visualConfig.unfocusedElevation,
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "card_elevation"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(LauncherAnimation.FastDuration),
        label = "border_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(isFocused) {
        if (isFocused) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            // Reset press state when losing focus
            isPressed = false
            pressStartTime = 0L
        }
    }

    Box(
        modifier = modifier
            .then(cardSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isFocused && visualConfig.useGlow) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = LauncherColors.AccentBlue.copy(alpha = glowAlpha * 0.3f),
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            size = size.copy(
                                width = size.width + 20.dp.toPx(),
                                height = size.height + 20.dp.toPx()
                            ),
                            topLeft = Offset(-10.dp.toPx(), -10.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .shadow(elevation, visualConfig.shape, clip = false)
            .clip(visualConfig.shape)
            .background(
                when {
                    hasBannerContent -> Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent)
                    )
                    isFocused -> Brush.verticalGradient(
                        listOf(
                            LauncherColors.DarkCardBackground,
                            LauncherColors.DarkCardBackground.copy(alpha = 0.95f)
                        )
                    )
                    else -> Brush.verticalGradient(
                        listOf(LauncherColors.DarkCardBackground, LauncherColors.DarkSurface)
                    )
                }
            )
            .then(
                when {
                    hasBannerContent && isFocused -> Modifier.border(
                        width = 3.dp,
                        color = Color.White,
                        shape = visualConfig.shape
                    )
                    borderAlpha > 0f && !hasBannerContent -> Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = borderAlpha),
                        shape = visualConfig.shape
                    )
                    else -> Modifier
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state -> isFocused = state.isFocused }
            .onKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        when {
                            isSelectKey -> {
                                if (pressStartTime == 0L) {
                                    pressStartTime = System.currentTimeMillis()
                                    isPressed = true
                                }
                                true
                            }
                            event.key == Key.Menu || event.key == Key.TvMediaContextMenu -> {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongClick()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        when {
                            isSelectKey -> {
                                val pressDuration = System.currentTimeMillis() - pressStartTime
                                isPressed = false
                                pressStartTime = 0L

                                if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongClick()
                                } else if (pressDuration > 0) {
                                    view.performHapticFeedback(buttonPressFeedbackConstant())
                                    onClick()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        when (style) {
            CardStyle.STANDARD -> StandardCardContent(app, isFocused, showNewBadge)
            CardStyle.COMPACT -> CompactCardContent(app, isFocused, showNewBadge)
            CardStyle.BANNER -> BannerCardContent(app, isFocused, showNewBadge)
            CardStyle.MINIMAL -> MinimalCardContent(app, isFocused)
        }
    }
}

@Composable
private fun StandardCardContent(
    app: AppModel,
    isFocused: Boolean,
    showNewBadge: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                app = app,
                size = LauncherCardSizes.appIconLarge,
                showShadow = isFocused,
                showGlow = isFocused
            )

            if (showNewBadge) {
                NewBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) Color.White else LauncherColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactCardContent(
    app: AppModel,
    isFocused: Boolean,
    showNewBadge: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            AppIcon(
                app = app,
                size = LauncherCardSizes.appIconMedium,
                showShadow = isFocused,
                showGlow = isFocused
            )

            if (showNewBadge) {
                NewBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = app.appLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) Color.White else LauncherColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BannerCardContent(
    app: AppModel,
    isFocused: Boolean,
    showNewBadge: Boolean
) {
    val hasBanner = app.hasBanner && app.appIcon != null

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            hasBanner -> {
                Image(
                    bitmap = app.appIcon,
                    contentDescription = app.appLabel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                val gradientHeight by animateDpAsState(
                    targetValue = if (isFocused) 80.dp else 60.dp,
                    label = "gradient_height"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gradientHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (isFocused) 0.9f else 0.75f)
                                )
                            )
                        )
                )

                Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }

            app.appIcon != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(LauncherColors.DarkCardBackground, LauncherColors.DarkSurface)
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        app = app,
                        size = LauncherCardSizes.appIconLarge,
                        showShadow = isFocused,
                        showGlow = isFocused
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isFocused) Color.White else LauncherColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LauncherColors.DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isFocused) Color.White else LauncherColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (showNewBadge) {
            NewBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun MinimalCardContent(
    app: AppModel,
    isFocused: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            app = app,
            size = LauncherCardSizes.appIconMedium,
            showShadow = isFocused,
            showGlow = isFocused
        )
    }
}

@Composable
fun AppIcon(
    app: AppModel,
    size: Dp,
    modifier: Modifier = Modifier,
    showShadow: Boolean = false,
    showGlow: Boolean = false,
) {
    val icon = app.appIcon
    val cornerRadius = size / 4

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = LauncherColors.AccentBlue.copy(alpha = 0.15f),
                            radius = this.size.minDimension / 2 + 12.dp.toPx()
                        )
                    }
                } else Modifier
            )
            .then(
                if (showShadow) {
                    Modifier.shadow(8.dp, RoundedCornerShape(cornerRadius))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.appLabel,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(LauncherColors.DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.6f),
                    tint = LauncherColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun NewBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(LauncherColors.AccentBlue, CircleShape)
            .padding(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.NewReleases,
            contentDescription = "New",
            modifier = Modifier.size(12.dp),
            tint = Color.White
        )
    }
}