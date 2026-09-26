package app.dizzify.ui.components

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dizzify.LauncherViewModel
import app.dizzify.data.AppModel
import app.dizzify.data.HomeItem
import app.dizzify.ui.theme.*
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val GRID_CELL_HEIGHT = 190.dp
private val GRID_GAP = 12.dp

@Composable
fun HomeGridSection(
    viewModel: LauncherViewModel,
    onNavigateToWidgetPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layout by viewModel.homeLayout.collectAsState()
    val appsAll by viewModel.appsAll.collectAsState()
    val widgetHost: LauncherWidgetHost = koinInject()

    var editMode by remember { mutableStateOf(false) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var moveOrigin by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var optionsWidget by remember { mutableStateOf<HomeItem.Widget?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var gridApp by remember { mutableStateOf<HomeItem.App?>(null) }

    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    fun requesterFor(id: String) = focusRequesters.getOrPut(id) { FocusRequester() }

    val liveByKey = remember(appsAll) { appsAll.associateBy { it.getKey() } }
    val movingItem = layout.items.filterIsInstance<HomeItem.Widget>().find { it.id == movingId }

    LaunchedEffect(movingId) {
        movingId?.let { requesterFor(it).requestFocus() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpacing.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Home",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = when {
                        movingItem != null -> "Arrows move • OK done • Back cancel"
                        editMode -> "Menu for options • arrows navigate"
                        layout.items.isNotEmpty() -> "${layout.items.size} items • Edit to rearrange"
                        else -> "Add widgets and apps to your home grid"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (movingItem != null) LauncherColors.AccentTeal
                    else LauncherColors.TextSecondary
                )
            }
            if (layout.items.isNotEmpty()) {
                Button(onClick = {
                    if (movingItem == null) editMode = !editMode
                }) {
                    Text(if (editMode) "Done" else "Edit")
                }
            }
        }

        Spacer(modifier = Modifier.height(LauncherSpacing.md))

        if (layout.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LauncherSpacing.screenPadding)
            ) {
                AddWidgetCard(onClick = onNavigateToWidgetPicker)
            }
        } else {
            val columns = layout.columns.coerceAtLeast(1)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LauncherSpacing.screenPadding)
            ) {
                val cellWidth = maxWidth / columns
                HomeGridLayout(
                    columns = columns,
                    cellWidth = cellWidth,
                    cellHeight = GRID_CELL_HEIGHT,
                    gap = GRID_GAP
                ) {
                    layout.items.forEach { item ->
                        key(item.id) {
                            Box(modifier = Modifier.layoutId(item)) {
                                when (item) {
                                    is HomeItem.Widget -> {
                                        val hostView = remember(item.appWidgetId) {
                                            widgetHost.createHostView(item.appWidgetId)
                                        }
                                        WidgetCard(
                                            widget = item,
                                            hostView = hostView,
                                            onRemove = { viewModel.removeWidget(item.appWidgetId) },
                                            onConfigure = { viewModel.requestWidgetReconfigure(item) },
                                            width = cellWidth * item.columnSpan - GRID_GAP,
                                            height = GRID_CELL_HEIGHT * item.rowSpan - GRID_GAP,
                                            isEditMode = editMode || movingId == item.id,
                                            focusRequester = requesterFor(item.id),
                                            onOptions = {
                                                optionsWidget = item
                                                showOptions = true
                                            },
                                            moveMode = movingId == item.id,
                                            onMove = { dx, dy ->
                                                viewModel.moveWidget(
                                                    item,
                                                    item.row + dy,
                                                    item.column + dx
                                                )
                                            },
                                            onMoveConfirm = {
                                                movingId = null
                                                moveOrigin = null
                                            },
                                            onMoveCancel = {
                                                val origin = moveOrigin
                                                if (origin != null) {
                                                    viewModel.moveWidget(
                                                        item,
                                                        origin.first,
                                                        origin.second
                                                    )
                                                }
                                                movingId = null
                                                moveOrigin = null
                                            }
                                        )
                                    }
                                    is HomeItem.App -> {
                                        val live: AppModel = liveByKey[item.id] ?: item.appModel
                                        Box(
                                            modifier = Modifier.size(
                                                cellWidth * item.columnSpan - GRID_GAP,
                                                GRID_CELL_HEIGHT * item.rowSpan - GRID_GAP
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppCard(
                                                app = live,
                                                onClick = { viewModel.launch(live) },
                                                onLongClick = { gridApp = item },
                                                style = CardStyle.STANDARD,
                                                focusRequester = requesterFor(item.id)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (editMode && movingId == null) {
                Spacer(modifier = Modifier.height(LauncherSpacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LauncherSpacing.screenPadding)
                ) {
                    AddWidgetCard(onClick = onNavigateToWidgetPicker)
                }
            }
        }
    }

    optionsWidget?.let { widget ->
        WidgetOptionsSheet(
            widget = widget,
            isVisible = showOptions,
            onDismiss = {
                showOptions = false
                optionsWidget = null
            },
            onConfigure = {
                viewModel.requestWidgetReconfigure(widget)
                showOptions = false
                optionsWidget = null
            },
            onRemove = {
                viewModel.removeWidget(widget.appWidgetId)
                showOptions = false
                optionsWidget = null
            },
            onResize = { rows, cols ->
                viewModel.resizeWidget(widget, rows, cols)
                showOptions = false
                optionsWidget = null
            },
            onMove = {
                movingId = widget.id
                moveOrigin = widget.row to widget.column
                editMode = true
                showOptions = false
                optionsWidget = null
            }
        )
    }

    val gridAppOptions = rememberAppOptionsState()
    val gridAppId = gridApp?.id
    LaunchedEffect(gridAppId) {
        val item = gridApp ?: return@LaunchedEffect
        gridAppOptions.open(liveByKey[item.id] ?: item.appModel)
        gridApp = null
    }

    AppOptionsHost(
        state = gridAppOptions,
        onOpen = { viewModel.launch(it) },
        onToggleHidden = { viewModel.toggleHidden(it) },
        isHidden = { it.isHidden },
        onToggleHome = { viewModel.toggleHomeApp(it) },
        isOnHome = { true }
    )
}

/** Absolute-position grid: children carry their HomeItem as layoutId. */
@Composable
private fun HomeGridLayout(
    columns: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val cellWPx = (constraints.maxWidth / columns.toFloat()).roundToInt()
        val cellHPx = cellHeight.roundToPx()
        val gapPx = gap.roundToPx()

        data class Placed(
            val placeable: androidx.compose.ui.layout.Placeable,
            val item: HomeItem?
        )

        val placed = measurables.map { measurable ->
            val item = measurable.layoutId as? HomeItem
            val w = (cellWPx * (item?.columnSpan ?: 1) - gapPx)
                .coerceIn(0, constraints.maxWidth)
            val h = (cellHPx * (item?.rowSpan ?: 1) - gapPx).coerceAtLeast(0)
            Placed(measurable.measure(Constraints.fixed(w, h)), item)
        }

        val rows = placed.mapNotNull { it.item }
            .maxOfOrNull { it.row + it.rowSpan }?.coerceAtLeast(1) ?: 1

        layout(constraints.maxWidth, rows * cellHPx) {
            placed.forEach { (placeable, item) ->
                val col = (item?.column ?: 0).coerceAtLeast(0)
                val row = (item?.row ?: 0).coerceAtLeast(0)
                placeable.placeRelative(
                    (col * cellWPx + gapPx / 2).coerceAtLeast(0),
                    row * cellHPx + gapPx / 2
                )
            }
        }
    }
}
