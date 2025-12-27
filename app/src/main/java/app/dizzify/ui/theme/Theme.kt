package app.dizzify.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LauncherColors {
    // Dark theme (primary for TV)
    val DarkBackground = Color(0xFF0D0D0D)
    val DarkSurface = Color(0xFF1A1A1A)
    val DarkSurfaceVariant = Color(0xFF2D2D2D)
    val DarkCardBackground = Color(0xFF1E1E1E)

    // Accent colors
    val AccentBlue = Color(0xFF4A9EFF)
    val AccentPurple = Color(0xFF9D4EDD)
    val AccentTeal = Color(0xFF00BFA5)
    val AccentOrange = Color(0xFFFF6D00)

    // Focus states
    val FocusRing = Color(0xFFFFFFFF)
    val FocusGlow = Color(0x40FFFFFF)

    // Text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextTertiary = Color(0xFF666666)

    // Status
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFE53935)
}

object LauncherSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp

    // TV-specific spacing (larger for 10-foot UI)
    val screenPadding = 48.dp
    val sidebarWidth = 80.dp
    val sidebarExpandedWidth = 280.dp
    val cardGap = 16.dp
    val rowGap = 32.dp
    val sectionGap = 48.dp
}

object LauncherCardSizes {
    // Standard app cards
    val appCardWidth = 160.dp
    val appCardHeight = 200.dp

    // Banner cards (TV banners - 16:9 aspect ratio)
    val bannerCardWidth = 320.dp
    val bannerCardHeight = 180.dp

    // Wide cards (for widgets/banners)
    val wideCardWidth = 340.dp
    val wideCardHeight = 120.dp

    // Small quick-access cards
    val smallCardSize = 80.dp

    // Icon sizes
    val appIconLarge = 64.dp
    val appIconMedium = 48.dp
    val appIconSmall = 32.dp
}


// Animation configuration
object LauncherAnimation {
    const val FastDuration = 150
    const val NormalDuration = 250
    const val SlowDuration = 400

    const val FocusScale = 1.08f
    const val PressScale = 0.95f

    const val StaggerDelayMs = 50L
}

private fun defaultTypography() = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    ),
)

// Local composition for launcher-specific values
data class LauncherDimens(
    val screenPadding: Dp = LauncherSpacing.screenPadding,
    val sidebarWidth: Dp = LauncherSpacing.sidebarWidth,
    val sidebarExpandedWidth: Dp = LauncherSpacing.sidebarExpandedWidth,
    val cardWidth: Dp = LauncherCardSizes.appCardWidth,
    val cardHeight: Dp = LauncherCardSizes.appCardHeight
)

@Composable
fun scaledTypography(scaleFactor: Float): Typography {
    val defaultTypo = defaultTypography()

    return Typography(
        displayLarge = defaultTypo.displayLarge.copy(fontSize = defaultTypo.displayLarge.fontSize * scaleFactor),
        displayMedium = defaultTypo.displayMedium.copy(fontSize = defaultTypo.displayMedium.fontSize * scaleFactor),
        displaySmall = defaultTypo.displaySmall.copy(fontSize = defaultTypo.displaySmall.fontSize * scaleFactor),
        headlineLarge = defaultTypo.headlineLarge.copy(fontSize = defaultTypo.headlineLarge.fontSize * scaleFactor),
        headlineMedium = defaultTypo.headlineMedium.copy(fontSize = defaultTypo.headlineMedium.fontSize * scaleFactor),
        headlineSmall = defaultTypo.headlineSmall.copy(fontSize = defaultTypo.headlineSmall.fontSize * scaleFactor),
        titleLarge = defaultTypo.titleLarge.copy(fontSize = defaultTypo.titleLarge.fontSize * scaleFactor),
        titleMedium = defaultTypo.titleMedium.copy(fontSize = defaultTypo.titleMedium.fontSize * scaleFactor),
        titleSmall = defaultTypo.titleSmall.copy(fontSize = defaultTypo.titleSmall.fontSize * scaleFactor),
        bodyLarge = defaultTypo.bodyLarge.copy(fontSize = defaultTypo.bodyLarge.fontSize * scaleFactor),
        bodyMedium = defaultTypo.bodyMedium.copy(fontSize = defaultTypo.bodyMedium.fontSize * scaleFactor),
        bodySmall = defaultTypo.bodySmall.copy(fontSize = defaultTypo.bodySmall.fontSize * scaleFactor),
        labelLarge = defaultTypo.labelLarge.copy(fontSize = defaultTypo.labelLarge.fontSize * scaleFactor),
        labelMedium = defaultTypo.labelMedium.copy(fontSize = defaultTypo.labelMedium.fontSize * scaleFactor),
        labelSmall = defaultTypo.labelSmall.copy(fontSize = defaultTypo.labelSmall.fontSize * scaleFactor)
    )
}


val LocalLauncherDimens = staticCompositionLocalOf { LauncherDimens() }

private val DarkColorScheme = darkColorScheme(
    primary = LauncherColors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = LauncherColors.AccentBlue.copy(alpha = 0.2f),
    onPrimaryContainer = LauncherColors.AccentBlue,
    secondary = LauncherColors.AccentPurple,
    onSecondary = Color.White,
    secondaryContainer = LauncherColors.AccentPurple.copy(alpha = 0.2f),
    onSecondaryContainer = LauncherColors.AccentPurple,
    tertiary = LauncherColors.AccentTeal,
    onTertiary = Color.White,
    background = LauncherColors.DarkBackground,
    onBackground = LauncherColors.TextPrimary,
    surface = LauncherColors.DarkSurface,
    onSurface = LauncherColors.TextPrimary,
    surfaceVariant = LauncherColors.DarkSurfaceVariant,
    onSurfaceVariant = LauncherColors.TextSecondary,
    error = LauncherColors.Error,
    onError = Color.White
)

@Composable
fun LauncherTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLauncherDimens provides LauncherDimens()
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = defaultTypography(),
            content = content
        )
    }
}