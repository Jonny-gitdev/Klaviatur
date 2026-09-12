package de.klaviatur.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceSection
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.data.model.ProgressLevel
import de.klaviatur.ui.theme.*

// ── Status badge ──────────────────────────────────────────────────────────────

@Composable
fun StatusBadge(status: PieceStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        PieceStatus.NEW        -> Purple50 to PurpleText
        PieceStatus.LEARNING   -> AmberBg  to AmberText
        PieceStatus.POLISHING  -> Blue50   to BlueText
        PieceStatus.REPERTOIRE -> ForestGreenBg to ForestGreenText
    }
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(20.dp),
        color     = bg,
        tonalElevation = 0.dp
    ) {
        Text(
            text  = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600, color = fg),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

// ── Difficulty dots ───────────────────────────────────────────────────────────

@Composable
fun DifficultyDots(difficulty: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < difficulty) Amber else BorderColor)
            )
        }
    }
}

// ── Progress bar (segmented) ──────────────────────────────────────────────────

@Composable
fun BarProgress(sections: List<PieceSection>, totalBars: Int, modifier: Modifier = Modifier) {
    if (totalBars <= 0) return

    val safeBars = sections.filter { it.level == ProgressLevel.SAFE }.sumOf { it.endBar - it.startBar + 1 }
    val safeFrac = (safeBars.toFloat() / totalBars).coerceIn(0f, 1f)

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Fortschritt", style = MaterialTheme.typography.bodySmall)
            Text("${(safeFrac * 100).toInt()}% sicher", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            // Background (Not learned)
            drawRect(color = LevelGray)

            sections.forEach { section ->
                val start = ((section.startBar - 1).toFloat() / totalBars).coerceIn(0f, 1f) * size.width
                val end = (section.endBar.toFloat() / totalBars).coerceIn(0f, 1f) * size.width
                if (end > start) {
                    drawRect(
                        color = Color(section.level.color),
                        topLeft = Offset(start, 0f),
                        size = Size(end - start, size.height)
                    )
                }
            }
        }
    }
}

// ── Stat tile ─────────────────────────────────────────────────────────────────

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        color     = PaperWhite,
        tonalElevation = 0.dp,
        border    = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge.copy(color = ForestGreen))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text(actionLabel, fontSize = 12.sp, color = ForestGreen, fontWeight = FontWeight.W500)
            }
        }
    }
}

// ── Piece card ────────────────────────────────────────────────────────────────

@Composable
fun PieceCard(piece: Piece, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        color     = PaperWhite,
        tonalElevation = 0.dp,
        border    = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(piece.title, style = MaterialTheme.typography.titleMedium)
                    Text(piece.composer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                StatusBadge(piece.status)
            }

            if (piece.totalBars > 0) {
                Spacer(Modifier.height(10.dp))
                BarProgress(piece.sections, piece.totalBars)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (piece.targetBpm > 0) {
                    MetaChip("♩ ${piece.targetBpm} BPM")
                }
                if (piece.keySignature.isNotBlank()) {
                    MetaChip("♪ ${piece.keySignature}")
                }
                DifficultyDots(piece.difficulty)
            }
        }
    }
}

@Composable
fun MetaChip(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
}
