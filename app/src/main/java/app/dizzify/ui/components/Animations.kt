package app.dizzify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.dizzify.ui.theme.LauncherAnimation

@Composable
fun StaggeredAnimatedVisibility(
    visible: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val delay = (index * LauncherAnimation.StaggerDelayMs).toInt()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = LauncherAnimation.NormalDuration,
                delayMillis = delay
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = LauncherAnimation.NormalDuration,
                delayMillis = delay
            ),
            initialOffsetY = { it / 4 }
        ),
        exit = fadeOut(animationSpec = tween(LauncherAnimation.FastDuration)),
        content = content
    )
}