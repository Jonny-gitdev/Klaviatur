package de.klaviatur.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.R
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.data.model.ProgressLevel
import de.klaviatur.ui.components.localizedLabel
import de.klaviatur.ui.screens.repertoire.RepertoireViewModel
import de.klaviatur.ui.screens.sessions.SessionViewModel
import de.klaviatur.ui.theme.*
import de.klaviatur.util.AppSettings
import de.klaviatur.util.StatsCalculator
import de.klaviatur.util.StatsRange
import de.klaviatur.util.WeekdayStat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repertoireVm: RepertoireViewModel = hiltViewModel(),
    sessionVm: SessionViewModel = hiltViewModel()
) {
    val pieces by repertoireVm.allPieces.collectAsState()
    val sessions by sessionVm.sessions.collectAsState()
    val totalMinutes by sessionVm.totalMinutes.collectAsState()
    val streak by sessionVm.streak.collectAsState()

    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }
    var rangeIdx by rememberSaveable { mutableStateOf(1) }
    val range = StatsRange.entries[rangeIdx]
    var showGoalDialog by remember { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }

    // ── Berechnungen ──────────────────────────────────────────────────────────
    val rangeSessions = remember(sessions, range) { StatsCalculator.inRange(sessions, range, today) }
    val kpis = remember(sessions, range) { StatsCalculator.kpis(sessions, range, today) }
    val buckets = remember(sessions, range) { StatsCalculator.activityBuckets(sessions, range, today) }
    val weekdays = remember(sessions, range) { StatsCalculator.weekdayStats(sessions, range, today) }
    val heatmap = remember(sessions) { StatsCalculator.heatmap(sessions, today) }
    val longestStreak = remember(sessions) { StatsCalculator.longestStreak(sessions) }
    val monthDays = remember(sessions) { StatsCalculator.daysPracticedInMonth(sessions, today) }
    val minutesThisWeek = remember(sessions) { StatsCalculator.minutesThisWeek(sessions, today) }
    val weekTrend = remember(sessions) { StatsCalculator.weekTrend(sessions, today) }
    val usage = remember(rangeSessions, pieces) { StatsCalculator.pieceUsage(rangeSessions, pieces) }
    val neglected = remember(pieces) { StatsCalculator.neglectedPieces(pieces, today) }
    val levelDist = remember(pieces) { StatsCalculator.levelDistribution(pieces) }
    val mastery = remember(pieces) { StatsCalculator.pieceMastery(pieces) }
    val scope = remember(pieces) { StatsCalculator.repertoireScope(pieces) }
    val added = remember(pieces) { StatsCalculator.addedPerMonth(pieces, today) }
    val epochMatrix = remember(pieces) { StatsCalculator.epochDifficultyMatrix(pieces) }
    val composerStatus = remember(pieces) { StatsCalculator.composerStatus(pieces) }
    val badges = remember(sessions, pieces) { StatsCalculator.badges(sessions, pieces) }
    val records = remember(sessions) { StatsCalculator.records(sessions) }

    val statusCounts = PieceStatus.entries.associateWith { s -> pieces.count { it.status == s } }
    val epochCounts = Epoch.entries.associateWith { e -> pieces.count { it.epoch == e } }
    val avgDifficulty = if (pieces.isEmpty()) 0f else pieces.sumOf { it.difficulty }.toFloat() / pieces.size
    val hasData = pieces.isNotEmpty() || sessions.isNotEmpty()

    val rangeLabels = listOf(
        stringResource(R.string.stats_range_week), stringResource(R.string.stats_range_month),
        stringResource(R.string.stats_range_year), stringResource(R.string.stats_range_all)
    )
    val piecesFmt = stringResource(R.string.pieces_count)
    val pieceSingularFmt = stringResource(R.string.piece_count_singular)
    fun pieceCountText(n: Int) = if (n == 1) pieceSingularFmt.format(n) else piecesFmt.format(n)

    val sectionBehaviorTitle = stringResource(R.string.stats_section_behavior)
    val sectionPiecesTitle = stringResource(R.string.stats_section_pieces)
    val sectionAchievementsTitle = stringResource(R.string.stats_section_achievements)

    if (showGoalDialog) {
        WeeklyGoalDialog(
            current = AppSettings.weeklyGoalMinutes,
            onDismiss = { showGoalDialog = false },
            onConfirm = { AppSettings.weeklyGoalMinutes = it; showGoalDialog = false }
        )
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(if (editMode) R.string.stats_edit_done else R.string.stats_edit),
                            tint = if (editMode) ForestGreen else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Kachel nur im Bearbeiten-Modus (dann ausgegraut) oder wenn sie angehakt ist
            fun tile(tile: StatTile, content: @Composable () -> Unit) {
                if (editMode || AppSettings.isStatTileVisible(tile)) {
                    item(key = tile.name) {
                        EditableTile(
                            editMode = editMode,
                            visible = AppSettings.isStatTileVisible(tile),
                            onToggle = { AppSettings.toggleStatTile(tile) },
                            content = content
                        )
                    }
                }
            }
            fun sectionHeader(section: StatSection, title: String) {
                if (editMode || StatTile.entries.any { it.section == section && AppSettings.isStatTileVisible(it) }) {
                    item(key = "header_${section.name}") { SectionHeader(title) }
                }
            }

            if (editMode) {
                item(key = "edit_hint") {
                    Text(stringResource(R.string.stats_edit_hint), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Overview tiles
            if (editMode || StatTile.entries.any { it.section == StatSection.OVERVIEW && AppSettings.isStatTileVisible(it) }) {
                item(key = "overview") {
                    val cards = listOf(
                        OverviewCard(StatTile.OVERVIEW_PIECES, "🎹", "${pieces.size}", stringResource(R.string.pieces_stat)),
                        OverviewCard(StatTile.OVERVIEW_TIME, "⏱", "${totalMinutes / 60}h ${totalMinutes % 60}m", stringResource(R.string.practice_time_stat)),
                        OverviewCard(StatTile.OVERVIEW_STREAK, "🔥", "$streak", stringResource(R.string.streak_label)),
                        OverviewCard(StatTile.OVERVIEW_SESSIONS, "📋", "${sessions.size}", stringResource(R.string.sessions_label))
                    ).filter { editMode || AppSettings.isStatTileVisible(it.tile) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cards.chunked(2).forEach { rowCards ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowCards.forEach { c ->
                                    EditableTile(
                                        editMode = editMode,
                                        visible = AppSettings.isStatTileVisible(c.tile),
                                        onToggle = { AppSettings.toggleStatTile(c.tile) },
                                        modifier = Modifier.weight(1f)
                                    ) { StatCard(c.emoji, c.value, c.label, Modifier.fillMaxWidth()) }
                                }
                                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (!hasData) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_stats_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ═══ Übungsverhalten ═════════════════════════════════════════════
            if (hasData) {
                sectionHeader(StatSection.BEHAVIOR, sectionBehaviorTitle)

                if (editMode || listOf(StatTile.KPIS, StatTile.ACTIVITY, StatTile.WEEKDAYS, StatTile.MOST_PRACTICED).any { AppSettings.isStatTileVisible(it) }) item(key = "range_chips") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsRange.entries.forEachIndexed { i, _ ->
                            FilterChip(
                                selected = rangeIdx == i,
                                onClick = { rangeIdx = i },
                                label = { Text(rangeLabels[i]) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreenBg,
                                    selectedLabelColor = ForestGreenText
                                )
                            )
                        }
                    }
                }

                // Wochenziel
                tile(StatTile.WEEKLY_GOAL) {
                    val goal = AppSettings.weeklyGoalMinutes
                    StatSection(stringResource(R.string.weekly_goal_title)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProgressRing(
                                fraction = if (goal > 0) minutesThisWeek.toFloat() / goal else 0f,
                                size = 88.dp, stroke = 10.dp
                            ) {
                                Text(
                                    if (goal > 0) "${minutesThisWeek * 100 / goal} %" else "–",
                                    style = MaterialTheme.typography.titleMedium.copy(color = ForestGreen)
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (goal > 0) {
                                    Text("${formatMinutes(minutesThisWeek)} / ${formatMinutes(goal)}", style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.weekly_goal_this_week), style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(stringResource(R.string.weekly_goal_none), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    weekTrend.percent?.let {
                                        stringResource(R.string.stats_trend_week, formatMinutes(weekTrend.thisWeekMinutes), formatTrend(it))
                                    } ?: stringResource(R.string.stats_last7, formatMinutes(weekTrend.thisWeekMinutes)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { showGoalDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                    Text(
                                        if (goal > 0) stringResource(R.string.action_edit) else stringResource(R.string.weekly_goal_set),
                                        color = ForestGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Kennzahlen
                tile(StatTile.KPIS) {
                    StatSection(stringResource(R.string.stats_kpis_title)) {
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat(formatMinutes(kpis.totalMinutes), stringResource(R.string.kpi_total_time), Modifier.weight(1f))
                            MiniStat("${kpis.sessionCount}", stringResource(R.string.sessions_label), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat("${kpis.activeDays}", stringResource(R.string.kpi_active_days), Modifier.weight(1f))
                            MiniStat(formatMinutes(kpis.avgSessionMinutes), stringResource(R.string.kpi_avg_session), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat(formatMinutes(kpis.longestSessionMinutes), stringResource(R.string.kpi_longest_session), Modifier.weight(1f))
                            MiniStat(String.format(locale, "%.1f", kpis.sessionsPerWeek), stringResource(R.string.kpi_sessions_per_week), Modifier.weight(1f))
                        }
                        kpis.trendPercent?.let { trend ->
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.stats_trend_previous, formatTrend(trend)),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (trend >= 0) ForestGreen else MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }

                // Serien
                tile(StatTile.STREAKS) {
                    StatSection(stringResource(R.string.stats_streaks_title)) {
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat("$streak 🔥", stringResource(R.string.streak_current), Modifier.weight(1f))
                            MiniStat("$longestStreak 🏅", stringResource(R.string.streak_longest), Modifier.weight(1f))
                            MiniStat("${monthDays.first} / ${monthDays.second}", stringResource(R.string.streak_month_days), Modifier.weight(1f))
                        }
                    }
                }

                // Aktivität (Tage oder Monate)
                tile(StatTile.ACTIVITY) {
                    val monthly = buckets.firstOrNull()?.monthly == true
                    StatSection(stringResource(if (monthly) R.string.chart_activity_monthly else R.string.chart_activity_daily)) {
                        BarChart(buckets = buckets, locale = locale, valueFormatter = { formatMinutes(it) })
                    }
                }

                // Heatmap
                tile(StatTile.HEATMAP) {
                    StatSection(stringResource(R.string.heatmap_title)) {
                        Heatmap(
                            weeks = heatmap, locale = locale,
                            legendLess = stringResource(R.string.heatmap_less),
                            legendMore = stringResource(R.string.heatmap_more)
                        )
                    }
                }

                // Wochentage
                tile(StatTile.WEEKDAYS) {
                    StatSection(stringResource(R.string.weekday_title)) {
                        WeekdayBars(weekdays, locale)
                    }
                }
            }

            // ═══ Stücke ══════════════════════════════════════════════════════
            if (pieces.isNotEmpty()) {
                sectionHeader(StatSection.PIECES, sectionPiecesTitle)

                // Meistgeübte Stücke
                tile(StatTile.MOST_PRACTICED) {
                    StatSection("${stringResource(R.string.most_practiced_title)} · ${rangeLabels[rangeIdx]}") {
                        if (usage.isEmpty()) {
                            Text(stringResource(R.string.most_practiced_empty), style = MaterialTheme.typography.bodySmall)
                        } else {
                            val top = usage.first().sessionCount.coerceAtLeast(1)
                            usage.forEachIndexed { i, u ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall.copy(color = TextHint), modifier = Modifier.width(18.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(u.piece.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(u.piece.composer, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                        Text(
                                            stringResource(R.string.most_practiced_detail, u.sessionCount, formatMinutes(u.approxMinutes)),
                                            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen)
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    StackedBar(listOf(u.sessionCount.toFloat() to ForestGreen, (top - u.sessionCount).toFloat() to PaperBeige), height = 5.dp)
                                }
                            }
                        }
                    }
                }

                // Vernachlässigte Stücke
                tile(StatTile.NEGLECTED) {
                    StatSection(stringResource(R.string.neglected_title)) {
                        if (neglected.isEmpty()) {
                            Text(stringResource(R.string.neglected_none), style = MaterialTheme.typography.bodySmall)
                        } else {
                            neglected.forEachIndexed { i, n ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(n.piece.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(n.piece.composer, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                    Text(
                                        if (n.neverPracticed) stringResource(R.string.neglected_never)
                                        else stringResource(R.string.neglected_days, n.daysSince),
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                                    )
                                }
                                if (i < neglected.lastIndex) HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Section-Fortschritt
                tile(StatTile.SECTION_PROGRESS) {
                    StatSection(stringResource(R.string.section_progress_title)) {
                        val totalBars = levelDist.values.sum()
                        if (totalBars == 0) {
                            Text(stringResource(R.string.section_progress_empty), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                DonutChart(
                                    segments = ProgressLevel.entries.map { (levelDist[it] ?: 0).toFloat() to Color(it.color) },
                                    size = 112.dp, stroke = 16.dp
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$totalBars", style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.section_progress_bars), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProgressLevel.entries.forEach { lvl ->
                                        val pct = (levelDist[lvl] ?: 0) * 100 / totalBars
                                        LegendDot(Color(lvl.color), "${lvl.localizedLabel()} · $pct %")
                                    }
                                }
                            }
                            if (mastery.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.section_progress_per_piece).uppercase(), style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(8.dp))
                                mastery.forEach { m ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(m.piece.title, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary), maxLines = 1, modifier = Modifier.weight(1f))
                                            Text("${(m.score * 100).toInt()} %", style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        StackedBar(ProgressLevel.entries.map { (m.barsByLevel[it] ?: 0).toFloat() to Color(it.color) }, height = 6.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Repertoire-Umfang
                tile(StatTile.SCOPE) {
                    StatSection(stringResource(R.string.scope_title)) {
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat("${scope.totalBars}", stringResource(R.string.scope_bars), Modifier.weight(1f))
                            MiniStat("${scope.keyCount}", stringResource(R.string.scope_keys), Modifier.weight(1f))
                            MiniStat("${scope.tags.size}", stringResource(R.string.scope_tags), Modifier.weight(1f))
                        }
                        if (scope.tags.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            val maxTag = scope.tags.first().second
                            scope.tags.take(6).forEach { (tag, count) ->
                                StatusBar(label = tag, count = count, fraction = count.toFloat() / maxTag, color = Amber)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Neue Stücke pro Monat
                tile(StatTile.ADDED_PER_MONTH) {
                    StatSection(stringResource(R.string.added_per_month_title)) {
                        BarChart(buckets = added, locale = locale, valueFormatter = { pieceCountText(it) }, chartHeight = 80.dp)
                    }
                }

                // Status distribution
                tile(StatTile.STATUS) {
                    StatSection(stringResource(R.string.status_distribution)) {
                        PieceStatus.entries.forEach { status ->
                            val count = statusCounts[status] ?: 0
                            val frac = count.toFloat() / pieces.size
                            StatusBar(
                                label = status.localizedLabel(),
                                count = count,
                                fraction = frac,
                                color = statusColor(status)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Epoch distribution
                val usedEpochs = epochCounts.filter { it.value > 0 }
                if (usedEpochs.isNotEmpty()) {
                    tile(StatTile.EPOCHS) {
                        StatSection(stringResource(R.string.epochs)) {
                            usedEpochs.entries.sortedByDescending { it.value }.forEach { (epoch, count) ->
                                StatusBar(
                                    label = epoch.localizedLabel(),
                                    count = count,
                                    fraction = count.toFloat() / pieces.size,
                                    color = Amber
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Difficulty distribution
                tile(StatTile.DIFFICULTY) {
                    StatSection(stringResource(R.string.difficulty)) {
                        (1..5).forEach { d ->
                            val count = pieces.count { it.difficulty == d }
                            StatusBar(
                                label = "★".repeat(d),
                                count = count,
                                fraction = if (pieces.isNotEmpty()) count.toFloat() / pieces.size else 0f,
                                color = Amber
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.avg_difficulty, avgDifficulty),
                            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen)
                        )
                    }
                }

                // Epoche × Schwierigkeit
                if (epochMatrix.isNotEmpty()) {
                    tile(StatTile.EPOCH_MATRIX) {
                        StatSection(stringResource(R.string.matrix_epoch_difficulty)) {
                            val rows = epochMatrix.entries.sortedBy { it.key.ordinal }
                            CountMatrix(
                                rowLabels = rows.map { it.key.localizedLabel() },
                                columnLabels = (1..5).map { "★$it" },
                                values = rows.map { it.value.toList() }
                            )
                        }
                    }
                }

                // Top composers
                tile(StatTile.TOP_COMPOSERS) {
                    val composerCounts = pieces.groupBy { it.composer }
                        .mapValues { it.value.size }
                        .entries.sortedByDescending { it.value }
                        .take(5)
                    StatSection(stringResource(R.string.top_composers)) {
                        composerCounts.forEachIndexed { i, (composer, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextHint),
                                        modifier = Modifier.width(16.dp)
                                    )
                                    Text(composer, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    pieceCountText(count),
                                    style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen)
                                )
                            }
                            if (i < composerCounts.lastIndex)
                                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        }
                    }
                }

                // Komponist × Status
                if (composerStatus.isNotEmpty()) {
                    tile(StatTile.COMPOSER_STATUS) {
                        StatSection(stringResource(R.string.matrix_composer_status)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PieceStatus.entries.forEach { LegendDot(statusColor(it), it.localizedLabel()) }
                            }
                            Spacer(Modifier.height(10.dp))
                            composerStatus.forEach { c ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(c.composer, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary), maxLines = 1, modifier = Modifier.weight(1f))
                                        Text("${c.total}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    StackedBar(PieceStatus.entries.map { (c.byStatus[it] ?: 0).toFloat() to statusColor(it) }, height = 8.dp)
                                }
                            }
                        }
                    }
                }
            }

            // ═══ Erfolge ═════════════════════════════════════════════════════
            if (hasData) {
                sectionHeader(StatSection.ACHIEVEMENTS, sectionAchievementsTitle)

                if (sessions.isNotEmpty()) {
                    tile(StatTile.RECORDS) {
                        StatSection(stringResource(R.string.records_title)) {
                            RecordRow(stringResource(R.string.record_longest_session),
                                records.longestSessionMinutes?.let { formatMinutes(it) },
                                records.longestSessionDate?.let { formatDate(it, locale) })
                            RecordRow(stringResource(R.string.record_best_day),
                                records.bestDayMinutes?.let { formatMinutes(it) },
                                records.bestDayDate?.let { formatDate(it, locale) })
                            RecordRow(stringResource(R.string.record_best_week),
                                records.bestWeekMinutes?.let { formatMinutes(it) },
                                records.bestWeekStart?.let { stringResource(R.string.record_week_of, formatDate(it, locale)) })
                            RecordRow(stringResource(R.string.record_longest_streak), "${records.longestStreak} 🔥", null)
                            RecordRow(stringResource(R.string.record_most_pieces),
                                records.mostPiecesInSession?.let { pieceCountText(it) },
                                records.mostPiecesDate?.let { formatDate(it, locale) },
                                last = true)
                        }
                    }
                }

                tile(StatTile.BADGES) {
                    StatSection(stringResource(R.string.badges_title)) {
                        Text(
                            stringResource(R.string.badges_unlocked, badges.count { it.unlocked }, badges.size),
                            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        BadgeGrid(badges)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun statusColor(status: PieceStatus): Color = when (status) {
    PieceStatus.NEW -> PurpleText
    PieceStatus.LEARNING -> AmberText
    PieceStatus.POLISHING -> BlueText
    PieceStatus.REPERTOIRE -> ForestGreen
}

private data class OverviewCard(val tile: StatTile, val emoji: String, val value: String, val label: String)

/**
 * Normale Ansicht: zeigt nur den Inhalt. Bearbeiten-Modus: Checkbox oben rechts; abgewählte Kacheln sind ausgegraut.
 */
@Composable
private fun EditableTile(
    editMode: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!editMode) {
        Box(modifier) { content() }
        return
    }
    Box(modifier) {
        Box(
            Modifier
                .graphicsLayer { alpha = if (visible) 1f else 0.45f }
                .drawWithContent {
                    drawContent()
                    if (!visible) {
                        drawRoundRect(Color.Gray.copy(alpha = 0.25f), cornerRadius = CornerRadius(14.dp.toPx()))
                    }
                }
        ) { content() }
        Checkbox(
            checked = visible,
            onCheckedChange = { onToggle() },
            modifier = Modifier.align(Alignment.TopEnd),
            colors = CheckboxDefaults.colors(checkedColor = ForestGreen)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(color = ForestGreen, fontWeight = FontWeight.W500))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecordRow(label: String, value: String?, detail: String?, last: Boolean = false) {
    if (value == null) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleSmall.copy(color = ForestGreen))
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (!last) HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

@Composable
private fun WeekdayBars(stats: List<WeekdayStat>, locale: Locale) {
    val maxAvg = stats.maxOf { it.avgMinutes }.coerceAtLeast(1f)
    val best = stats.maxByOrNull { it.avgMinutes }?.takeIf { it.totalMinutes > 0 }
    Row(
        Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEach { st ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (st.avgMinutes > 0f) "${st.avgMinutes.toInt()}" else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (st.avgMinutes > 0f) maxOf(3.dp, 88.dp * (st.avgMinutes / maxAvg)) else 2.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (st.avgMinutes <= 0f) BorderColor else if (st == best) Amber else ForestGreen)
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.forEach { st ->
            Text(
                st.day.getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                textAlign = TextAlign.Center,
                softWrap = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (best != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.weekday_best, best.day.getDisplayName(TextStyle.FULL, locale)),
            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen)
        )
    }
}

@Composable
private fun WeeklyGoalDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weekly_goal_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                label = { Text(stringResource(R.string.minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(
                value, style = MaterialTheme.typography.headlineMedium.copy(color = ForestGreen),
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, fraction: Float, color: Color) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PaperBeige)
        ) {
            if (fraction > 0f)
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(
            "$count", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
            modifier = Modifier.width(20.dp)
        )
    }
}
