package app.dizzify.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.dizzify.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Generic horizontal content row: title + focus-aware scrolling container.
 *
 * Shared shell for every home-screen row that is NOT an [AppModel] grid
 * (TV inputs, Watch-Next, …). App rows keep using [AppRow]; new rows should
 * use this so title styling, focus behavior and scroll restoration stay
 * consistent in one place.
 *
 * The card owns its focus node (same pattern as [AppCard]): it must attach
 * [focusRequester], report focus via [onFocused], and activate [onClick] on
 * D-pad-center/Enter.
 *
 * @param items row items (stable order).
 * @param keyOf stable key per item (for [key]).
 * @param cardWidth approximate card width incl. spacing — used to scroll a
 * focused card into view.
 */
@Composable
fun <T> HomeContentRow(
    title: String,
    items: List<T>,
    keyOf: (T) -> String,
    onItemClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    countSuffix: String? = null,
    accentColor: Color = LauncherColors.AccentBlue,
    cardWidth: Dp = LauncherCardSizes.bannerCardWidth,
    itemCard: @Composable (
        item: T,
        focused: Boolean,
        focusRequester: FocusRequester,
        onFocused: (Boolean) -> Unit,
        onClick: () -> Unit,
    ) -> Unit,
) {
    if (items.isEmpty()) return

    var isRowFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRestorer = rememberFocusRestorer()
    val density = LocalDensity.current

    val cardWidthPx = with(density) { (cardWidth + LauncherSpacing.cardGap).toPx() }

    val titleAlpha by animateFloatAsState(
        targetValue = if (isRowFocused) 1f else 0.7f,
        animationSpec = tween(LauncherAnimation.NormalDuration),
        label = "row_title_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isRowFocused = it.hasFocus },
    ) {
        Row(
            modifier = Modifier
                .padding(start = LauncherSpacing.screenPadding, bottom = LauncherSpacing.md)
                .graphicsLayer { alpha = titleAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isRowFocused) Color.White else LauncherColors.TextSecondary,
            )
            if (isRowFocused && countSuffix != null) {
                Text(
                    text = "  •  $countSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherColors.TextTertiary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .focusGroup()
                .padding(
                    start = LauncherSpacing.screenPadding,
                    end = LauncherSpacing.screenPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap),
        ) {
            items.forEachIndexed { index, item ->
                key(keyOf(item)) {
                    var focused by remember { mutableStateOf(false) }
                    itemCard(
                        item,
                        focused,
                        focusRestorer.getFocusRequester(keyOf(item)),
                        { isFocused ->
                            focused = isFocused
                            if (isFocused) {
                                focusRestorer.saveFocus(keyOf(item))
                                coroutineScope.launch {
                                    val target = (index * cardWidthPx).toInt()
                                        .coerceAtLeast(0)
                                    scrollState.animateScrollTo(
                                        target,
                                        animationSpec = tween(LauncherAnimation.NormalDuration),
                                    )
                                }
                            }
                        },
                    ) { onItemClick(item) }
                }
            }
        }
    }
}
