package app.dizzify.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppModel
import app.dizzify.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppRow(
    title: String,
    apps: List<AppModel>,
    onAppClick: (AppModel) -> Unit,
    onAppLongClick: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
    cardStyle: CardStyle = CardStyle.STANDARD,
    showIndex: Int = 0,
    accentColor: Color = LauncherColors.AccentBlue,
    rowFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isRowFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusRestorer = rememberFocusRestorer()
    val density = LocalDensity.current
    
    // Calculate card width for scroll offset
    val cardWidth = with(density) {
        when (cardStyle) {
            CardStyle.STANDARD -> LauncherCardSizes.appCardWidth.toPx()
            CardStyle.COMPACT -> 120.dp.toPx()
            CardStyle.BANNER -> LauncherCardSizes.bannerCardWidth.toPx()
            CardStyle.MINIMAL -> (LauncherCardSizes.smallCardSize + 20.dp).toPx()
        } + LauncherSpacing.cardGap.toPx()
    }
    
    val titleAlpha by animateFloatAsState(
        targetValue = if (isRowFocused) 1f else 0.7f,
        animationSpec = tween(LauncherAnimation.NormalDuration),
        label = "title_alpha"
    )
    
    val titleScale by animateFloatAsState(
        targetValue = if (isRowFocused) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "title_scale"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isRowFocused = it.hasFocus }
    ) {
        Row(
            modifier = Modifier
                .padding(start = LauncherSpacing.screenPadding, bottom = LauncherSpacing.md)
                .graphicsLayer {
                    alpha = titleAlpha
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar
            AnimatedVisibility(
                visible = isRowFocused,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .padding(end = LauncherSpacing.sm)
                        .graphicsLayer {
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                            clip = true
                        }
                        .animateContentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = accentColor,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(if (isRowFocused) 8.dp else 0.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isRowFocused) Color.White else LauncherColors.TextSecondary
            )
            
            AnimatedVisibility(
                visible = isRowFocused && apps.size > 5,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it }
            ) {
                Text(
                    text = "  •  ${apps.size} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherColors.TextTertiary
                )
            }
        }
        
        // Apps row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .focusGroup()
                .padding(
                    start = LauncherSpacing.screenPadding,
                    end = LauncherSpacing.screenPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap)
        ) {
            apps.forEachIndexed { index, app ->
                val itemFocusRequester = focusRestorer.getFocusRequester(index)
                
                key(app.getKey()) {
                    StaggeredAnimatedVisibility(
                        visible = true,
                        index = index
                    ) {
                        AppCard(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) },
                            style = cardStyle,
                            focusRequester = itemFocusRequester,
                            modifier = Modifier.onFocusChanged { state ->
                                if (state.isFocused) {
                                    focusRestorer.saveFocus(index)
                                    // Smooth scroll to focused item
                                    coroutineScope.launch {
                                        val targetScroll = (index * cardWidth - cardWidth).toInt()
                                            .coerceAtLeast(0)
                                        scrollState.animateScrollTo(
                                            targetScroll,
                                            animationSpec = tween(LauncherAnimation.NormalDuration)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppGrid(
    apps: List<AppModel>,
    onAppClick: (AppModel) -> Unit,
    onAppLongClick: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
    cardStyle: CardStyle = CardStyle.STANDARD
) {
    val rows = apps.chunked(columns)
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap)
    ) {
        rows.forEachIndexed { rowIndex, rowApps ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap)
            ) {
                rowApps.forEachIndexed { colIndex, app ->
                    val globalIndex = rowIndex * columns + colIndex
                    
                    StaggeredAnimatedVisibility(
                        visible = true,
                        index = globalIndex
                    ) {
                        AppCard(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) },
                            style = cardStyle
                        )
                    }
                }
                
                // Fill remaining space if row is not complete
                repeat(columns - rowApps.size) {
                    Spacer(
                        modifier = Modifier.size(
                            when (cardStyle) {
                                CardStyle.STANDARD -> LauncherCardSizes.appCardWidth
                                CardStyle.COMPACT -> 120.dp
                                else -> LauncherCardSizes.appCardWidth
                            }
                        )
                    )
                }
            }
        }
    }
}