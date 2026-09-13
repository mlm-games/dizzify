package app.dizzify.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dizzify.platform.tv.TvInputEntry
import app.dizzify.platform.tv.WatchNextItem
import app.dizzify.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TvCardShape = RoundedCornerShape(16.dp)

@Composable
private fun tvCardScale(focused: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tv_card_scale",
    )
    return scale
}

@Composable
private fun TvCardContainer(
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = LauncherCardSizes.bannerCardWidth,
    height: androidx.compose.ui.unit.Dp = LauncherCardSizes.bannerCardHeight,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale = tvCardScale(focused)
    Box(
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(TvCardShape)
            .background(
                if (focused) LauncherColors.DarkCardBackground
                else LauncherColors.DarkSurface,
            )
            .then(
                if (focused) Modifier.border(
                    width = 2.dp,
                    color = Color.White,
                    shape = TvCardShape,
                ) else Modifier,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { onFocused(it.isFocused) }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            },
        content = content,
    )
}

@Composable
fun TvInputsRow(
    inputs: List<TvInputEntry>,
    onInputClick: (TvInputEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeContentRow(
        title = "TV Inputs",
        items = inputs,
        keyOf = { it.inputId },
        onItemClick = onInputClick,
        modifier = modifier,
        countSuffix = "${inputs.size} inputs",
        accentColor = LauncherColors.AccentTeal,
        cardWidth = LauncherCardSizes.bannerCardWidth,
    ) { item, focused, focusRequester, onFocused, onClick ->
        TvCardContainer(
            focused = focused,
            focusRequester = focusRequester,
            onFocused = onFocused,
            onClick = onClick,
            height = 110.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LauncherSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (focused) Color.White else LauncherColors.AccentTeal,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(LauncherSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.typeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = LauncherColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun WatchNextRow(
    items: List<WatchNextItem>,
    onItemClick: (WatchNextItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeContentRow(
        title = "Continue Watching",
        items = items,
        keyOf = { it.id },
        onItemClick = onItemClick,
        modifier = modifier,
        countSuffix = "${items.size} items",
        accentColor = LauncherColors.AccentOrange,
        cardWidth = LauncherCardSizes.bannerCardWidth,
    ) { item, focused, focusRequester, onFocused, onClick ->
        TvCardContainer(
            focused = focused,
            focusRequester = focusRequester,
            onFocused = onFocused,
            onClick = onClick,
        ) {
            val poster = rememberWatchNextPoster(item)
            if (poster != null) {
                Image(
                    bitmap = poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    LauncherColors.DarkCardBackground,
                                    LauncherColors.DarkSurface,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = LauncherColors.TextTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    )
                    .padding(LauncherSpacing.md),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = LauncherColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.progress?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth(),
                        color = LauncherColors.AccentOrange,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberWatchNextPoster(item: WatchNextItem): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id, item.posterUri) {
        val uri = item.posterUri ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 480) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}
