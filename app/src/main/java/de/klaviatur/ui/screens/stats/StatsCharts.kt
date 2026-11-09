package de.klaviatur.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klaviatur.R
import de.klaviatur.util.Badge
import de.klaviatur.util.BadgeKind
import de.klaviatur.util.Bucket
import de.klaviatur.util.HeatCell
import de.klaviatur.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// ── Formatierung ──────────────────────────────────────────────────────────────

internal fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"

internal fun formatTrend(percent: Int): String = (if (percent > 0) "+" else "") + "$percent %"

internal fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private fun formatMonth(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

// ── Balkendiagramm ────────────────────────────────────────────────────────────

/** Balkendiagramm für Tages- oder Monatswerte. Ein Tipp auf einen Balken zeigt den genauen Wert. */
@Composable
internal fun BarChart(
    buckets: List<Bucket>,
    locale: Locale,
    valueFormatter: (Int) -> String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 110.dp
) {
    if (buckets.isEmpty()) return
    val max = buckets.maxOf { it.value }.coerceAtLeast(1)
    var selected by remember(buckets) { mutableIntStateOf(buckets.lastIndex) }
    val n = buckets.size
    val labelEvery = when { n <= 12 -> 1; n <= 31 -> 5; else -> 6 }

    Column(modifier) {
        val sel = buckets.getOrNull(selected)
        Text(
            if (sel != null) {
                val whenText = if (sel.monthly) formatMonth(sel.start, locale) else formatDate(sel.start, locale)
                "$whenText: ${valueFormatter(sel.value)}"
            } else "",
            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(if (n > 12) 2.dp else 5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEachIndexed { i, b ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { selected = i },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val frac = b.value.toFloat() / max
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (b.value > 0) Modifier.fillMaxHeight(frac) else Modifier.height(2.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                when {
                                    b.value == 0 -> BorderColor
                                    i == selected -> Amber
                                    else -> ForestGreen
                                }
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (n > 12) 2.dp else 5.dp)) {
            buckets.forEachIndexed { i, b ->
                val show = (n - 1 - i) % labelEvery == 0   // letzter Balken trägt immer ein Label
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (show) {
                        Text(
                            bucketLabel(b, n, locale),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

private fun bucketLabel(b: Bucket, total: Int, locale: Locale): String = when {
    b.monthly && total > 12 -> b.start.format(DateTimeFormatter.ofPattern("MMM yy", locale))
    b.monthly -> b.start.month.getDisplayName(TextStyle.SHORT, locale)
    total <= 7 -> b.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    else -> b.start.dayOfMonth.toString()
}

// ── Heatmap ───────────────────────────────────────────────────────────────────

private fun heatColor(minutes: Int, max: Int): Color =
    if (minutes <= 0) PaperBeige
    else lerp(ForestGreenBg, ForestGreen, (minutes.toFloat() / max.coerceAtLeast(1)).coerceIn(0.15f, 1f))

/** Kalender-Heatmap: Spalten = Wochen, Zeilen = Mo–So. Ein Tipp auf ein Feld zeigt den Tag. */
@Composable
internal fun Heatmap(
    weeks: List<List<HeatCell?>>,
    locale: Locale,
    legendLess: String,
    legendMore: String,
    modifier: Modifier = Modifier
) {
    if (weeks.isEmpty()) return
    val max = weeks.flatten().maxOfOrNull { it?.minutes ?: 0 }?.coerceAtLeast(1) ?: 1
    var selected by remember(weeks) { mutableStateOf<HeatCell?>(null) }
    val gap = 3.dp
    val labelWidth = 26.dp

    Column(modifier) {
        Text(
            selected?.let { "${formatDate(it.date, locale)}: ${formatMinutes(it.minutes)}" } ?: "",
            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen),
            modifier = Modifier.padding(bottom = 6.dp).heightIn(min = 16.dp)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - labelWidth - gap * weeks.size) / weeks.size
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // Monatslabels
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Spacer(Modifier.width(labelWidth))
                    weeks.forEachIndexed { w, col ->
                        val first = col.firstOrNull()?.date ?: weeks.getOrNull(w)?.firstOrNull { it != null }?.date
                        val prevMonth = weeks.getOrNull(w - 1)?.firstOrNull()?.date?.month
                        Box(Modifier.width(cell)) {
                            if (first != null && (w == 0 || first.month != prevMonth)) {
                                Text(
                                    first.month.getDisplayName(TextStyle.SHORT, locale),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    softWrap = false,
                                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                                )
                            }
                        }
                    }
                }
                // 7 Zeilen (Mo–So)
                for (row in 0 until 7) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(labelWidth).height(cell), contentAlignment = Alignment.CenterStart) {
                            if (row % 2 == 0) {
                                Text(
                                    DayOfWeek.of(row + 1).getDisplayName(TextStyle.SHORT, locale),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    softWrap = false
                                )
                            }
                        }
                        weeks.forEach { col ->
                            val c = col.getOrNull(row)
                            Box(
                                Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (c == null) Color.Transparent else heatColor(c.minutes, max))
                                    .then(if (c != null) Modifier.clickable { selected = c } else Modifier)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(legendLess, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            listOf(0, 25, 50, 75, 100).forEach { pct ->
                Box(
                    Modifier.padding(horizontal = 1.5.dp).size(11.dp).clip(RoundedCornerShape(2.dp))
                        .background(heatColor(pct, 100))
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(legendMore, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Ring & Donut ──────────────────────────────────────────────────────────────

@Composable
internal fun ProgressRing(
    fraction: Float,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    color: Color = ForestGreen,
    track: Color = PaperBeige,
    content: @Composable () -> Unit = {}
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Ringdiagramm aus beliebig vielen Segmenten (Wert, Farbe). */
@Composable
internal fun DonutChart(
    segments: List<Pair<Float, Color>>,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(PaperBeige, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (total > 0f) {
                var start = -90f
                segments.filter { it.first > 0f }.forEach { (value, color) ->
                    val sweep = 360f * value / total
                    drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(s))
                    start += sweep
                }
            }
        }
        content()
    }
}

// ── Gestapelter Balken ────────────────────────────────────────────────────────

@Composable
internal fun StackedBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val visible = segments.filter { it.first > 0f }
    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(PaperBeige)) {
        visible.forEach { (value, color) -> Box(Modifier.weight(value).fillMaxHeight().background(color)) }
    }
}

@Composable
internal fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ── Matrix (Epoche × Schwierigkeit) ───────────────────────────────────────────

@Composable
internal fun CountMatrix(
    rowLabels: List<String>,
    columnLabels: List<String>,
    values: List<List<Int>>,
    modifier: Modifier = Modifier
) {
    val max = values.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(96.dp))
            columnLabels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        rowLabels.forEachIndexed { r, label ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.width(96.dp))
                values[r].forEach { v ->
                    Box(
                        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (v == 0) PaperBeige else lerp(AmberBg, Amber, (v.toFloat() / max).coerceIn(0.2f, 1f))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (v > 0) Text("$v", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                    }
                }
            }
        }
    }
}

// ── Abzeichen ─────────────────────────────────────────────────────────────────

@Composable
private fun badgeTitle(b: Badge): String = when (b.kind) {
    BadgeKind.STREAK -> stringResource(R.string.badge_streak, b.target)
    BadgeKind.HOURS -> stringResource(R.string.badge_hours, b.target)
    BadgeKind.SESSIONS -> stringResource(R.string.badge_sessions, b.target)
    BadgeKind.REPERTOIRE ->
        if (b.target == 1) stringResource(R.string.badge_repertoire_first) else stringResource(R.string.badge_repertoire, b.target)
    BadgeKind.SAFE_SECTION -> stringResource(R.string.badge_safe_section)
    BadgeKind.MARATHON -> stringResource(R.string.badge_marathon, b.target)
}

private fun badgeEmoji(kind: BadgeKind): String = when (kind) {
    BadgeKind.STREAK -> "🔥"
    BadgeKind.HOURS -> "⏳"
    BadgeKind.SESSIONS -> "🎹"
    BadgeKind.REPERTOIRE -> "🏆"
    BadgeKind.SAFE_SECTION -> "✅"
    BadgeKind.MARATHON -> "🏃"
}

@Composable
internal fun BadgeGrid(badges: List<Badge>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge -> BadgeTile(badge, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (badge.unlocked) ForestGreenBg else PaperBeige
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                badgeEmoji(badge.kind),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alpha(if (badge.unlocked) 1f else 0.3f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                badgeTitle(badge),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (badge.unlocked) ForestGreenText else TextMuted, fontSize = 10.sp
                ),
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
            if (!badge.unlocked) {
                Text(
                    "${badge.current}/${badge.target}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = TextHint)
                )
            }
        }
    }
}
