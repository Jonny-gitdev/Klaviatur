package de.klaviatur.ui.screens.repertoire

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klaviatur.R
import de.klaviatur.data.model.*
import kotlin.math.hypot

/**
 * PdfPageCard mit Zoom-Unterstützung (vor letzter Änderung).
 */
@Composable
internal fun PdfPageCard(
    page: SheetPage,
    pageAnnotations: List<PageAnnotation>,
    selectedTool: DrawingTool,
    currentColor: Color,
    strokeWidth: Float,
    zoom: Float,
    pageWidthPx: Float,
    editAction: EditAction,
    pageMeasures: List<MeasureBox> = emptyList(),
    selectedMeasures: Set<Int> = emptySet(),
    sections: List<PieceSection> = emptyList(),
    showSectionsOverlay: Boolean = true,
    onToggleMeasureSelection: (Int) -> Unit = {},
    onSelectMeasure: (Int) -> Unit = {},
    onUpdateAnnotations: (List<PageAnnotation>) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sheetFileId = page.sheetFileId
    val pageIndex = page.pageIndex
    val bitmap = page.bitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val sectionBadgePaint = remember { android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL; isAntiAlias = true } }
    val sectionTextPaint = remember { android.graphics.Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true; isFakeBoldText = true } }
    val numTextPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; isFakeBoldText = true } }
    val numBgPaint = remember { android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL } }

    val currentAnnotation = pageAnnotations.find { it.sheetFileId == sheetFileId && it.pageIndex == pageIndex }
    val strokes = currentAnnotation?.strokes ?: emptyList()

    val strokesState = rememberUpdatedState(strokes)
    val pageAnnotationsState = rememberUpdatedState(pageAnnotations)
    val selectedToolState = rememberUpdatedState(selectedTool)
    val currentColorState = rememberUpdatedState(currentColor)
    val strokeWidthState = rememberUpdatedState(strokeWidth)
    val onUpdateAnnotationsState = rememberUpdatedState(onUpdateAnnotations)
    val zoomState = rememberUpdatedState(zoom)
    val editActionState = rememberUpdatedState(editAction)

    var currentStrokePoints by remember { mutableStateOf(emptyList<DrawingPoint>()) }

    // Edit-Modus
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var dragStrokes by remember { mutableStateOf<List<AnnotationStroke>?>(null) }
    val editHistory: SnapshotStateList<List<AnnotationStroke>> = remember { mutableStateListOf() }

    LaunchedEffect(strokes) { dragStrokes = null }
    LaunchedEffect(selectedTool) { if (selectedTool != DrawingTool.EDIT) selectedIndex = null }

    val displayStrokes = dragStrokes ?: strokes

    fun commit(updated: List<AnnotationStroke>) {
        val newAnn = PageAnnotation(sheetFileId = sheetFileId, pageIndex = pageIndex, strokes = updated)
        val others = pageAnnotationsState.value.filterNot {
            it.sheetFileId == sheetFileId && it.pageIndex == pageIndex
        }
        onUpdateAnnotationsState.value(others + newAnn)
    }

    fun commitEdit(updated: List<AnnotationStroke>) {
        editHistory.add(strokesState.value)
        commit(updated)
    }

    Card(
        modifier = Modifier.width(with(density) { (pageWidthPx * zoom).toDp() }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
            )

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    // ── Takte auswählen im Scroll-Modus ──
                    .pointerInput(sheetFileId, pageIndex, selectedTool, pageMeasures, zoom) {
                        if (selectedTool != DrawingTool.SCROLL || pageMeasures.isEmpty()) return@pointerInput
                        detectTapGestures { offset ->
                            val z = zoomState.value
                            val x = offset.x / z
                            val y = offset.y / z
                            val hit = pageMeasures.find { m -> m.bounds.contains(x, y) }
                            if (hit != null) {
                                onToggleMeasureSelection(hit.measureIndex)
                            }
                        }
                    }
                    .pointerInput(sheetFileId, pageIndex, selectedTool, pageMeasures, zoom) {
                        if (selectedTool != DrawingTool.SCROLL || pageMeasures.isEmpty()) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val z = zoomState.value
                                val x = offset.x / z
                                val y = offset.y / z
                                val hit = pageMeasures.find { m -> m.bounds.contains(x, y) }
                                if (hit != null) {
                                    onSelectMeasure(hit.measureIndex)
                                }
                            },
                            onDrag = { change, _ ->
                                val z = zoomState.value
                                val x = change.position.x / z
                                val y = change.position.y / z
                                val hit = pageMeasures.find { m -> m.bounds.contains(x, y) }
                                if (hit != null) {
                                    onSelectMeasure(hit.measureIndex)
                                }
                            }
                        )
                    }
                    // ── Stift / Textmarker / Radierer ──
                    .pointerInput(sheetFileId, pageIndex, selectedTool, zoom) {
                        if (selectedTool == DrawingTool.SCROLL || selectedTool == DrawingTool.EDIT) {
                            return@pointerInput
                        }
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStrokePoints = listOf(DrawingPoint(offset.x / zoom, offset.y / zoom))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStrokePoints = currentStrokePoints +
                                        DrawingPoint(change.position.x / zoom, change.position.y / zoom)
                            },
                            onDragEnd = {
                                if (currentStrokePoints.isNotEmpty()) {
                                    val tool = selectedToolState.value
                                    val color = currentColorState.value
                                    val width = strokeWidthState.value
                                    val currentStrokes = strokesState.value

                                    val newStroke = AnnotationStroke(
                                        points = currentStrokePoints,
                                        color = color.toArgb().toLong(),
                                        strokeWidth = width,
                                        isEraser = tool == DrawingTool.ERASER
                                    )

                                    val updatedStrokes = if (tool == DrawingTool.ERASER) {
                                        currentStrokes.filter { stroke ->
                                            if (stroke.lineKind() != null) true
                                            else !stroke.points.any { p ->
                                                currentStrokePoints.any { ep ->
                                                    hypot(p.x - ep.x, p.y - ep.y) < 30f
                                                }
                                            }
                                        }
                                    } else {
                                        currentStrokes + newStroke
                                    }
                                    commit(updatedStrokes)
                                    currentStrokePoints = emptyList()
                                }
                            },
                            onDragCancel = { currentStrokePoints = emptyList() }
                        )
                    }
                    // ── Edit-Modus: Antippen ──
                    .pointerInput(sheetFileId, pageIndex, selectedTool, zoom) {
                        if (selectedTool != DrawingTool.EDIT) return@pointerInput
                        detectTapGestures { offset ->
                            val z = zoomState.value
                            val x = offset.x / z
                            val y = offset.y / z
                            val pageW = size.width / z
                            val pageH = size.height / z
                            val current = strokesState.value
                            when (editActionState.value) {
                                EditAction.ADD_SYSTEM -> {
                                    commitEdit(addSystem(current, y, pageW, pageH))
                                    selectedIndex = current.size
                                }
                                EditAction.ADD_BARLINE -> {
                                    val updated = addBarline(current, x, y, pageW)
                                    if (updated == null) {
                                        Toast.makeText(context, "Bitte innerhalb eines Systems tippen.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        commitEdit(updated)
                                        selectedIndex = current.size
                                    }
                                }
                                EditAction.DELETE -> {
                                    val hit = hitTestLine(current, x, y, 36f / z)
                                    if (hit != null) {
                                        val kind = current[hit].lineKind()
                                        if (kind == LineKind.PAGE_EDGE) {
                                            Toast.makeText(context, "Seitenränder können nicht einzeln gelöscht werden.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val updated = deleteLine(current, hit)
                                            commitEdit(updated)
                                            selectedIndex = null
                                            val msg = if (kind == LineKind.BARLINE) "Taktstrich gelöscht." else "Notensystem gelöscht."
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                EditAction.NONE -> {
                                    selectedIndex = hitTestLine(current, x, y, 36f / z)
                                }
                            }
                        }
                    }
                    // ── Edit-Modus: Linie verschieben ──
                    .pointerInput(sheetFileId, pageIndex, selectedTool, zoom) {
                        if (selectedTool != DrawingTool.EDIT) return@pointerInput
                        var startStrokes: List<AnnotationStroke> = emptyList()
                        var index = -1
                        var total = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val z = zoomState.value
                                val current = strokesState.value
                                val hit = hitTestLine(current, offset.x / z, offset.y / z, 44f / z)
                                if (hit != null) {
                                    index = hit
                                    startStrokes = current
                                    total = Offset.Zero
                                    selectedIndex = hit
                                    dragStrokes = current
                                } else {
                                    index = -1
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (index >= 0) {
                                    change.consume()
                                    val z = zoomState.value
                                    total += dragAmount / z
                                    dragStrokes = moveLine(
                                        startStrokes, index, total.x, total.y,
                                        size.width / z, size.height / z
                                    )
                                }
                            },
                            onDragEnd = {
                                if (index >= 0) {
                                    dragStrokes?.let { finalStrokes ->
                                        if (finalStrokes != startStrokes) {
                                            editHistory.add(startStrokes)
                                            commit(finalStrokes)
                                        }
                                    }
                                }
                                index = -1
                            },
                            onDragCancel = {
                                index = -1
                                dragStrokes = null
                            }
                        )
                    }
            ) {
                scale(zoom, pivot = Offset.Zero) {
                    displayStrokes.forEach { stroke ->
                        val isLine = stroke.lineKind() != null
                        val shouldDraw = if (selectedTool == DrawingTool.EDIT) isLine else !isLine
                        if (shouldDraw && stroke.points.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke.points.first().x, stroke.points.first().y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            val w = if (isLine) stroke.strokeWidth / zoom else stroke.strokeWidth
                            drawPath(
                                path = path,
                                color = Color(stroke.color.toInt()),
                                style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    if (selectedTool != DrawingTool.EDIT && currentStrokePoints.size > 1) {
                        val activePath = Path().apply {
                            moveTo(currentStrokePoints.first().x, currentStrokePoints.first().y)
                            for (i in 1 until currentStrokePoints.size) {
                                lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                            }
                        }
                        drawPath(
                            path = activePath,
                            color = currentColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (selectedTool != DrawingTool.EDIT) {
                        pageMeasures.forEach { mBox ->
                            val isSelected = mBox.measureIndex in selectedMeasures
                            val bounds = mBox.bounds
                            val bLeft = bounds.left
                            val bTop = bounds.top
                            val bWidth = bounds.width()
                            val bHeight = bounds.height()

                            val sec = if (showSectionsOverlay) sections.find { mBox.measureIndex in it.startBar..it.endBar } else null

                            if (isSelected) {
                                drawRect(
                                    color = Color(0x4026A69A),
                                    topLeft = Offset(bLeft, bTop),
                                    size = androidx.compose.ui.geometry.Size(bWidth, bHeight)
                                )
                                drawRect(
                                    color = Color(0xFF26A69A),
                                    topLeft = Offset(bLeft, bTop),
                                    size = androidx.compose.ui.geometry.Size(bWidth, bHeight),
                                    style = Stroke(width = 2.5f / zoom)
                                )
                            } else if (sec != null) {
                                val secColor = Color(sec.level.color)
                                drawRect(
                                    color = secColor.copy(alpha = 0.35f),
                                    topLeft = Offset(bLeft, bTop),
                                    size = androidx.compose.ui.geometry.Size(bWidth, bHeight)
                                )
                                drawRect(
                                    color = secColor,
                                    topLeft = Offset(bLeft, bTop),
                                    size = androidx.compose.ui.geometry.Size(bWidth, bHeight),
                                    style = Stroke(width = 2f / zoom)
                                )

                                val pageSecMeasures = pageMeasures.filter { it.measureIndex in sec.startBar..sec.endBar }
                                val firstBarOnPage = pageSecMeasures.minOfOrNull { it.measureIndex }
                                if (mBox.measureIndex == firstBarOnPage) {
                                    val secName = if (sec.name.isNotBlank()) sec.name else "Takt ${sec.startBar}–${sec.endBar}"
                                    val badgePaint = sectionBadgePaint.apply { color = sec.level.color.toInt() }
                                    val badgeTextPaint = sectionTextPaint.apply { textSize = (11f / zoom).coerceIn(8f, 18f) }
                                    val textW = badgeTextPaint.measureText(secName)
                                    val bX = bLeft + 2f / zoom
                                    val bY = bTop + 2f / zoom
                                    val rectF = android.graphics.RectF(bX, bY, bX + textW + 8f / zoom, bY + (badgeTextPaint.textSize * 1.3f))
                                    drawContext.canvas.nativeCanvas.drawRoundRect(rectF, 4f / zoom, 4f / zoom, badgePaint)
                                    drawContext.canvas.nativeCanvas.drawText(secName, bX + 4f / zoom, bY + badgeTextPaint.textSize * 1.0f, badgeTextPaint)
                                }
                            } else if (selectedTool == DrawingTool.SCROLL || selectedMeasures.isNotEmpty()) {
                                drawRect(
                                    color = Color(0x1826A69A),
                                    topLeft = Offset(bLeft, bTop),
                                    size = androidx.compose.ui.geometry.Size(bWidth, bHeight),
                                    style = Stroke(width = 1f / zoom)
                                )
                            }

                            // Taktnummern-Badge
                            val textPaint = numTextPaint.apply {
                                color = if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
                                textSize = (11f / zoom).coerceIn(8f, 20f)
                            }
                            val bgPaint = numBgPaint.apply {
                                color = if (isSelected) android.graphics.Color.rgb(38, 166, 154) else android.graphics.Color.argb(180, 240, 240, 240)
                            }
                            val label = "${mBox.measureIndex}"
                            val textWidth = textPaint.measureText(label)
                            val badgeX = bLeft + 3f / zoom
                            val badgeY = bTop + 3f / zoom
                            val badgeH = (textPaint.textSize * 1.2f)
                            val badgeW = textWidth + 6f / zoom
                            val badgeRect = android.graphics.RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)

                            drawContext.canvas.nativeCanvas.drawRoundRect(badgeRect, 3f / zoom, 3f / zoom, bgPaint)
                            drawContext.canvas.nativeCanvas.drawText(label, badgeX + 3f / zoom, badgeY + badgeH - 2f / zoom, textPaint)
                        }
                    }

                    if (selectedTool == DrawingTool.EDIT) {
                        val sel = selectedIndex?.let { displayStrokes.getOrNull(it) }
                        if (sel != null && sel.points.size == 2) {
                            val a = Offset(sel.points[0].x, sel.points[0].y)
                            val b = Offset(sel.points[1].x, sel.points[1].y)
                            val col = Color(0xFFFF9800)
                            drawLine(col, a, b, strokeWidth = 4f / zoom, cap = StrokeCap.Round)
                            drawCircle(col, radius = 8f / zoom, center = a)
                            drawCircle(col, radius = 8f / zoom, center = b)
                        }
                    }
                }
            }
        }
    }
}
