package de.klaviatur.ui.screens.repertoire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.R
import de.klaviatur.data.model.*
import de.klaviatur.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min



/**
 * Erkennt fünfzeilige Notensysteme anhand der horizontalen Pixelprojektion.
 * Die Koordinaten werden auf die tatsächliche Anzeigebreite skaliert, weil
 * AnnotationStroke im bestehenden Viewer Bildschirm-Pixelkoordinaten nutzt.
 *
 * Dies ist eine heuristische Erkennung für gedruckte Notenblätter; bei stark
 * schrägen oder gekrümmten Fotos kann eine manuelle Korrektur nötig sein.
 */
private fun detectStaffBoundaryStrokes(
    bitmap: Bitmap,
    displayWidthPx: Float
): List<AnnotationStroke> {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 100 || height < 100) return emptyList()

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    fun isDark(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return (r * 299 + g * 587 + b * 114) / 1000 < 180
    }

    /*
     * Staff lines are long horizontal runs. Counting all dark pixels in a row
     * is too sensitive to note heads, stems and text; the longest horizontal
     * run gives a more useful signal. Allow tiny gaps caused by paper texture
     * or compression, but do not bridge large gaps.
     */
    val rowScores = IntArray(height)
    val gapAllowance = 2
    for (y in 0 until height) {
        val offset = y * width
        var runStart = -1
        var lastDarkX = -1
        var runLength = 0
        var longest = 0

        for (x in 0 until width) {
            if (isDark(pixels[offset + x])) {
                if (runStart < 0) {
                    runStart = x
                    runLength = 1
                } else {
                    val gap = x - lastDarkX - 1
                    if (gap <= gapAllowance) {
                        runLength += gap + 1
                    } else {
                        longest = max(longest, runLength)
                        runStart = x
                        runLength = 1
                    }
                }
                lastDarkX = x
            }
        }
        longest = max(longest, runLength)
        rowScores[y] = longest
    }

    // Relative threshold handles pages with different margins and image sizes.
    val lineThreshold = (width * 0.20f).toInt().coerceAtLeast(35)
    val activeRows = BooleanArray(height) { rowScores[it] >= lineThreshold }

    // Merge adjacent active pixels into one candidate line, including small
    // one-row breaks, then retain the strongest position within each cluster.
    val candidates = mutableListOf<Int>()
    var y = 0
    while (y < height) {
        if (!activeRows[y]) {
            y++
            continue
        }
        val startY = y
        while (y < height && activeRows[y]) y++
        var endY = y - 1

        // Join clusters separated by at most one inactive row.
        while (y + 1 < height && !activeRows[y] && activeRows[y + 1]) {
            y++
            while (y < height && activeRows[y]) y++
            endY = y - 1
        }

        var bestY = startY
        for (yy in startY..endY) {
            if (rowScores[yy] > rowScores[bestY]) bestY = yy
        }
        candidates.add(bestY)
    }

    if (candidates.size < 5) return emptyList()

    data class Staff(val top: Float, val bottom: Float, val spacing: Float, val lines: List<Int>)

    /*
     * Find the most plausible five-line groups rather than greedily consuming
     * every next five candidates. Candidate gaps should be approximately
     * uniform, but some perspective and print variation is accepted.
     */
    val staffCandidates = mutableListOf<Staff>()
    var i = 0
    while (i <= candidates.size - 5) {
        val five = candidates.subList(i, i + 5)
        val gaps = (0 until 4).map { five[it + 1] - five[it] }
        val avgGap = gaps.average().toFloat()
        val maxGap = gaps.maxOrNull() ?: 0
        val minGap = gaps.minOrNull() ?: 0
        val regular = avgGap in 2.0f..36.0f &&
                gaps.all { it >= avgGap * 0.45f && it <= avgGap * 1.75f } &&
                (maxGap - minGap) <= max(6.0f, avgGap * 0.95f)

        if (regular) {
            val top = (five.first() - avgGap / 2f).coerceAtLeast(0f)
            val bottom = (five.last() + avgGap / 2f).coerceAtMost(height.toFloat())
            val candidate = Staff(top, bottom, avgGap, five.toList())

            // Avoid duplicate detections that share most of the same lines.
            val duplicateIndex = staffCandidates.indexOfFirst { existing ->
                existing.lines.intersect(candidate.lines.toSet()).size >= 3
            }
            if (duplicateIndex >= 0) {
                val existing = staffCandidates[duplicateIndex]
                val oldScore = existing.lines.sumOf { rowScores[it] }
                val newScore = candidate.lines.sumOf { rowScores[it] }
                if (newScore > oldScore) staffCandidates[duplicateIndex] = candidate
            } else {
                staffCandidates.add(candidate)
            }
            i += 1
        } else {
            i++
        }
    }

    if (staffCandidates.isEmpty()) return emptyList()

    val staffs = staffCandidates.sortedBy { it.top }

    // Determine horizontal bounds from dark pixels in detected staff bands.
    // Ignore isolated specks by using per-column hit counts rather than a
    // single extreme pixel.
    val columnHits = IntArray(width)
    for (staff in staffs) {
        val fromY = staff.top.toInt().coerceIn(0, height - 1)
        val toY = staff.bottom.toInt().coerceIn(fromY + 1, height)
        for (yy in fromY until toY) {
            val offset = yy * width
            for (x in 0 until width) {
                if (isDark(pixels[offset + x])) columnHits[x]++
            }
        }
    }

    val totalBandRows = staffs.sumOf {
        (it.bottom.toInt() - it.top.toInt()).coerceAtLeast(1)
    }
    val columnThreshold = (totalBandRows * 0.008f).toInt().coerceAtLeast(2)
    var minX = 0
    while (minX < width && columnHits[minX] < columnThreshold) minX++
    var maxX = width - 1
    while (maxX >= 0 && columnHits[maxX] < columnThreshold) maxX--

    if (minX >= maxX) return emptyList()

    val scale = displayWidthPx / width.toFloat()
    val displayHeight = height * scale
    val leftX = (minX - width * 0.006f).coerceAtLeast(0f) * scale
    val rightX = (maxX + width * 0.006f).coerceAtMost(width.toFloat()) * scale
    val green = android.graphics.Color.rgb(0, 220, 40).toLong()
    val red = android.graphics.Color.rgb(240, 35, 35).toLong()

    val result = mutableListOf<AnnotationStroke>()
    // Deliberately keep every five-line staff separate. Piano grand-staff
    // pairing is disabled until individual staff detection is reliable.
    for (staff in staffs) {
        val topY = (staff.top * scale).coerceIn(0f, displayHeight)
        val bottomY = (staff.bottom * scale).coerceIn(0f, displayHeight)
        result.add(
            AnnotationStroke(
                points = listOf(DrawingPoint(leftX, topY), DrawingPoint(rightX, topY)),
                color = green,
                strokeWidth = 3.5f,
                isEraser = false
            )
        )
        result.add(
            AnnotationStroke(
                points = listOf(DrawingPoint(leftX, bottomY), DrawingPoint(rightX, bottomY)),
                color = green,
                strokeWidth = 3.5f,
                isEraser = false
            )
        )
    }

    result.add(
        AnnotationStroke(
            points = listOf(DrawingPoint(leftX, staffs.first().top * scale), DrawingPoint(leftX, staffs.last().bottom * scale)),
            color = red,
            strokeWidth = 3.5f,
            isEraser = false
        )
    )
    result.add(
        AnnotationStroke(
            points = listOf(DrawingPoint(rightX, staffs.first().top * scale), DrawingPoint(rightX, staffs.last().bottom * scale)),
            color = red,
            strokeWidth = 3.5f,
            isEraser = false
        )
    )
    return result
}

enum class DrawingTool {
    SCROLL, PEN, HIGHLIGHTER, ERASER
}

data class SheetPage(
    val sheetFileId: String,
    val pageIndex: Int,
    val bitmap: Bitmap
)

@Composable
fun rememberSheetPages(
    context: Context,
    sheetFiles: List<SheetFile>,
    sheetFileKeys: List<String>
): List<SheetPage> {
    var pages by remember(sheetFileKeys) { mutableStateOf<List<SheetPage>>(emptyList()) }

    LaunchedEffect(sheetFileKeys) {
        if (sheetFiles.isEmpty()) {
            pages = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val loadedPages = mutableListOf<SheetPage>()
            for (file in sheetFiles) {
                try {
                    val uri = Uri.parse(file.uriString)
                    if (file.type == SheetFileType.PDF) {
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: continue
                        pfd.use {
                            val renderer = PdfRenderer(pfd)
                            try {
                                val pageCount = renderer.pageCount
                                for (i in 0 until pageCount) {
                                    val pdfPage = renderer.openPage(i)
                                    try {
                                        val targetWidth = 1200
                                        val scale = (targetWidth.toFloat() / pdfPage.width.toFloat()).coerceAtLeast(1.0f)
                                        val renderWidth = (pdfPage.width * scale).toInt()
                                        val renderHeight = (pdfPage.height * scale).toInt()

                                        val bmp = Bitmap.createBitmap(
                                            renderWidth.coerceAtLeast(100),
                                            renderHeight.coerceAtLeast(100),
                                            Bitmap.Config.ARGB_8888
                                        )
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                        loadedPages.add(SheetPage(file.id, i, bmp))
                                        val currentList = loadedPages.toList()
                                        withContext(Dispatchers.Main) {
                                            pages = currentList
                                        }
                                    } finally {
                                        pdfPage.close()
                                    }
                                }
                            } finally {
                                renderer.close()
                            }
                        }
                    } else {
                        val bmp = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                        if (bmp != null) {
                            loadedPages.add(SheetPage(file.id, 0, bmp))
                            val currentList = loadedPages.toList()
                            withContext(Dispatchers.Main) {
                                pages = currentList
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
    return pages
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetViewerScreen(
    pieceId: Long,
    onBack: () -> Unit,
    onStartPractice: (Piece) -> Unit = {},
    viewModel: RepertoireViewModel = hiltViewModel()
) {
    val allPieces by viewModel.allPieces.collectAsState()
    val piece = allPieces.find { it.id == pieceId }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isDetectingLines by remember { mutableStateOf(false) }

    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.5f) }

    var selectedTool by remember { mutableStateOf(DrawingTool.SCROLL) }
    var penColor by remember { mutableStateOf(Color.Black) }
    var highlighterColor by remember { mutableStateOf(Color(0xFFFFEB3B)) }

    val sheetFileKeys = remember(piece?.sheetFiles) {
        piece?.sheetFiles?.map { "${it.id}:${it.uriString}" } ?: emptyList()
    }
    val sheetPages = rememberSheetPages(context, piece?.sheetFiles ?: emptyList(), sheetFileKeys)

    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isAutoScrolling) {
                scrollState.scrollTo((scrollState.value + scrollSpeed.toInt()).coerceAtMost(scrollState.maxValue))
                if (scrollState.value >= scrollState.maxValue) {
                    isAutoScrolling = false
                    break
                }
                delay(16L)
            }
        }
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text(piece?.title ?: stringResource(R.string.sheet_viewer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (piece != null) {
                        IconButton(onClick = { onStartPractice(piece) }) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = stringResource(R.string.start_practice_session),
                                tint = ForestGreen
                            )
                        }
                    }
                    IconButton(
                        enabled = !isDetectingLines && sheetPages.isNotEmpty(),
                        onClick = {
                            val currentPiece = piece ?: return@IconButton
                            isDetectingLines = true
                            coroutineScope.launch {
                                try {
                                    val displayWidthPx = with(density) {
                                        (context.resources.displayMetrics.widthPixels -
                                                32.dp.roundToPx()).toFloat()
                                    }.coerceAtLeast(100f)
                                    var updatedAnnotations = currentPiece.pageAnnotations
                                    var detectedCount = 0
                                    for (page in sheetPages) {
                                        val detected = withContext(Dispatchers.Default) {
                                            detectStaffBoundaryStrokes(page.bitmap, displayWidthPx)
                                        }
                                        if (detected.isNotEmpty()) {
                                            val existing = updatedAnnotations.find {
                                                it.sheetFileId == page.sheetFileId && it.pageIndex == page.pageIndex
                                            }?.strokes.orEmpty()
                                            // Bereits erzeugte automatische Linien ersetzen, damit ein
                                            // erneuter Lauf keine doppelten Markierungen erzeugt.
                                            val manual = existing.filterNot {
                                                it.color == android.graphics.Color.rgb(0, 220, 40).toLong() ||
                                                        it.color == android.graphics.Color.rgb(240, 35, 35).toLong()
                                            }
                                            val replacement = PageAnnotation(
                                                sheetFileId = page.sheetFileId,
                                                pageIndex = page.pageIndex,
                                                strokes = manual + detected
                                            )
                                            updatedAnnotations = updatedAnnotations.filterNot {
                                                it.sheetFileId == page.sheetFileId && it.pageIndex == page.pageIndex
                                            } + replacement
                                            detectedCount++
                                        }
                                    }
                                    viewModel.updatePageAnnotations(currentPiece, updatedAnnotations)
                                    Toast.makeText(
                                        context,
                                        if (detectedCount > 0) "Notensysteme auf $detectedCount Seite(n) markiert."
                                        else "Keine Notensysteme sicher erkannt.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erkennung fehlgeschlagen: ${e.localizedMessage ?: "unbekannter Fehler"}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isDetectingLines = false
                                }
                            }
                        }
                    ) {
                        if (isDetectingLines) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Notensysteme automatisch markieren",
                                tint = ForestGreen
                            )
                        }
                    }
                    IconButton(onClick = { isAutoScrolling = !isAutoScrolling }) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.auto_scroll),
                            tint = if (isAutoScrolling) ForestGreen else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        bottomBar = {
            Surface(
                color = PaperWhite,
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToolButton(
                                label = stringResource(R.string.tool_scroll),
                                icon = Icons.Default.PanTool,
                                selected = selectedTool == DrawingTool.SCROLL,
                                onClick = { selectedTool = DrawingTool.SCROLL }
                            )
                            ToolButton(
                                label = stringResource(R.string.tool_pen),
                                selected = selectedTool == DrawingTool.PEN,
                                onClick = { selectedTool = DrawingTool.PEN }
                            )
                            ToolButton(
                                label = stringResource(R.string.tool_highlighter),
                                selected = selectedTool == DrawingTool.HIGHLIGHTER,
                                onClick = { selectedTool = DrawingTool.HIGHLIGHTER }
                            )
                            ToolButton(
                                label = stringResource(R.string.tool_eraser),
                                selected = selectedTool == DrawingTool.ERASER,
                                onClick = { selectedTool = DrawingTool.ERASER }
                            )
                        }
                    }

                    if (selectedTool == DrawingTool.PEN || selectedTool == DrawingTool.HIGHLIGHTER) {
                        val colors = if (selectedTool == DrawingTool.PEN) {
                            listOf(
                                Color.Black,
                                Color(0xFFE53935),
                                Color(0xFF1E88E5),
                                Color(0xFF43A047),
                                Color(0xFFFB8C00),
                                Color(0xFF8E24AA)
                            )
                        } else {
                            listOf(
                                Color(0xFFFFEB3B),
                                Color(0xFF7CB342),
                                Color(0xFF00ACC1),
                                Color(0xFFF06292),
                                Color(0xFFFF9800)
                            )
                        }
                        val currentSelectedColor = if (selectedTool == DrawingTool.PEN) penColor else highlighterColor

                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedTool == DrawingTool.PEN) "Stiftfarbe:" else "Textmarker:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                colors.forEach { col ->
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(
                                                width = if (currentSelectedColor == col) 3.dp else 1.dp,
                                                color = if (currentSelectedColor == col) ForestGreen else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                if (selectedTool == DrawingTool.PEN) {
                                                    penColor = col
                                                } else {
                                                    highlighterColor = col
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    if (isAutoScrolling) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.scroll_speed), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = scrollSpeed,
                                onValueChange = { scrollSpeed = it },
                                valueRange = 0.5f..5f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(String.format(Locale.getDefault(), "%.1fx", scrollSpeed), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (piece == null || piece.sheetFiles.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(64.dp), tint = TextHint)
                    Text(stringResource(R.string.no_sheet_files), style = MaterialTheme.typography.bodyLarge, color = TextHint)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (sheetPages.isEmpty()) {
                    CircularProgressIndicator()
                }
                val currentColor = when (selectedTool) {
                    DrawingTool.HIGHLIGHTER -> highlighterColor.copy(alpha = 0.35f)
                    else -> penColor
                }
                sheetPages.forEach { sheetPage ->
                    PdfPageCard(
                        page = sheetPage,
                        pageAnnotations = piece.pageAnnotations,
                        selectedTool = selectedTool,
                        currentColor = currentColor,
                        strokeWidth = if (selectedTool == DrawingTool.HIGHLIGHTER) 24f else if (selectedTool == DrawingTool.ERASER) 20f else 4f,
                        onUpdateAnnotations = { updated -> viewModel.updatePageAnnotations(piece, updated) }
                    )
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) ForestGreenBg else PaperWhite,
        border = BorderStroke(1.dp, if (selected) ForestGreen else Color.LightGray)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) ForestGreen else MaterialTheme.colorScheme.onSurface
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) ForestGreen else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun PdfPageCard(
    page: SheetPage,
    pageAnnotations: List<PageAnnotation>,
    selectedTool: DrawingTool,
    currentColor: Color,
    strokeWidth: Float,
    onUpdateAnnotations: (List<PageAnnotation>) -> Unit
) {
    val sheetFileId = page.sheetFileId
    val pageIndex = page.pageIndex
    val bitmap = page.bitmap
    val currentAnnotation = pageAnnotations.find { it.sheetFileId == sheetFileId && it.pageIndex == pageIndex }
    val strokes = currentAnnotation?.strokes ?: emptyList()

    val strokesState = rememberUpdatedState(strokes)
    val pageAnnotationsState = rememberUpdatedState(pageAnnotations)
    val selectedToolState = rememberUpdatedState(selectedTool)
    val currentColorState = rememberUpdatedState(currentColor)
    val strokeWidthState = rememberUpdatedState(strokeWidth)
    val onUpdateAnnotationsState = rememberUpdatedState(onUpdateAnnotations)

    var currentStrokePoints by remember { mutableStateOf(emptyList<DrawingPoint>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
            )

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(sheetFileId, pageIndex, selectedTool) {
                        if (selectedTool == DrawingTool.SCROLL) return@pointerInput

                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStrokePoints = listOf(DrawingPoint(offset.x, offset.y))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStrokePoints = currentStrokePoints + DrawingPoint(change.position.x, change.position.y)
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
                                            !stroke.points.any { p ->
                                                currentStrokePoints.any { ep ->
                                                    hypot(p.x - ep.x, p.y - ep.y) < 30f
                                                }
                                            }
                                        }
                                    } else {
                                        currentStrokes + newStroke
                                    }

                                    val newAnn = PageAnnotation(
                                        sheetFileId = sheetFileId,
                                        pageIndex = pageIndex,
                                        strokes = updatedStrokes
                                    )
                                    val anns = pageAnnotationsState.value
                                    val otherAnns = anns.filter { !(it.sheetFileId == sheetFileId && it.pageIndex == pageIndex) }
                                    onUpdateAnnotationsState.value(otherAnns + newAnn)
                                    currentStrokePoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentStrokePoints = emptyList()
                            }
                        )
                    }
            ) {
                strokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        val path = Path().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            for (i in 1 until stroke.points.size) {
                                lineTo(stroke.points[i].x, stroke.points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(stroke.color.toInt()),
                            style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                if (currentStrokePoints.size > 1) {
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
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (strokes.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val updatedStrokes = strokes.dropLast(1)
                            val newAnn = PageAnnotation(sheetFileId = sheetFileId, pageIndex = pageIndex, strokes = updatedStrokes)
                            val otherAnns = pageAnnotations.filter { !(it.sheetFileId == sheetFileId && it.pageIndex == pageIndex) }
                            onUpdateAnnotations(otherAnns + newAnn)
                        },
                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Undo, stringResource(R.string.tool_undo), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val newAnn = PageAnnotation(sheetFileId = sheetFileId, pageIndex = pageIndex, strokes = emptyList())
                            val otherAnns = pageAnnotations.filter { !(it.sheetFileId == sheetFileId && it.pageIndex == pageIndex) }
                            onUpdateAnnotations(otherAnns + newAnn)
                        },
                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.tool_clear), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
