package de.klaviatur.ui.screens.repertoire

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.stringResource
import de.klaviatur.R
import de.klaviatur.data.model.PieceSection
import de.klaviatur.ui.theme.ForestGreen
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Anteil der Taktkasten-Höhe, der oben/unten zusätzlich mitgezeigt wird (Hilfslinien, Noten außerhalb des Systems). */
private const val VERTICAL_PADDING_FRACTION = 0.28f

/**
 * Handy-Modus: Querformat, alle erkannten Takte in einer einzigen Zeile,
 * vergrößert und endlos nach rechts scrollbar.
 *
 * @param referenceWidthPx Breite (in px), auf die sich die gespeicherten Koordinaten der
 *   Takterkennung beziehen.
 */
@Composable
internal fun PhoneModeScreen(
    sheetPages: List<SheetPage>,
    measures: List<MeasureBox>,
    sections: List<PieceSection>,
    showSections: Boolean,
    referenceWidthPx: Float,
    barsPerScreen: Int,
    onBarsPerScreenChange: (Int) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    BackHandler(onBack = onExit)

    // Querformat erzwingen + Systemleisten ausblenden; beim Verlassen zurücksetzen.
    DisposableEffect(activity) {
        val act = activity
        if (act == null) {
            onDispose { }
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val controller = WindowCompat.getInsetsController(act.window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                if (!act.isChangingConfigurations) {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }

    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(2f) }
    var isUserTouching by remember { mutableStateOf(false) }

    // Steuerleiste blendet sich nach ein paar Sekunden aus; Antippen blendet sie wieder ein.
    LaunchedEffect(controlsVisible, barsPerScreen) {
        if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(isAutoScrolling, scrollSpeed, isUserTouching) {
        if (isAutoScrolling) {
            while (isAutoScrolling) {
                if (!isUserTouching) {
                    val consumed = listState.scrollBy(scrollSpeed)
                    if (consumed == 0f && !listState.canScrollForward) {
                        isAutoScrolling = false
                        break
                    }
                }
                delay(16L)
            }
        }
    }

    val pagesByKey = remember(sheetPages) { sheetPages.associateBy { it.sheetFileId to it.pageIndex } }
    val imagesByKey = remember(sheetPages) {
        sheetPages.associate { (it.sheetFileId to it.pageIndex) to it.bitmap.asImageBitmap() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        isUserTouching = event.changes.any { it.pressed }
                    }
                }
            }
            .background(Color(0xFFFAFAF7))
    ) {
        if (measures.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.phone_mode_no_bars),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val availW = constraints.maxWidth.toFloat()
                val availH = constraints.maxHeight.toFloat()

                // Einheitlicher Maßstab (px pro Referenz-Pixel) für alle Takte:
                // Der mittlere Takt füllt 1/n der Bildschirmbreite, begrenzt so, dass der höchste Takt passt.
                val sortedWidths = remember(measures) { measures.map { it.bounds.width() }.sorted() }
                val medianW = sortedWidths[sortedWidths.size / 2].coerceAtLeast(1f)
                val maxH = remember(measures) {
                    measures.maxOf { it.bounds.height() * (1f + 2f * VERTICAL_PADDING_FRACTION) }
                }.coerceAtLeast(1f)
                val horizontalPaddingPx = with(density) { 16.dp.toPx() }
                val scaleByWidth = (availW - 2f * horizontalPaddingPx) / (barsPerScreen * medianW)
                val scaleByHeight = (availH * 0.96f) / maxH
                val scale = minOf(scaleByWidth, scaleByHeight)

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { controlsVisible = !controlsVisible }
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(measures, key = { _, m -> m.measureIndex }) { index, m ->
                        val key = m.sheetFileId to m.pageIndex
                        val page = pagesByKey[key]
                        val image = imagesByKey[key]
                        if (page != null && image != null) {
                            val startsNewSystem = index > 0 && measures[index - 1].let {
                                it.sheetFileId != m.sheetFileId ||
                                        it.pageIndex != m.pageIndex ||
                                        it.systemIndex != m.systemIndex
                            }
                            val section = if (showSections) {
                                sections.find { m.measureIndex in it.startBar..it.endBar }
                            } else null

                            val boxH = m.bounds.height()
                            val padY = boxH * VERTICAL_PADDING_FRACTION
                            val widthDp = with(density) { (m.bounds.width() * scale).toDp() }

                            // Quellbereich im Bitmap
                            val srcScale = page.bitmap.width / referenceWidthPx
                            val sl = (m.bounds.left * srcScale).roundToInt().coerceIn(0, page.bitmap.width - 1)
                            val sr = (m.bounds.right * srcScale).roundToInt().coerceIn(sl + 1, page.bitmap.width)
                            val st = ((m.bounds.top - padY) * srcScale).roundToInt().coerceIn(0, page.bitmap.height - 1)
                            val sb = ((m.bounds.bottom + padY) * srcScale).roundToInt().coerceIn(st + 1, page.bitmap.height)

                            val actualSrcHRef = (sb - st).toFloat() / srcScale
                            val heightDp = with(density) { (actualSrcHRef * scale).toDp() }

                            Box(
                                Modifier
                                    .padding(start = if (startsNewSystem) 14.dp else 0.dp)
                                    .width(widthDp)
                                    .height(heightDp)
                            ) {
                                Canvas(Modifier.width(widthDp).height(heightDp)) {
                                    drawImage(
                                        image = image,
                                        srcOffset = IntOffset(sl, st),
                                        srcSize = IntSize(sr - sl, sb - st),
                                        dstOffset = IntOffset.Zero,
                                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                        filterQuality = FilterQuality.High
                                    )
                                }
                                if (section != null) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .height(3.dp)
                                            .width(widthDp)
                                            .background(Color(section.level.color))
                                    )
                                }
                                Text(
                                    text = "${m.measureIndex}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF777777),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 2.dp, top = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Aktuell sichtbarer Taktbereich
        val visibleRange by remember(measures) {
            derivedStateOf {
                val infos = listState.layoutInfo.visibleItemsInfo
                if (infos.isEmpty() || measures.isEmpty()) null
                else {
                    val first = measures.getOrNull(infos.first().index)?.measureIndex
                    val last = measures.getOrNull(infos.last().index)?.measureIndex
                    if (first != null && last != null) first to last else null
                }
            }
        }

        if (controlsVisible || measures.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xE6FFFFFF),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.exit_phone_mode))
                    }
                    if (measures.isNotEmpty()) {
                        val range = visibleRange
                        Text(
                            text = if (range != null) "${stringResource(R.string.bar_range_format, range.first, range.second)} / ${measures.size}" else stringResource(R.string.bars_total_count, measures.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.DarkGray
                        )
                        IconButton(
                            onClick = { onBarsPerScreenChange((barsPerScreen - 1).coerceAtLeast(1)) },
                            enabled = barsPerScreen > 1
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.fewer_bars))
                        }
                        Text(
                            text = "$barsPerScreen",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForestGreen
                        )
                        IconButton(
                            onClick = { onBarsPerScreenChange((barsPerScreen + 1).coerceAtMost(8)) },
                            enabled = barsPerScreen < 8
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.more_bars))
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(20.dp)
                                .background(Color(0xFFCCCCCC))
                        )
                        IconButton(onClick = { isAutoScrolling = !isAutoScrolling }) {
                            Icon(
                                imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(if (isAutoScrolling) R.string.pause_autoscroll else R.string.start_autoscroll),
                                tint = if (isAutoScrolling) ForestGreen else Color.DarkGray
                            )
                        }
                        if (isAutoScrolling) {
                            IconButton(
                                onClick = { scrollSpeed = (scrollSpeed - 0.5f).coerceAtLeast(0.5f) },
                                enabled = scrollSpeed > 0.5f
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.slower))
                            }
                            Text(
                                text = String.format(Locale.getDefault(), "%.1fx", scrollSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                color = ForestGreen
                            )
                            IconButton(
                                onClick = { scrollSpeed = (scrollSpeed + 0.5f).coerceAtMost(10.0f) },
                                enabled = scrollSpeed < 10.0f
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.faster))
                            }
                        }
                    }
                }
            }
        }
    }
}
