package de.klaviatur.ui.screens.repertoire

import de.klaviatur.data.model.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/* ════════════════════════════════════════════════════════════════════
 * TEIL 1: Regelmäßigkeit der Notensysteme nutzen
 * ════════════════════════════════════════════════════════════════════ */

/** Spiegelbild der lokalen Klasse `Staff` in detectStaffBoundaryStrokes. */
internal data class StaffGeom(
    val top: Float,
    val bottom: Float,
    val spacing: Float,
    val slope: Float,
    val support: Int,
    val lineCenters: List<Int>
)

/**
 * Gleiche Logik wie vorher im Inline-Code: aufeinanderfolgende Bins über der
 * Schwelle werden zu einem Cluster zusammengefasst (Position = Schwerpunkt).
 * Jetzt als Funktion, damit sie mit zwei Schwellen aufgerufen werden kann.
 */
internal fun extractLinePeaks(
    projection: IntArray,
    padding: Int,
    minSupport: Int
): List<Pair<Int, Int>> {
    val peaks = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < projection.size) {
        if (projection[i] < minSupport) {
            i++
            continue
        }
        val start = i
        while (i < projection.size && projection[i] >= minSupport) i++
        val end = i - 1

        // Position = Schwerpunkt des Clusters, Stärke = stärkstes Bin.
        var best = start
        var weighted = 0L
        var total = 0L
        for (j in start..end) {
            if (projection[j] > projection[best]) best = j
            weighted += j.toLong() * projection[j]
            total += projection[j]
        }
        val centroid = Math.round(weighted.toDouble() / total).toInt()
        peaks.add((centroid - padding) to projection[best])
    }
    return peaks
}

/**
 * Zweiter Durchlauf mit gelernten Regelmäßigkeiten:
 *  1. Linienabstand der Systeme: Median bestimmen, Ausreißer verwerfen.
 *  2. Typische Abstände zwischen den Systemen merken ("Rhythmus").
 *  3. Mit den schwachen Peaks (niedrige Schwelle) nach fehlenden Systemen
 *     suchen. Ein Kandidat muss fünf Linien im erwarteten Abstand haben UND
 *     im Rhythmus zu einem Nachbarn passen.
 */
internal fun refineStaffsByRegularity(
    found: List<StaffGeom>,
    relaxedPeaks: List<Pair<Int, Int>>,
    slope: Float,
    imageHeight: Int
): List<StaffGeom> {
    if (found.size < 2) return found

    val sortedSpacings = found.map { it.spacing }.sorted()
    val medianSpacing = sortedSpacings[sortedSpacings.size / 2]

    val kept = found
        .filter { abs(it.spacing - medianSpacing) <= medianSpacing * 0.25f }
        .sortedBy { it.top }
    if (kept.size < 2 || kept.size * 2 < found.size) return found

    val typicalGaps = kept.zipWithNext { a, b -> b.top - a.bottom }
    fun rhythmMatches(gap: Float): Boolean =
        typicalGaps.any { abs(gap - it) <= max(it * 0.25f, medianSpacing * 2f) }

    val peaks = relaxedPeaks.sortedBy { it.first }
    val lineTolerance = max(2f, medianSpacing * 0.2f)
    val candidates = mutableListOf<StaffGeom>()

    for (start in peaks) {
        val chosen = mutableListOf(start)
        var complete = true
        for (k in 1..4) {
            val previous = chosen.last().first
            val expected = previous + medianSpacing
            val hit = peaks
                .filter { it.first > previous }
                .minByOrNull { abs(it.first - expected) }
            if (hit == null || abs(hit.first - expected) > lineTolerance) {
                complete = false
                break
            }
            chosen.add(hit)
        }
        if (!complete) continue

        val centers = chosen.map { it.first }
        val spacing = (centers.last() - centers.first()) / 4f
        if (abs(spacing - medianSpacing) > max(1f, medianSpacing * 0.12f)) continue

        candidates.add(
            StaffGeom(
                top = (centers.first() - spacing / 2f).coerceAtLeast(0f),
                bottom = (centers.last() + spacing / 2f).coerceAtMost(imageHeight.toFloat()),
                spacing = spacing,
                slope = slope,
                support = chosen.sumOf { it.second },
                lineCenters = centers
            )
        )
    }

    val result = kept.toMutableList()
    // Bis zu drei Runden: ein neu gefundenes System kann das nächste "freischalten".
    for (round in 0 until 3) {
        var added = false
        for (candidate in candidates.sortedByDescending { it.support }) {
            val tooClose = result.any {
                min(it.bottom, candidate.bottom) - max(it.top, candidate.top) > -medianSpacing
            }
            if (tooClose) continue

            val index = result.indexOfFirst { it.top > candidate.top }
                .let { if (it < 0) result.size else it }
            val above = result.getOrNull(index - 1)
            val below = result.getOrNull(index)
            val fits = (above != null && rhythmMatches(candidate.top - above.bottom)) ||
                    (below != null && rhythmMatches(below.top - candidate.bottom))
            if (!fits) continue

            result.add(index, candidate)
            added = true
        }
        if (!added) break
    }
    return result.sortedBy { it.top }
}

/* ════════════════════════════════════════════════════════════════════
 * TEIL 2: Manuelle Bearbeitung der Linien
 *
 * Es gibt kein neues Datenmodell: Die Linien bleiben AnnotationStrokes und
 * werden an Farbe + 2 Punkten erkannt (grün = Systemkante, blau = Taktstrich,
 * rot = Seitenrand). Koordinaten sind Pixel bei Zoom 1.
 * ════════════════════════════════════════════════════════════════════ */

enum class EditAction { NONE, ADD_SYSTEM, ADD_BARLINE, DELETE }

internal val LINE_COLOR_SYSTEM_EDGE = android.graphics.Color.rgb(0, 220, 40).toLong()
internal val LINE_COLOR_PAGE_EDGE = android.graphics.Color.rgb(240, 35, 35).toLong()
internal val LINE_COLOR_BARLINE = android.graphics.Color.rgb(35, 125, 245).toLong()

internal enum class LineKind { SYSTEM_EDGE, PAGE_EDGE, BARLINE }

internal fun AnnotationStroke.lineKind(): LineKind? {
    if (isEraser || points.size != 2) return null
    return when (color) {
        LINE_COLOR_SYSTEM_EDGE -> LineKind.SYSTEM_EDGE
        LINE_COLOR_PAGE_EDGE -> LineKind.PAGE_EDGE
        LINE_COLOR_BARLINE -> LineKind.BARLINE
        else -> null
    }
}

internal fun AnnotationStroke.meanY(): Float = (points[0].y + points[1].y) / 2f

/** y-Wert der (ggf. schrägen) Linie an der Stelle x. */
internal fun AnnotationStroke.yAt(x: Float): Float {
    val a = points[0]
    val b = points[1]
    val dx = b.x - a.x
    return if (abs(dx) < 0.001f) a.y else a.y + (b.y - a.y) * (x - a.x) / dx
}

/** Neigung eines Taktstrichs: Pixel in x pro Pixel in y (Taktstriche können leicht schräg sein). */
private fun AnnotationStroke.tilt(): Float {
    val dy = points[1].y - points[0].y
    return if (abs(dy) < 0.001f) 0f else ((points[1].x - points[0].x) / dy).coerceIn(-0.2f, 0.2f)
}

/** Schnittpunkt einer geneigten Linie (durch (xm, ymid), Neigung tilt) mit einer Systemkante. */
private fun intersectEdge(edge: AnnotationStroke, xm: Float, ymid: Float, tilt: Float): Pair<Float, Float> {
    var x = xm
    var y = edge.yAt(xm)
    repeat(3) {
        x = xm + tilt * (y - ymid)
        y = edge.yAt(x)
    }
    return x to y
}

private fun lineStroke(kind: LineKind, x1: Float, y1: Float, x2: Float, y2: Float) =
    AnnotationStroke(
        points = listOf(DrawingPoint(x1, y1), DrawingPoint(x2, y2)),
        color = when (kind) {
            LineKind.SYSTEM_EDGE -> LINE_COLOR_SYSTEM_EDGE
            LineKind.PAGE_EDGE -> LINE_COLOR_PAGE_EDGE
            LineKind.BARLINE -> LINE_COLOR_BARLINE
        },
        strokeWidth = if (kind == LineKind.BARLINE) 2.5f else 3.5f,
        isEraser = false
    )

private fun distToSegment(px: Float, py: Float, a: DrawingPoint, b: DrawingPoint): Float {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val len2 = vx * vx + vy * vy
    val t = if (len2 == 0f) 0f else (((px - a.x) * vx + (py - a.y) * vy) / len2).coerceIn(0f, 1f)
    return hypot(px - (a.x + t * vx), py - (a.y + t * vy))
}

/** Systeme = grüne Linien nach y sortiert und paarweise (oben, unten) gruppiert. Liefert Indizes. */
internal fun findSystems(strokes: List<AnnotationStroke>): List<Pair<Int, Int>> =
    strokes.indices
        .filter { strokes[it].lineKind() == LineKind.SYSTEM_EDGE }
        .sortedBy { strokes[it].meanY() }
        .chunked(2)
        .filter { it.size == 2 }
        .map { it[0] to it[1] }

/**
 * Hält das Layout konsistent, nachdem etwas verschoben/hinzugefügt wurde:
 *  - Systemkanten reichen genau von linkem bis rechtem Seitenrand
 *  - Taktstriche gehen exakt von der oberen bis zur unteren Kante "ihres" Systems
 *  - Seitenränder reichen vom ersten bis zum letzten System
 * Die Indizes der Strokes bleiben unverändert.
 */
internal fun normalizeLayout(strokes: List<AnnotationStroke>): List<AnnotationStroke> {
    val out = strokes.toMutableList()
    val systems = findSystems(out)
    if (systems.isEmpty()) return out

    val pageEdges = out.indices.filter { out[it].lineKind() == LineKind.PAGE_EDGE }
    val hasEdges = pageEdges.size >= 2
    if (hasEdges) {
        val xs = pageEdges.flatMap { i -> out[i].points.map { it.x } }
        val left = xs.minOrNull() ?: 0f
        val right = xs.maxOrNull() ?: 0f
        for ((top, bottom) in systems) {
            for (i in listOf(top, bottom)) {
                val s = out[i]
                out[i] = lineStroke(LineKind.SYSTEM_EDGE, left, s.yAt(left), right, s.yAt(right))
            }
        }
    }

    for (i in out.indices) {
        val s = out[i]
        if (s.lineKind() != LineKind.BARLINE) continue
        val xm = (s.points[0].x + s.points[1].x) / 2f
        val midY = s.meanY()
        val tilt = s.tilt()
        val target = systems.minByOrNull { (top, bottom) ->
            abs(midY - (out[top].yAt(xm) + out[bottom].yAt(xm)) / 2f)
        } ?: continue
        val (xTop, yTop) = intersectEdge(out[target.first], xm, midY, tilt)
        val (xBottom, yBottom) = intersectEdge(out[target.second], xm, midY, tilt)
        out[i] = lineStroke(LineKind.BARLINE, xTop, yTop, xBottom, yBottom)
    }

    if (hasEdges) {
        val firstTop = systems.first().first
        val lastBottom = systems.last().second
        for (i in pageEdges) {
            val x = out[i].points[0].x
            out[i] = lineStroke(LineKind.PAGE_EDGE, x, out[firstTop].yAt(x), x, out[lastBottom].yAt(x))
        }
    }
    return out
}

/** Index der Linie, die dem Punkt am nächsten liegt (innerhalb radius), sonst null. */
internal fun hitTestLine(
    strokes: List<AnnotationStroke>,
    x: Float,
    y: Float,
    radius: Float
): Int? {
    var best: Int? = null
    var bestDist = radius
    for (i in strokes.indices) {
        if (strokes[i].lineKind() == null) continue
        val d = distToSegment(x, y, strokes[i].points[0], strokes[i].points[1])
        if (d <= bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

private fun clampShift(delta: Float, lo: Float, hi: Float, limit: Float): Float {
    val minShift = -lo
    val maxShift = max(minShift, limit - hi)
    return delta.coerceIn(minShift, maxShift)
}

/**
 * Verschiebt eine Linie ausgehend vom Zustand VOR dem Ziehen um (dx, dy).
 * Systemkanten bewegen sich nur vertikal, Taktstriche und Seitenränder nur horizontal.
 */
internal fun moveLine(
    strokes: List<AnnotationStroke>,
    index: Int,
    dx: Float,
    dy: Float,
    pageW: Float,
    pageH: Float
): List<AnnotationStroke> {
    val s = strokes.getOrNull(index) ?: return strokes
    val kind = s.lineKind() ?: return strokes
    val moved = when (kind) {
        LineKind.SYSTEM_EDGE -> {
            val d = clampShift(dy, s.points.minOf { it.y }, s.points.maxOf { it.y }, pageH)
            s.points.map { DrawingPoint(it.x, it.y + d) }
        }
        LineKind.BARLINE, LineKind.PAGE_EDGE -> {
            val d = clampShift(dx, s.points.minOf { it.x }, s.points.maxOf { it.x }, pageW)
            s.points.map { DrawingPoint(it.x + d, it.y) }
        }
    }
    val result = strokes.toMutableList()
    result[index] = AnnotationStroke(
        points = moved,
        color = s.color,
        strokeWidth = s.strokeWidth,
        isEraser = s.isEraser
    )
    return normalizeLayout(result)
}

/**
 * Fügt ein System mit der Mitte bei centerY hinzu. Höhe = Median der vorhandenen
 * Systeme. Die neue obere Kante hat den Index strokes.size, die untere strokes.size + 1.
 */
internal fun addSystem(
    strokes: List<AnnotationStroke>,
    centerY: Float,
    pageW: Float,
    pageH: Float
): List<AnnotationStroke> {
    val systems = findSystems(strokes)
    val heights = systems.map { (t, b) -> strokes[b].meanY() - strokes[t].meanY() }.sorted()
    val height = (if (heights.isNotEmpty()) heights[heights.size / 2] else pageW * 0.12f)
        .coerceIn(10f, max(10f, pageH * 0.5f))

    val edgeXs = strokes
        .filter { it.lineKind() == LineKind.PAGE_EDGE }
        .flatMap { s -> s.points.map { it.x } }
    val hasEdges = edgeXs.size >= 4
    val left = edgeXs.minOrNull()?.takeIf { hasEdges } ?: (pageW * 0.04f)
    val right = edgeXs.maxOrNull()?.takeIf { hasEdges } ?: (pageW * 0.96f)

    // Neigung der vorhandenen Systeme übernehmen (schräge Fotos)
    val slope = strokes
        .filter { it.lineKind() == LineKind.SYSTEM_EDGE }
        .map { s ->
            val dx = s.points[1].x - s.points[0].x
            if (abs(dx) < 1f) 0f else (s.points[1].y - s.points[0].y) / dx
        }
        .average()
        .let { if (it.isNaN()) 0f else it.toFloat() }

    val topY = (centerY - height / 2f).coerceIn(0f, max(0f, pageH - height))
    val cx = (left + right) / 2f
    val added = mutableListOf(
        lineStroke(
            LineKind.SYSTEM_EDGE,
            left, topY + slope * (left - cx),
            right, topY + slope * (right - cx)
        ),
        lineStroke(
            LineKind.SYSTEM_EDGE,
            left, topY + height + slope * (left - cx),
            right, topY + height + slope * (right - cx)
        )
    )
    if (!hasEdges) {
        added += lineStroke(LineKind.PAGE_EDGE, left, topY, left, topY + height)
        added += lineStroke(LineKind.PAGE_EDGE, right, topY, right, topY + height)
    }
    return normalizeLayout(strokes + added)
}

/** Fügt einen Taktstrich an x im System hinzu, in das (x, y) fällt. null = kein System getroffen. */
internal fun addBarline(
    strokes: List<AnnotationStroke>,
    x: Float,
    y: Float,
    pageW: Float
): List<AnnotationStroke>? {
    val bx = x.coerceIn(0f, pageW)
    val target = findSystems(strokes).firstOrNull { (t, b) ->
        val top = strokes[t].yAt(bx)
        val bottom = strokes[b].yAt(bx)
        val margin = (bottom - top) * 0.1f
        y >= top - margin && y <= bottom + margin
    } ?: return null
    val tilts = strokes.filter { it.lineKind() == LineKind.BARLINE }.map { it.tilt() }
    val tilt = if (tilts.isEmpty()) 0f else tilts.average().toFloat()
    val pivotY = (strokes[target.first].yAt(bx) + strokes[target.second].yAt(bx)) / 2f
    val (xTop, yTop) = intersectEdge(strokes[target.first], bx, pivotY, tilt)
    val (xBottom, yBottom) = intersectEdge(strokes[target.second], bx, pivotY, tilt)
    return normalizeLayout(strokes + lineStroke(LineKind.BARLINE, xTop, yTop, xBottom, yBottom))
}

/**
 * Taktstrich: nur dieser. Systemkante: das ganze System inklusive seiner Taktstriche.
 * Seitenränder lassen sich nicht einzeln löschen.
 */
internal fun deleteLine(strokes: List<AnnotationStroke>, index: Int): List<AnnotationStroke> {
    val kind = strokes.getOrNull(index)?.lineKind() ?: return strokes
    return when (kind) {
        LineKind.PAGE_EDGE -> strokes
        LineKind.BARLINE -> normalizeLayout(strokes.filterIndexed { i, _ -> i != index })
        LineKind.SYSTEM_EDGE -> {
            val system = findSystems(strokes)
                .firstOrNull { it.first == index || it.second == index } ?: return strokes
            val remove = mutableSetOf(system.first, system.second)
            strokes.forEachIndexed { i, s ->
                if (s.lineKind() == LineKind.BARLINE) {
                    val x = (s.points[0].x + s.points[1].x) / 2f
                    val mid = s.meanY()
                    val top = strokes[system.first].yAt(x)
                    val bottom = strokes[system.second].yAt(x)
                    if (mid >= top - 1f && mid <= bottom + 1f) remove.add(i)
                }
            }
            val remaining = strokes.filterIndexed { i, _ -> i !in remove }
            // Letztes System weg -> auch die Seitenränder entfernen
            if (findSystems(remaining).isEmpty()) {
                remaining.filter { it.lineKind() != LineKind.PAGE_EDGE }
            } else {
                normalizeLayout(remaining)
            }
        }
    }
}

/* ════════════════════════════════════════════════════════════════════
 * TEIL 3: Takt-Extraktion & Abschnitte
 * ════════════════════════════════════════════════════════════════════ */

internal data class MeasureBox(
    val measureIndex: Int,
    val sheetFileId: String,
    val pageIndex: Int,
    val systemIndex: Int,
    val bounds: android.graphics.RectF,
    val leftX: Float,
    val rightX: Float,
    val topY: Float,
    val bottomY: Float
)

private data class EmptyKey(val l: Int, val t: Int, val r: Int, val b: Int, val slope: Int)
private val staffCache = java.util.WeakHashMap<android.graphics.Bitmap, HashMap<EmptyKey, Boolean>>()

/** true, wenn im Kasten Notenlinien liegen. Im Zweifel true, dann bleibt der Takt erhalten. */
private fun hasStaffLines(
    bmp: android.graphics.Bitmap,
    scale: Float,
    box: android.graphics.RectF,
    slope: Float
): Boolean {
    val l = (box.left * scale).toInt().coerceIn(0, bmp.width - 1)
    val r = (box.right * scale).toInt().coerceIn(l + 1, bmp.width)
    val t = (box.top * scale).toInt().coerceIn(0, bmp.height - 1)
    val b = (box.bottom * scale).toInt().coerceIn(t + 1, bmp.height)
    val w = r - l
    val h = b - t
    if (w < 12 || h < 12) return true

    val key = EmptyKey(l, t, r, b, (slope * 1000).toInt())
    synchronized(staffCache) { staffCache[bmp]?.get(key)?.let { return it } }

    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, l, t, w, h)
    val lum = IntArray(px.size) {
        val p = px[it]
        (((p shr 16) and 0xff) * 299 + ((p shr 8) and 0xff) * 587 + (p and 0xff) * 114) / 1000
    }
    // Papierhelligkeit = 90. Perzentil; Linien sind deutlich dunkler
    val hist = IntArray(256)
    for (v in lum) hist[v]++
    var acc = 0
    var paper = 255
    for (v in 0..255) {
        acc += hist[v]
        if (acc >= lum.size * 0.9) { paper = v; break }
    }

    val result = if (paper < 100) {
        true // zu dunkles Foto, nicht beurteilbar
    } else {
        val thr = paper * 0.82f
        val pad = (abs(slope) * w).toInt() + 3
        val proj = IntArray(h + 2 * pad + 2)
        for (y in 0 until h) for (x in 0 until w) {
            if (lum[y * w + x] < thr) {
                val ay = Math.round(y - slope * (x - w / 2f)) + pad
                if (ay in proj.indices) proj[ay]++
            }
        }
        val need = max(8f, w * 0.25f)
        var lines = 0
        var prev = false
        for (i in 0 until proj.size - 1) {
            val on = proj[i] + proj[i + 1] >= need
            if (on && !prev) lines++
            prev = on
        }
        lines >= 2
    }
    synchronized(staffCache) { staffCache.getOrPut(bmp) { HashMap() }[key] = result }
    return result
}

internal fun extractAllMeasures(
    sheetPages: List<SheetPage>,
    pageAnnotations: List<PageAnnotation>,
    displayWidthPx: Float = 0f   // 0 = Prüfung aus
): List<MeasureBox> {
    val result = mutableListOf<MeasureBox>()
    var measureCounter = 1

    for (page in sheetPages) {
        val annotation = pageAnnotations.find {
            it.sheetFileId == page.sheetFileId && it.pageIndex == page.pageIndex
        }
        val strokes = annotation?.strokes ?: emptyList()
        val systems = findSystems(strokes)

        for ((sysIdx, sysPair) in systems.withIndex()) {
            val topStroke = strokes[sysPair.first]
            val bottomStroke = strokes[sysPair.second]

            val sysTopY = min(topStroke.meanY(), bottomStroke.meanY())
            val sysBottomY = max(topStroke.meanY(), bottomStroke.meanY())
            val dxTop = topStroke.points[1].x - topStroke.points[0].x
            val sysSlope = if (abs(dxTop) > 1f) (topStroke.points[1].y - topStroke.points[0].y) / dxTop else 0f

            val pageEdges = strokes.filter { it.lineKind() == LineKind.PAGE_EDGE }
            var rawLeftX = pageEdges.map { s -> min(s.points[0].x, s.points[1].x) }
                .minOrNull() ?: min(topStroke.points.minOf { it.x }, bottomStroke.points.minOf { it.x })
            var rawRightX = pageEdges.map { s -> max(s.points[0].x, s.points[1].x) }
                .maxOrNull() ?: max(topStroke.points.maxOf { it.x }, bottomStroke.points.maxOf { it.x })

            val sysWidth = (rawRightX - rawLeftX).coerceAtLeast(10f)
            // Mindestabstand eines Taktstrichs zum Rand (~ 6% der Systembreite bzw. min. 35px)
            val marginThreshold = (sysWidth * 0.06f).coerceAtLeast(35f)

            // Alle Taktstriche in diesem System holen und nach X sortieren
            val allBarlines = strokes.filter { stroke ->
                stroke.lineKind() == LineKind.BARLINE &&
                        stroke.meanY() >= sysTopY - 15f && stroke.meanY() <= sysBottomY + 15f
            }.map { stroke ->
                (stroke.points[0].x + stroke.points[1].x) / 2f
            }.sorted()

            // Äußere Taktstriche identifizieren (Systemanfang/Ende), die keine Binnentaktstriche sind
            val internalBarlines = mutableListOf<Float>()
            for (bx in allBarlines) {
                if (bx - rawLeftX < marginThreshold) {
                    // Taktstrich direkt am linken Rand -> Systemanfang verschieben
                    rawLeftX = max(rawLeftX, bx)
                } else if (rawRightX - bx < marginThreshold) {
                    // Taktstrich direkt am rechten Rand -> Systemende verschieben
                    rawRightX = min(rawRightX, bx)
                } else {
                    internalBarlines.add(bx)
                }
            }

            // Taktgrenzen bilden und zu nahe beieinander liegende Binnen-Taktstriche filtern
            val finalXList = mutableListOf(rawLeftX)
            for (bx in internalBarlines) {
                val lastX = finalXList.last()
                if (bx - lastX >= marginThreshold) {
                    finalXList.add(bx)
                }
            }
            if (rawRightX - finalXList.last() < marginThreshold && finalXList.size > 1) {
                finalXList[finalXList.lastIndex] = rawRightX
            } else {
                finalXList.add(rawRightX)
            }

            for (m in 0 until finalXList.size - 1) {
                val mLeft = finalXList[m]
                val mRight = finalXList[m + 1]
                if (mRight - mLeft < marginThreshold * 0.5f) continue

                val mTop = min(topStroke.yAt(mLeft), topStroke.yAt(mRight))
                val mBottom = max(bottomStroke.yAt(mLeft), bottomStroke.yAt(mRight))
                val rect = android.graphics.RectF(mLeft, mTop, mRight, mBottom)
                val isEdgeBox = m == 0 || m == finalXList.size - 2
                if (displayWidthPx > 0f && isEdgeBox &&
                    !hasStaffLines(page.bitmap, page.bitmap.width / displayWidthPx, rect, sysSlope)
                ) continue

                result.add(
                    MeasureBox(
                        measureIndex = measureCounter++,
                        sheetFileId = page.sheetFileId,
                        pageIndex = page.pageIndex,
                        systemIndex = sysIdx,
                        bounds = rect,
                        leftX = mLeft,
                        rightX = mRight,
                        topY = mTop,
                        bottomY = mBottom
                    )
                )
            }
        }
    }
    return result
}

internal fun groupContiguousMeasures(selectedBars: Set<Int>): List<IntRange> {
    if (selectedBars.isEmpty()) return emptyList()
    val sorted = selectedBars.sorted()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var end = start

    for (i in 1 until sorted.size) {
        val curr = sorted[i]
        if (curr == end + 1) {
            end = curr
        } else {
            ranges.add(start..end)
            start = curr
            end = curr
        }
    }
    ranges.add(start..end)
    return ranges
}
