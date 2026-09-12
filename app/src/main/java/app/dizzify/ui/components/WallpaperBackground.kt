package app.dizzify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import app.dizzify.helper.WallpaperHelper
import app.dizzify.ui.theme.LauncherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home-screen background layer driven by [WallpaperHelper.HomeBackground].
 *
 * - [WallpaperHelper.HomeBackground.Image] → cover image (decoded off-main).
 * - [WallpaperHelper.HomeBackground.Color] → plain color (system set failed).
 * - [WallpaperHelper.HomeBackground.Default] → default gradient wash.
 */
@Composable
fun HomeWallpaperLayer(
    background: WallpaperHelper.HomeBackground,
    modifier: Modifier = Modifier,
) {
    when (background) {
        is WallpaperHelper.HomeBackground.Image -> {
            var bitmap by remember(background.file.absolutePath) {
                mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
            }
            LaunchedEffect(background.file.absolutePath) {
                bitmap = withContext(Dispatchers.IO) {
                    WallpaperHelper.decodeFile(background.file)
                }
            }
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = null,
                    modifier = modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                DefaultHomeWash(modifier)
            }
        }

        is WallpaperHelper.HomeBackground.Color ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(background.argb)),
            )

        WallpaperHelper.HomeBackground.Default -> DefaultHomeWash(modifier)
    }
}

@Composable
fun DefaultHomeWash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LauncherColors.AccentBlue.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
