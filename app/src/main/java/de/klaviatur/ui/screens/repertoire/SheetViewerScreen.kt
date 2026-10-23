package de.klaviatur.ui.screens.repertoire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.ui.components.localizedLabel
import de.klaviatur.R
import de.klaviatur.data.model.*
import de.klaviatur.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
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

    // Papierhelligkeit = Median; Schwelle relativ dazu (weißes Papier ergibt ~183, wie bisher 180)
    val paperLevel = run {
        val hist = IntArray(256)
        for (p in pixels) {
            hist[(((p shr 16) and 0xff) * 299 + ((p shr 8) and 0xff) * 587 + (p and 0xff) * 114) / 1000]++
        }
        var acc = 0
        var level = 255
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= pixels.size / 2) {
                level = v
                break
            }
        }
        level
    }
    val darkThreshold = (paperLevel * 0.72f).toInt().coerceIn(90, 185)

    fun isDark(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return (r * 299 + g * 587 + b * 114) / 1000 < darkThreshold
    }

    data class Staff(
        val top: Float,
        val bottom: Float,
        val spacing: Float,
        val slope: Float,
        val support: Int,
        val lineCenters: List<Int>
    )

    /*
     * A normal row projection misses lines on skewed phone photos: the staff
     * line moves vertically as x changes, so no single row contains enough
     * dark pixels. Try small shears and accumulate dark pixels along each
     * possible line angle. The best angle is selected by the strength and
     * regularity of five-line staff candidates.
     */
    data class Projection(
        val slope: Float,
        val peaks: List<Pair<Int, Int>>,
        val relaxedPeaks: List<Pair<Int, Int>>
    )
    val centerX = (width - 1) / 2f

    // --- Nur dünne Strukturen (Notenlinien) behalten -------------------------
    // Notenköpfe, Hälse, Balken und Text sind vertikal dicker als eine Linie.
    val dark = BooleanArray(width * height) { isDark(pixels[it]) }

    // Typische Linienstärke = häufigste vertikale Lauflänge (Linien dominieren).
    val runHistogram = IntArray(16)
    for (x in 0 until width) {
        var y = 0
        while (y < height) {
            if (!dark[y * width + x]) { y++; continue }
            val start = y
            while (y < height && dark[y * width + x]) y++
            val len = y - start
            if (len < runHistogram.size) runHistogram[len]++
        }
    }
    var lineThickness = 1
    for (len in 1 until runHistogram.size) {
        if (runHistogram[len] > runHistogram[lineThickness]) lineThickness = len
    }
    val maxRun = (lineThickness * 3 / 2 + 1).coerceIn(3, 8)

    val thin = BooleanArray(width * height)
    var thinCount = 0
    for (x in 0 until width) {
        var y = 0
        while (y < height) {
            if (!dark[y * width + x]) { y++; continue }
            val start = y
            while (y < height && dark[y * width + x]) y++
            if (y - start <= maxRun) {
                for (yy in start until y) thin[yy * width + x] = true
                thinCount += y - start
            }
        }
    }
    val thinXs = IntArray(thinCount)
    val thinYs = IntArray(thinCount)
    var n = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (thin[y * width + x]) { thinXs[n] = x; thinYs[n] = y; n++ }
        }
    }

    val projections = mutableListOf<Projection>()
    val angleSteps = (-8..8).map { it * 0.01f } // approximately +/- 4.6 degrees
    val minimumLineSupport = (width * 0.14f).toInt().coerceAtLeast(30)
    // Lockere Schwelle für den zweiten Durchlauf (nur dort wirksam, wo ein System erwartet wird)
    val relaxedLineSupport = (minimumLineSupport * 0.5f).toInt().coerceAtLeast(20)

    for (slope in angleSteps) {
        val padding = (kotlin.math.abs(slope) * width / 2f).toInt() + 4
        val projection = IntArray(height + padding * 2 + 2)

        for (k in 0 until thinCount) {
            val alignedY = Math.round(thinYs[k] - slope * (thinXs[k] - centerX)) + padding
            if (alignedY in projection.indices) projection[alignedY]++
        }

        projections.add(
            Projection(
                slope = slope,
                peaks = extractLinePeaks(projection, padding, minimumLineSupport),
                relaxedPeaks = extractLinePeaks(projection, padding, relaxedLineSupport)
            )
        )
    }

    fun groupsFor(projection: Projection): List<Staff> {
        val peaks = projection.peaks.sortedBy { it.first }
        if (peaks.size < 5) return emptyList()

        val groups = mutableListOf<Staff>()
        for (i in 0..peaks.size - 5) {
            val five = peaks.subList(i, i + 5)
            val centers = five.map { it.first }
            val gaps = (0 until 4).map { centers[it + 1] - centers[it] }
            val averageGap = gaps.average().toFloat()
            if (averageGap !in 2f..38f) continue

            // Allow uneven printing and perspective, but reject groups with
            // one implausibly large gap that usually separates two staves.
            if (gaps.any { it < averageGap * 0.48f || it > averageGap * 1.65f }) continue
            val spread = (gaps.maxOrNull() ?: 0) - (gaps.minOrNull() ?: 0)
            if (spread > max(5f, averageGap * 0.85f)) continue

            val support = five.sumOf { it.second }
            val regularityPenalty = gaps.sumOf { abs(it - averageGap).toDouble() }.toFloat()
            val score = (support - regularityPenalty * width * 0.10f).toInt()
            val top = centers.first() - averageGap / 2f
            val bottom = centers.last() + averageGap / 2f
            groups.add(
                Staff(
                    top = top.coerceAtLeast(0f),
                    bottom = bottom.coerceAtMost(height.toFloat()),
                    spacing = averageGap,
                    slope = projection.slope,
                    support = score,
                    lineCenters = centers
                )
            )
        }
        return groups
    }

    // Prefer angles which explain several strong, regular five-line groups.
    val candidatesByAngle = projections.map { projection ->
        val groups = groupsFor(projection)
        val sorted = groups.sortedByDescending { it.support }
        val accepted = mutableListOf<Staff>()
        for (candidate in sorted) {
            val overlaps = accepted.any { existing ->
                val sharedLines = existing.lineCenters.count { it in candidate.lineCenters }
                val verticalOverlap = min(existing.bottom, candidate.bottom) -
                        max(existing.top, candidate.top)
                sharedLines >= 2 || verticalOverlap > min(
                    existing.bottom - existing.top,
                    candidate.bottom - candidate.top
                ) * 0.55f
            }
            if (!overlaps) accepted.add(candidate)
        }
        projection.slope to accepted.sortedBy { it.top }
    }

    val best = candidatesByAngle.maxByOrNull { (_, staffs) ->
        // Count matters, but reward strong candidates so text lines do not win
        // merely because they form many weak combinations.
        staffs.sumOf { it.support.toLong() } + staffs.size * width.toLong() * 2L
    } ?: return emptyList()
    val slope = best.first
    // Zweiter Durchlauf: Regelmäßigkeit (Linienabstand, Abstand zwischen Systemen) nutzen
    val relaxedPeaks = projections.first { it.slope == slope }.relaxedPeaks
    val staffs = refineStaffsByRegularity(
        found = best.second.map {
            StaffGeom(it.top, it.bottom, it.spacing, it.slope, it.support, it.lineCenters)
        },
        relaxedPeaks = relaxedPeaks,
        slope = slope,
        imageHeight = height
    ).map { Staff(it.top, it.bottom, it.spacing, it.slope, it.support, it.lineCenters) }
    if (staffs.isEmpty()) return emptyList()

    // Determine content bounds robustly. A column must contain dark pixels in
    // several detected staff bands; a single speck cannot extend the red edge.
    val columnHits = IntArray(width)
    for (staff in staffs) {
        val fromY = (staff.top - staff.spacing).toInt().coerceIn(0, height - 1)
        val toY = (staff.bottom + staff.spacing).toInt().coerceIn(fromY + 1, height)
        for (yy in fromY until toY) {
            val offset = yy * width
            for (x in 0 until width) {
                if (isDark(pixels[offset + x])) columnHits[x]++
            }
        }
    }

    val totalBandRows = staffs.sumOf {
        (it.bottom - it.top + it.spacing * 2f).toInt().coerceAtLeast(1)
    }
    val columnThreshold = (totalBandRows * 0.006f).toInt().coerceAtLeast(2)
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
    val blue = android.graphics.Color.rgb(35, 125, 245).toLong()

    val result = mutableListOf<AnnotationStroke>()

    fun yAtX(lineCenterAtImageCenter: Float, x: Float): Float =
        (lineCenterAtImageCenter + slope * (x - centerX)) * scale

    // --- Klaviersysteme bilden -------------------------------------------------
    // Zwei übereinanderliegende Notensysteme (rechte + linke Hand) gehören zusammen.
    // Der Abstand innerhalb eines Systems ist kleiner als der zur nächsten Zeile.
    data class PianoSystem(val top: Float, val bottom: Float, val staves: List<Staff>)

    val systems = mutableListOf<PianoSystem>()
    var s = 0
    while (s < staffs.size) {
        val upper = staffs[s]
        val lower = staffs.getOrNull(s + 1)
        if (lower != null) {
            val gapToLower = lower.top - upper.bottom
            val following = staffs.getOrNull(s + 2)
            val gapToFollowing =
                if (following != null) following.top - lower.bottom else Float.MAX_VALUE
            if (gapToLower <= upper.spacing * 14f && gapToLower < gapToFollowing) {
                systems.add(PianoSystem(upper.top, lower.bottom, listOf(upper, lower)))
                s += 2
                continue
            }
        }
        // Einzelnes System ohne Partner (z. B. Erkennung eines Systems fehlgeschlagen)
        systems.add(PianoSystem(upper.top, upper.bottom, listOf(upper)))
        s += 1
    }

    // Grüne Ober-/Unterkante pro Klaviersystem (beide Hände zusammen)
    for (system in systems) {
        val topLeft = yAtX(system.top, 0f).coerceIn(0f, displayHeight)
        val topRight = yAtX(system.top, width.toFloat()).coerceIn(0f, displayHeight)
        val bottomLeft = yAtX(system.bottom, 0f).coerceIn(0f, displayHeight)
        val bottomRight = yAtX(system.bottom, width.toFloat()).coerceIn(0f, displayHeight)

        result.add(
            AnnotationStroke(
                points = listOf(DrawingPoint(leftX, topLeft), DrawingPoint(rightX, topRight)),
                color = green, strokeWidth = 3.5f, isEraser = false
            )
        )
        result.add(
            AnnotationStroke(
                points = listOf(DrawingPoint(leftX, bottomLeft), DrawingPoint(rightX, bottomRight)),
                color = green, strokeWidth = 3.5f, isEraser = false
            )
        )
    }

    // --- Taktstriche -------------------------------------------------------------
    // Ein Taktstrich ist eine lange, dünne, (fast) senkrechte Linie, die ein ganzes
    // Klaviersystem von der obersten bis zur untersten Notenlinie durchläuft.
    // Notenhälse sind dafür zu kurz oder ragen über das System hinaus; Notenköpfe,
    // Balken usw. sitzen immer direkt daneben. Fotos sind oft perspektivisch
    // verzerrt, daher darf die Linie leicht geneigt sein.
    val thick = BooleanArray(width * height)    // dunkel, aber keine dünne Notenlinie
    val thickDil = BooleanArray(width * height) // wie `thick`, horizontal 1 px breiter
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (dark[row + x] && !thin[row + x]) {
                thick[row + x] = true
                thickDil[row + x] = true
                if (x > 0) thickDil[row + x - 1] = true
                if (x < width - 1) thickDil[row + x + 1] = true
            }
        }
    }

    class BarCandidate(
        val x: Int,       // Spalte an der Oberkante
        val tilt: Float,  // Neigung (Pixel in x pro Pixel in y)
        val up: Float,    // längster dunkler Lauf über dem Bereich, in Linienabständen
        val down: Float,  // längster dunkler Lauf unter dem Bereich, in Linienabständen
        val left: Float,  // Anteil dunkler Pixel direkt links daneben
        val right: Float  // Anteil dunkler Pixel direkt rechts daneben
    )

    // Anteil der Zeilen yTop..yBottom, in denen die geneigte Linie auf dickem Pixel liegt.
    fun lineCoverage(x0: Int, tilt: Float, yTop: Float, yBottom: Float): Float {
        val y0 = Math.round(yTop)
        val y1 = Math.round(yBottom)
        if (y1 <= y0) return 0f
        var hits = 0
        for (y in y0..y1) {
            val xx = Math.round(x0 + tilt * (y - y0))
            if (y in 0 until height && xx in 0 until width && thickDil[y * width + xx]) hits++
        }
        return hits.toFloat() / (y1 - y0 + 1)
    }

    // Längster zusammenhängender dunkler Lauf entlang der verlängerten Linie.
    fun longestRun(xc: Int, tilt: Float, yRef: Float, yFrom: Float, yTo: Float): Int {
        var bestRun = 0
        var run = 0
        for (y in Math.round(yFrom)..Math.round(yTo)) {
            val xx = Math.round(xc + tilt * (y - yRef))
            if (y in 0 until height && xx in 0 until width && thickDil[y * width + xx]) {
                run++
                if (run > bestRun) bestRun = run
            } else {
                run = 0
            }
        }
        return bestRun
    }

    // Anteil dunkler Pixel in einer Spalte neben der Linie (nur für getrennte Hände).
    fun sideFraction(xc: Int, tilt: Float, yTop: Float, yBottom: Float, offset: Int): Float {
        val y0 = Math.round(yTop)
        val y1 = Math.round(yBottom)
        var hits = 0
        var total = 0
        for (y in y0..y1) {
            val xx = Math.round(xc + tilt * (y - yTop)) + offset
            total++
            if (y in 0 until height && xx in 0 until width && thick[y * width + xx]) hits++
        }
        return if (total > 0) hits.toFloat() / total else 0f
    }

    val scanMinX = minX
    val scanMaxX = maxX

    // Sucht senkrechte Linien, die den Bereich yTopAt..yBottomAt fast vollständig füllen.
    fun findBars(
        yTopAt: (Int) -> Float,
        yBottomAt: (Int) -> Float,
        spacing: Float
    ): List<BarCandidate> {
        val tilts = FloatArray(13) { (it - 6) * 0.01f }
        val bestCov = FloatArray(width)
        val bestTilt = FloatArray(width)
        for (x in scanMinX..scanMaxX) {
            val yt = yTopAt(x)
            val yb = yBottomAt(x)
            for (tilt in tilts) {
                val c = lineCoverage(x, tilt, yt, yb)
                if (c > bestCov[x]) {
                    bestCov[x] = c
                    bestTilt[x] = tilt
                }
            }
        }

        val found = mutableListOf<BarCandidate>()
        var x = scanMinX
        while (x <= scanMaxX) {
            if (bestCov[x] < 0.85f) { x++; continue }
            val a = x
            while (x <= scanMaxX && bestCov[x] >= 0.85f) x++
            val b = x - 1
            // Zu breit: Notenblock, Text o. Ä. (Doppel-/Wiederholungsstriche sind schmaler)
            if (b - a + 1 > 2.2f * spacing) continue

            // Position = Mitte der Spalten mit der besten Abdeckung. Dadurch zieht ein
            // direkt daneben liegendes Zeichen (z. B. Arpeggio) die Linie nicht zur Seite.
            var maxCov = 0f
            for (k in a..b) maxCov = max(maxCov, bestCov[k])
            var first = -1
            var last = -1
            for (k in a..b) {
                if (bestCov[k] >= maxCov - 0.02f) {
                    if (first < 0) first = k
                    last = k
                }
            }
            val xc = (first + last) / 2
            val tilt = bestTilt[xc]
            val yt = yTopAt(xc)
            val yb = yBottomAt(xc)

            val up = longestRun(xc, tilt, yt, yt - 1.2f * spacing, yt - 0.3f * spacing) / spacing
            val down = longestRun(xc, tilt, yt, yb + 0.3f * spacing, yb + 1.2f * spacing) / spacing
            val half = (b - a) / 2 + 1
            val left = max(
                sideFraction(xc, tilt, yt, yb, -half - 2),
                sideFraction(xc, tilt, yt, yb, -half - 3)
            )
            val right = max(
                sideFraction(xc, tilt, yt, yb, half + 2),
                sideFraction(xc, tilt, yt, yb, half + 3)
            )
            found.add(BarCandidate(xc, tilt, up, down, left, right))
        }
        return found
    }

    for (system in systems) {
        val upper = system.staves.first()
        val lower = system.staves.last()
        val spacing = upper.spacing

        val topOfUpper = { x: Int -> upper.lineCenters.first() + upper.slope * (x - centerX) }
        val bottomOfUpper = { x: Int -> upper.lineCenters.last() + upper.slope * (x - centerX) }
        val topOfLower = { x: Int -> lower.lineCenters.first() + lower.slope * (x - centerX) }
        val bottomOfLower = { x: Int -> lower.lineCenters.last() + lower.slope * (x - centerX) }

        val maxExtension = 0.45f  // Hälse ragen länger über das System hinaus
        val maxNeighbour = 0.15f  // Noten direkt neben der Linie => eher ein Hals

        // (x an der Oberkante, Neigung)
        val bars = mutableListOf<Pair<Float, Float>>()

        // 1) Taktstrich über das ganze Klaviersystem (übliche Notation, durchgehend)
        for (c in findBars(topOfUpper, bottomOfLower, spacing)) {
            if (c.up <= maxExtension && c.down <= maxExtension) bars.add(c.x.toFloat() to c.tilt)
        }

        // 2) Zusätzlich: Taktstriche, die in beiden Händen einzeln vorhanden sind
        //    (Notation ohne durchgehenden Strich zwischen den Systemen)
        if (system.staves.size == 2) {
            val inUpper = findBars(topOfUpper, bottomOfUpper, spacing).filter {
                it.up <= maxExtension && it.left <= maxNeighbour && it.right <= maxNeighbour
            }
            val inLower = findBars(topOfLower, bottomOfLower, spacing).filter {
                it.down <= maxExtension && it.left <= maxNeighbour && it.right <= maxNeighbour
            }
            for (c in inUpper) {
                val inBoth = inLower.any { abs(it.x - c.x) <= spacing }
                val known = bars.any { abs(it.first - c.x) <= spacing }
                if (inBoth && !known) bars.add(c.x.toFloat() to c.tilt)
            }
        } else {
            for (c in findBars(topOfUpper, bottomOfUpper, spacing)) {
                if (c.up <= maxExtension && c.down <= maxExtension &&
                    c.left <= maxNeighbour && c.right <= maxNeighbour &&
                    bars.none { abs(it.first - c.x) <= spacing }
                ) bars.add(c.x.toFloat() to c.tilt)
            }
        }

        // Doppel-/Wiederholungsstriche (dünn + dick) zu einem Strich zusammenfassen
        bars.sortBy { it.first }
        val merged = mutableListOf<MutableList<Pair<Float, Float>>>()
        for (bar in bars) {
            if (merged.isNotEmpty() && bar.first - merged.last().last().first <= 1.3f * spacing) {
                merged.last().add(bar)
            } else {
                merged.add(mutableListOf(bar))
            }
        }

        for (group in merged) {
            val x = group.map { it.first }.average().toFloat()
            val tilt = group.map { it.second }.average().toFloat()
            // Systemanfang und Zeilenende fallen mit den roten Außenkanten zusammen
            if (x <= minX + 3.5f * spacing || x >= maxX - 3.5f * spacing) continue

            val yTop = (system.top + slope * (x - centerX)).coerceIn(0f, height.toFloat())
            val yBottom = (system.bottom + slope * (x - centerX)).coerceIn(0f, height.toFloat())
            val xBottom = (x + tilt * (yBottom - yTop)).coerceIn(0f, width.toFloat())
            result.add(
                AnnotationStroke(
                    points = listOf(
                        DrawingPoint(x * scale, yTop * scale),
                        DrawingPoint(xBottom * scale, yBottom * scale)
                    ),
                    color = blue, strokeWidth = 2.5f, isEraser = false
                )
            )
        }
    }

    // Rote Außenkanten über alle Systeme
    val topY = yAtX(systems.first().top, minX.toFloat()).coerceIn(0f, displayHeight)
    val bottomY = yAtX(systems.last().bottom, minX.toFloat()).coerceIn(0f, displayHeight)
    val topRightY = yAtX(systems.first().top, maxX.toFloat()).coerceIn(0f, displayHeight)
    val bottomRightY = yAtX(systems.last().bottom, maxX.toFloat()).coerceIn(0f, displayHeight)

    result.add(
        AnnotationStroke(
            points = listOf(DrawingPoint(leftX, topY), DrawingPoint(leftX, bottomY)),
            color = red, strokeWidth = 3.5f, isEraser = false
        )
    )
    result.add(
        AnnotationStroke(
            points = listOf(DrawingPoint(rightX, topRightY), DrawingPoint(rightX, bottomRightY)),
            color = red, strokeWidth = 3.5f, isEraser = false
        )
    )

    return result
}

enum class DrawingTool {
    SCROLL, PEN, HIGHLIGHTER, ERASER, EDIT
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var editAction by remember { mutableStateOf(EditAction.NONE) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val hScrollState = rememberScrollState()
    var penColor by remember { mutableStateOf(Color.Black) }
    var highlighterColor by remember { mutableStateOf(Color(0xFFFFEB3B)) }

    // Taktauswahl & Abschnitte
    var selectedMeasures by remember { mutableStateOf(setOf<Int>()) }
    var showCreateSectionDialog by remember { mutableStateOf(false) }
    var showSectionsOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(selectedTool) {
        if (selectedTool != DrawingTool.EDIT) editAction = EditAction.NONE
    }

    val sheetFileKeys = remember(piece?.sheetFiles) {
        piece?.sheetFiles?.map { "${it.id}:${it.uriString}" } ?: emptyList()
    }
    val sheetPages = rememberSheetPages(context, piece?.sheetFiles ?: emptyList(), sheetFileKeys)

    var isFullscreen by remember { mutableStateOf(false) }

    fun getPageTopY(targetPageIndex: Int, pageWidthPx: Float): Int {
        if (targetPageIndex <= 0 || sheetPages.isEmpty()) return 0
        var yAcc = 0f
        val spacingPx = with(density) { 24.dp.toPx() }
        val maxIdx = targetPageIndex.coerceAtMost(sheetPages.size - 1)
        for (i in 0 until maxIdx) {
            val bmp = sheetPages[i].bitmap
            val pageH = pageWidthPx * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1).toFloat()) * zoom
            yAcc += pageH + spacingPx
        }
        return yAcc.toInt()
    }

    fun getCurrentPageIndex(pageWidthPx: Float): Int {
        if (sheetPages.isEmpty()) return 0
        val currentScrollY = scrollState.value
        var yAcc = 0f
        val spacingPx = with(density) { 24.dp.toPx() }
        for (i in sheetPages.indices) {
            val bmp = sheetPages[i].bitmap
            val pageH = pageWidthPx * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1).toFloat()) * zoom
            val pageBottom = yAcc + pageH + spacingPx / 2f
            if (currentScrollY < pageBottom) {
                return i
            }
            yAcc += pageH + spacingPx
        }
        return sheetPages.size - 1
    }

    // Takte extrahieren & Gesamttaktanzahl synchronisieren
    val allMeasures = remember(sheetPages, piece?.pageAnnotations) {
        if (sheetPages.isEmpty()) emptyList()
        else extractAllMeasures(sheetPages, piece?.pageAnnotations ?: emptyList())
    }
    val totalCalculatedBars = allMeasures.size
    LaunchedEffect(totalCalculatedBars, piece) {
        if (piece != null && totalCalculatedBars > 0 && totalCalculatedBars != piece.totalBars) {
            viewModel.upsertPiece(piece.copy(totalBars = totalCalculatedBars))
        }
    }

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
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(piece?.title ?: stringResource(R.string.sheet_viewer_title), style = MaterialTheme.typography.titleMedium)
                            val barText = if (piece != null && piece.totalBars > 0) "${piece.totalBars} Takte" else ""
                            val pageWidthPx = with(density) {
                                (context.resources.displayMetrics.widthPixels - 32.dp.roundToPx()).toFloat()
                            }.coerceAtLeast(100f)
                            val currentPage = getCurrentPageIndex(pageWidthPx)
                            val pageText = if (sheetPages.size > 1) "Seite ${currentPage + 1}/${sheetPages.size}" else ""
                            val subtitle = listOf(barText, pageText).filter { it.isNotBlank() }.joinToString(" • ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Vollbildmodus",
                                tint = LocalContentColor.current
                            )
                        }
                        if (piece != null) {
                            IconButton(onClick = { onStartPractice(piece) }) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = stringResource(R.string.start_practice_session),
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
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                Surface(
                    color = PaperWhite,
                    tonalElevation = 8.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ToolButton(
                                label = "(Verschieben)",
                                icon = Icons.Default.PanTool,
                                selected = selectedTool == DrawingTool.SCROLL,
                                onClick = { selectedTool = DrawingTool.SCROLL }
                            )
                            ToolButton(
                                label = "(Stift)",
                                icon = Icons.Default.Create,
                                selected = selectedTool == DrawingTool.PEN,
                                onClick = { selectedTool = DrawingTool.PEN }
                            )
                            ToolButton(
                                label = "(Marker)",
                                icon = Icons.Default.Highlight,
                                selected = selectedTool == DrawingTool.HIGHLIGHTER,
                                onClick = { selectedTool = DrawingTool.HIGHLIGHTER }
                            )
                            ToolButton(
                                label = "(Radierer)",
                                icon = Icons.Default.CleaningServices,
                                selected = selectedTool == DrawingTool.ERASER,
                                onClick = { selectedTool = DrawingTool.ERASER }
                            )
                            ToolButton(
                                label = "(Takt)",
                                icon = Icons.Default.Tune,
                                selected = selectedTool == DrawingTool.EDIT,
                                onClick = { selectedTool = DrawingTool.EDIT }
                            )
                            ToolButton(
                                label = "(Abschnitte)",
                                icon = Icons.Default.Bookmark,
                                selected = showSectionsOverlay,
                                onClick = { showSectionsOverlay = !showSectionsOverlay }
                            )
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

                        if (selectedTool == DrawingTool.EDIT) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolButton(
                                    label = if (isDetectingLines) "Erkennen..." else "Takterkennung",
                                    icon = Icons.Default.AutoAwesome,
                                    selected = isDetectingLines,
                                    onClick = {
                                        val currentPiece = piece ?: return@ToolButton
                                        if (isDetectingLines || sheetPages.isEmpty()) return@ToolButton
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
                                                        val manual = existing.filterNot {
                                                            it.color == android.graphics.Color.rgb(0, 220, 40).toLong() ||
                                                                    it.color == android.graphics.Color.rgb(240, 35, 35).toLong() ||
                                                                    it.color == android.graphics.Color.rgb(35, 125, 245).toLong()
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
                                                val updatedMeasures = extractAllMeasures(sheetPages, updatedAnnotations)
                                                val newTotal = updatedMeasures.size
                                                if (newTotal > 0 && currentPiece.totalBars != newTotal) {
                                                    viewModel.upsertPiece(currentPiece.copy(pageAnnotations = updatedAnnotations, totalBars = newTotal))
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (detectedCount > 0) "Notensysteme auf $detectedCount Seite(n) erkannt ($newTotal Takte)."
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
                                )
                                ToolButton(
                                    label = "System +",
                                    selected = editAction == EditAction.ADD_SYSTEM,
                                    onClick = {
                                        editAction = if (editAction == EditAction.ADD_SYSTEM) EditAction.NONE
                                        else EditAction.ADD_SYSTEM
                                    }
                                )
                                ToolButton(
                                    label = "Taktstrich +",
                                    selected = editAction == EditAction.ADD_BARLINE,
                                    onClick = {
                                        editAction = if (editAction == EditAction.ADD_BARLINE) EditAction.NONE
                                        else EditAction.ADD_BARLINE
                                    }
                                )
                            }
                            Text(
                                text = when (editAction) {
                                    EditAction.NONE -> "Linie antippen = auswählen · lange drücken und ziehen = verschieben"
                                    EditAction.ADD_SYSTEM -> "Auf die Mitte des neuen Systems tippen"
                                    EditAction.ADD_BARLINE -> "In ein System tippen, um einen Taktstrich zu setzen"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
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
            val pageWidthPx = with(density) {
                (context.resources.displayMetrics.widthPixels - 32.dp.roundToPx()).toFloat()
            }.coerceAtLeast(100f)
            val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
            val currentColor = when (selectedTool) {
                DrawingTool.HIGHLIGHTER -> highlighterColor.copy(alpha = 0.35f)
                else -> penColor
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        // Pinch-Zoom: nur bei >= 2 Fingern eingreifen, ein Finger scrollt wie gewohnt
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val oldZoom = zoom
                                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, 4f)
                                    val ratio = newZoom / oldZoom
                                    zoom = newZoom
                                    // Inhalt unter den Fingern soll stehen bleiben
                                    hScrollState.dispatchRawDelta(
                                        (hScrollState.value + centroid.x) * (ratio - 1f) - pan.x
                                    )
                                    scrollState.dispatchRawDelta(
                                        (scrollState.value + centroid.y) * (ratio - 1f) - pan.y
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(sheetPages, selectedTool, zoom, pageWidthPx) {
                        if (selectedTool != DrawingTool.SCROLL || sheetPages.size <= 1) return@pointerInput
                        val thresholdPx = 25.dp.toPx()
                        val minVelocityPxPerSec = 200f

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val startTime = System.currentTimeMillis()
                            val startX = down.position.x
                            val startY = down.position.y
                            var handled = false

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.find { it.id == down.id } ?: break

                                if (!handled && event.changes.count { it.pressed } == 1) {
                                    val dx = change.position.x - startX
                                    val dy = change.position.y - startY
                                    val absDx = abs(dx)
                                    val absDy = abs(dy)
                                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

                                    if (absDx > thresholdPx && absDx > absDy * 1.2f) {
                                        val velocityX = (dx / elapsedMs.toFloat()) * 1000f
                                        if (abs(velocityX) >= minVelocityPxPerSec || absDx > 45.dp.toPx()) {
                                            handled = true
                                            val currentPage = getCurrentPageIndex(pageWidthPx)
                                            val targetPage = if (dx < 0f) {
                                                (currentPage + 1).coerceAtMost(sheetPages.size - 1)
                                            } else {
                                                (currentPage - 1).coerceAtLeast(0)
                                            }
                                            if (targetPage != currentPage) {
                                                coroutineScope.launch {
                                                    val targetY = getPageTopY(targetPage, pageWidthPx)
                                                    scrollState.animateScrollTo(targetY)
                                                }
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Auswahlleiste bei ausgewählten Takten
                    if (selectedMeasures.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = ForestGreenBg,
                            border = BorderStroke(1.dp, ForestGreen),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val count = selectedMeasures.size
                                val ranges = groupContiguousMeasures(selectedMeasures)
                                val text = if (ranges.size > 1) {
                                    "$count Takte in ${ranges.size} Abschnitten"
                                } else {
                                    "$count Takt(e) ausgewählt"
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ForestGreen
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { showCreateSectionDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Abschnitt erstellen", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { selectedMeasures = emptySet() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Auswahl aufheben", tint = ForestGreen)
                                    }
                                }
                            }
                        }
                    }

                    // Übersichtsleiste für Abschnitte
                    if (showSectionsOverlay && piece != null && piece.sections.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = PaperWhite,
                            border = BorderStroke(1.dp, BorderColor),
                            shadowElevation = 2.dp
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    text = "Abschnitte im Stück (${piece.sections.size}):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    piece.sections.sortedBy { it.startBar }.forEach { sec ->
                                        val color = Color(sec.level.color)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = color.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, color)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                )
                                                val barText = if (sec.startBar == sec.endBar) "Takt ${sec.startBar}" else "Takt ${sec.startBar}–${sec.endBar}"
                                                val titleText = if (sec.name.isNotBlank()) "${sec.name} ($barText)" else barText
                                                Text(
                                                    text = "$titleText • ${sec.level.localizedLabel()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.DarkGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Column(
                            Modifier
                                .horizontalScroll(hScrollState)
                                .widthIn(min = screenWidthDp)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (sheetPages.isEmpty()) {
                                CircularProgressIndicator()
                            }
                            sheetPages.forEach { sheetPage ->
                                val pageMeasures = allMeasures.filter {
                                    it.sheetFileId == sheetPage.sheetFileId && it.pageIndex == sheetPage.pageIndex
                                }
                                Box(
                                    modifier = Modifier
                                        .width(with(density) { (pageWidthPx * zoom).toDp() })
                                        .wrapContentHeight()
                                        .graphicsLayer {
                                            scaleX = zoom
                                            scaleY = zoom
                                            transformOrigin = TransformOrigin(0f, 0f)
                                        }
                                ) {
                                    PdfPageCard(
                                        page = sheetPage,
                                        pageAnnotations = piece.pageAnnotations,
                                        selectedTool = selectedTool,
                                        currentColor = currentColor,
                                        strokeWidth = if (selectedTool == DrawingTool.HIGHLIGHTER) 24f else if (selectedTool == DrawingTool.ERASER) 20f else 4f,
                                        zoom = zoom,
                                        pageWidthPx = pageWidthPx,
                                        editAction = editAction,
                                        pageMeasures = pageMeasures,
                                        selectedMeasures = selectedMeasures,
                                        sections = piece.sections,
                                        showSectionsOverlay = showSectionsOverlay,
                                        onToggleMeasureSelection = { idx ->
                                            selectedMeasures = if (selectedMeasures.contains(idx)) selectedMeasures - idx else selectedMeasures + idx
                                        },
                                        onSelectMeasure = { idx ->
                                            selectedMeasures = selectedMeasures + idx
                                        },
                                        onUpdateAnnotations = { updated -> viewModel.updatePageAnnotations(piece, updated) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isFullscreen) {
                    IconButton(
                        onClick = { isFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(PaperWhite.copy(alpha = 0.85f), CircleShape)
                            .border(1.dp, Color.LightGray, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Vollbild beenden",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (sheetPages.size > 1) {
                        val currentPage = getCurrentPageIndex(pageWidthPx)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = PaperWhite.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.LightGray),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "Seite ${currentPage + 1} / ${sheetPages.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSectionDialog && piece != null && selectedMeasures.isNotEmpty()) {
        val ranges = groupContiguousMeasures(selectedMeasures)
        CreateSectionDialog(
            ranges = ranges,
            onDismiss = { showCreateSectionDialog = false },
            onConfirm = { newSections ->
                val updatedSections = addSectionsWithOverwrite(piece.sections, newSections)
                viewModel.updateSections(piece, updatedSections)
                selectedMeasures = emptySet()
                showCreateSectionDialog = false
                Toast.makeText(context, "${newSections.size} Abschnitt(e) hinzugefügt.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

fun addSectionsWithOverwrite(
    existingSections: List<PieceSection>,
    newSections: List<PieceSection>
): List<PieceSection> {
    if (newSections.isEmpty()) return existingSections
    val newBarSet = newSections.flatMap { it.startBar..it.endBar }.toSet()

    val updatedExisting = mutableListOf<PieceSection>()

    for (oldSec in existingSections) {
        val remainingBars = (oldSec.startBar..oldSec.endBar).filter { it !in newBarSet }
        if (remainingBars.isEmpty()) {
            continue
        }
        val contiguousRanges = groupContiguousMeasures(remainingBars.toSet())
        for (range in contiguousRanges) {
            val isUnchanged = (range.first == oldSec.startBar && range.last == oldSec.endBar)
            val name = if (isUnchanged) {
                oldSec.name
            } else {
                if (oldSec.name.startsWith("Takt ")) {
                    if (range.first == range.last) "Takt ${range.first}" else "Takt ${range.first}–${range.last}"
                } else {
                    oldSec.name
                }
            }
            updatedExisting.add(
                oldSec.copy(
                    name = name,
                    startBar = range.first,
                    endBar = range.last
                )
            )
        }
    }

    val combined = updatedExisting + newSections
    return combined.sortedBy { it.startBar }
}

@Composable
fun CreateSectionDialog(
    ranges: List<IntRange>,
    onDismiss: () -> Unit,
    onConfirm: (List<PieceSection>) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(ProgressLevel.NOT_LEARNED) }
    var singleName by remember(ranges) {
        mutableStateOf(
            if (ranges.size == 1) {
                val r = ranges[0]
                if (r.first == r.last) "Takt ${r.first}" else "Takt ${r.first}–${r.last}"
            } else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (ranges.size == 1) "Abschnitt erstellen" else "${ranges.size} Abschnitte erstellen",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ranges.size == 1) {
                    OutlinedTextField(
                        value = singleName,
                        onValueChange = { singleName = it },
                        label = { Text("Name des Abschnitts") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Es wurden ${ranges.size} nicht zusammenhängende Bereiche markiert:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PaperBeige, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        ranges.forEach { r ->
                            Text(
                                text = if (r.first == r.last) "• Takt ${r.first}" else "• Takt ${r.first}–${r.last}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForestGreen
                            )
                        }
                    }
                    Text(
                        text = "Diese werden als ${ranges.size} separate Abschnitte angelegt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Text(
                    text = "Status / Fortschritt:",
                    style = MaterialTheme.typography.labelMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressLevel.values().forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedLevel = level }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = (selectedLevel == level),
                                onClick = { selectedLevel = level }
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(level.color))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = level.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sections = if (ranges.size == 1) {
                        val name = if (singleName.isNotBlank()) singleName else (if (ranges[0].first == ranges[0].last) "Takt ${ranges[0].first}" else "Takt ${ranges[0].first}–${ranges[0].last}")
                        listOf(
                            PieceSection(
                                name = name,
                                startBar = ranges[0].first,
                                endBar = ranges[0].last,
                                level = selectedLevel
                            )
                        )
                    } else {
                        ranges.map { r ->
                            PieceSection(
                                name = if (r.first == r.last) "Takt ${r.first}" else "Takt ${r.first}–${r.last}",
                                startBar = r.first,
                                endBar = r.last,
                                level = selectedLevel
                            )
                        }
                    }
                    onConfirm(sections)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
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
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) ForestGreen else MaterialTheme.colorScheme.onSurface
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                    color = if (selected) ForestGreen else TextMuted
                )
            }
        }
    }
}
