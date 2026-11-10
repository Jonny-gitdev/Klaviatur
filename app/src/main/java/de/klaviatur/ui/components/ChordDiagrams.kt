package de.klaviatur.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaviatur.R
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.ui.theme.*
import de.klaviatur.util.ChordTheory
import de.klaviatur.util.FretVoicing
import de.klaviatur.util.PianoVoicing
import kotlinx.coroutines.launch

/** Bottom Sheet mit Griffbild(ern) für den angetippten Akkord – je nach Instrument. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordDiagramSheet(
    chord: String,
    instrument: ChordInstrument,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PaperWhite
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(chord, style = MaterialTheme.typography.headlineMedium)
            Text(instrument.label(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(12.dp))

            if (instrument.id == ChordInstrument.PIANO.id) {
                val voicings = remember(chord) { ChordTheory.pianoVoicings(chord) }
                val flat = remember(chord) { ChordTheory.split(chord)?.accidental == 'b' }
                VoicingPager(voicings.size) { i -> PianoChordDiagram(voicings[i], flat) }
            } else if (instrument.id == ChordInstrument.GUITAR.id || instrument.id == ChordInstrument.UKULELE.id) {
                val voicings = remember(chord, instrument) { ChordTheory.fretVoicings(chord, instrument) }
                VoicingPager(voicings.size) { i -> FretChordDiagram(voicings[i]) }
            } else {
                val voicings = remember(chord) { ChordTheory.pianoVoicings(chord) }
                val flat = remember(chord) { ChordTheory.split(chord)?.accidental == 'b' }
                VoicingPager(voicings.size) { i -> PianoChordDiagram(voicings[i], flat) }
            }
        }
    }
}

@Composable
private fun VoicingPager(count: Int, content: @Composable (Int) -> Unit) {
    if (count == 0) {
        Text(
            stringResource(R.string.chord_no_diagram),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(vertical = 32.dp)
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { count })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
            enabled = page > 0
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.chord_prev_variant)) }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { p ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content(p) }
        }

        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
            enabled = page < count - 1
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.chord_next_variant)) }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.chord_variant, page + 1, count),
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted
    )
    if (count > 1) {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (i == page) ForestGreen else BorderColor)
                )
            }
        }
    }
}

// ── Gitarre / Ukulele ────────────────────────────────────────────────────────

@Composable
private fun FretChordDiagram(v: FretVoicing) {
    val strings = v.frets.size
    val fretRows = 5
    val ink = TextPrimary
    val grid = TextMuted
    val fretLabel = if (v.baseFret > 1) stringResource(R.string.chord_fret_position, v.baseFret) else null

    Canvas(
        Modifier
            .width(if (strings > 4) 220.dp else 180.dp)
            .height(230.dp)
    ) {
        val sidePad = 34.dp.toPx()
        val topPad = 38.dp.toPx()
        val bottomPad = 8.dp.toPx()
        val gridW = size.width - 2 * sidePad
        val gridH = size.height - topPad - bottomPad
        val stringGap = gridW / (strings - 1)
        val rowH = gridH / fretRows
        val dotR = minOf(stringGap, rowH) * 0.36f

        // Sattel (dick) bzw. oberste Linie
        val nutStroke = if (v.baseFret == 1) 5.dp.toPx() else 1.5.dp.toPx()
        drawLine(ink, Offset(sidePad, topPad), Offset(sidePad + gridW, topPad), nutStroke)
        for (r in 1..fretRows) {
            val y = topPad + r * rowH
            drawLine(grid, Offset(sidePad, y), Offset(sidePad + gridW, y), 1.5.dp.toPx())
        }
        for (s in 0 until strings) {
            val x = sidePad + s * stringGap
            drawLine(grid, Offset(x, topPad), Offset(x, topPad + fretRows * rowH), 1.5.dp.toPx())
        }

        // Barré
        val barre = v.barreFret
        if (barre != null) {
            val y = topPad + (barre - v.baseFret + 0.5f) * rowH
            drawRoundRect(
                color = ink,
                topLeft = Offset(sidePad + v.barreFrom * stringGap - dotR, y - dotR),
                size = Size((v.barreTo - v.barreFrom) * stringGap + 2 * dotR, 2 * dotR),
                cornerRadius = CornerRadius(dotR)
            )
        }

        // Punkte, offene und gedämpfte Saiten
        val markerY = topPad - 16.dp.toPx()
        val markerR = 6.dp.toPx()
        v.frets.forEachIndexed { s, f ->
            val x = sidePad + s * stringGap
            when {
                f < 0 -> {
                    val d = markerR * 0.8f
                    val w = 2.dp.toPx()
                    drawLine(ink, Offset(x - d, markerY - d), Offset(x + d, markerY + d), w, StrokeCap.Round)
                    drawLine(ink, Offset(x - d, markerY + d), Offset(x + d, markerY - d), w, StrokeCap.Round)
                }
                f == 0 -> drawCircle(ink, markerR, Offset(x, markerY), style = Stroke(2.dp.toPx()))
                else -> {
                    val y = topPad + (f - v.baseFret + 0.5f) * rowH
                    drawCircle(ink, dotR, Offset(x, y))
                }
            }
        }

        if (fretLabel != null) {
            val paint = Paint().apply {
                color = grid.toArgb()
                textSize = 13.sp.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            drawContext.canvas.nativeCanvas.drawText(fretLabel, sidePad - 8.dp.toPx(), topPad + rowH * 0.5f + 5.dp.toPx(), paint)
        }
    }
}

// ── Klavier ──────────────────────────────────────────────────────────────────

private val WHITE_OFFSETS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

@Composable
private fun PianoChordDiagram(v: PianoVoicing, preferFlat: Boolean) {
    val pressed = v.notes.toSet()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val whiteCount = v.octaves * 7 + 1
            val ww = size.width / whiteCount
            val wh = size.height
            val bw = ww * 0.62f
            val bh = wh * 0.62f
            val border = BorderColor

            for (i in 0 until whiteCount) {
                val semis = (i / 7) * 12 + WHITE_OFFSETS[i % 7]
                val x = i * ww
                drawRect(Color.White, Offset(x, 0f), Size(ww, wh))
                drawRect(border, Offset(x, 0f), Size(ww, wh), style = Stroke(1.dp.toPx()))
                if (semis in pressed) {
                    val isRoot = semis == v.rootNote
                    drawCircle(if (isRoot) Amber else ForestGreen, ww * 0.3f, Offset(x + ww / 2, wh - ww * 0.55f))
                }
            }
            for (i in 0 until whiteCount - 1) {
                val k = i % 7
                if (k == 2 || k == 6) continue // kein schwarzer Ton nach E und H
                val semis = (i / 7) * 12 + WHITE_OFFSETS[k] + 1
                val x = (i + 1) * ww - bw / 2
                drawRect(Color(0xFF222222), Offset(x, 0f), Size(bw, bh))
                if (semis in pressed) {
                    val isRoot = semis == v.rootNote
                    drawCircle(if (isRoot) Amber else ForestGreen, bw * 0.34f, Offset(x + bw / 2, bh - bw * 0.55f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            v.notes.joinToString(" · ") { ChordTheory.noteName(it % 12, preferFlat) },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
