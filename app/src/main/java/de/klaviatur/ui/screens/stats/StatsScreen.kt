package de.klaviatur.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.ui.screens.sessions.SessionViewModel
import de.klaviatur.ui.screens.repertoire.RepertoireViewModel
import de.klaviatur.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    repertoireVm: RepertoireViewModel = hiltViewModel(),
    sessionVm: SessionViewModel = hiltViewModel()
) {
    val pieces       by repertoireVm.allPieces.collectAsState()
    val sessions     by sessionVm.sessions.collectAsState()
    val totalMinutes by sessionVm.totalMinutes.collectAsState()
    val streak       by sessionVm.streak.collectAsState()

    val statusCounts = PieceStatus.entries.associateWith { s -> pieces.count { it.status == s } }
    val epochCounts  = Epoch.entries.associateWith { e -> pieces.count { it.epoch == e } }
    val avgDifficulty = if (pieces.isEmpty()) 0f else pieces.sumOf { it.difficulty }.toFloat() / pieces.size

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text("Statistik", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Overview tiles
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("🎹", "${pieces.size}", "Stücke", Modifier.weight(1f))
                    StatCard("⏱", "${totalMinutes / 60}h ${totalMinutes % 60}m", "Übungszeit", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("🔥", "$streak", "Tage Streak", Modifier.weight(1f))
                    StatCard("📋", "${sessions.size}", "Sessions", Modifier.weight(1f))
                }
            }

            // Status distribution
            if (pieces.isNotEmpty()) {
                item {
                    StatSection("Status-Verteilung") {
                        PieceStatus.entries.forEach { status ->
                            val count = statusCounts[status] ?: 0
                            val frac  = count.toFloat() / pieces.size
                            StatusBar(
                                label = status.label,
                                count = count,
                                fraction = frac,
                                color = when (status) {
                                    PieceStatus.NEW        -> PurpleText
                                    PieceStatus.LEARNING   -> AmberText
                                    PieceStatus.POLISHING  -> BlueText
                                    PieceStatus.REPERTOIRE -> ForestGreen
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Epoch distribution
                val usedEpochs = epochCounts.filter { it.value > 0 }
                if (usedEpochs.isNotEmpty()) {
                    item {
                        StatSection("Epochen") {
                            usedEpochs.entries.sortedByDescending { it.value }.forEach { (epoch, count) ->
                                StatusBar(
                                    label    = epoch.label,
                                    count    = count,
                                    fraction = count.toFloat() / pieces.size,
                                    color    = Amber
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Difficulty distribution
                item {
                    StatSection("Schwierigkeit") {
                        (1..5).forEach { d ->
                            val count = pieces.count { it.difficulty == d }
                            StatusBar(
                                label    = "★".repeat(d),
                                count    = count,
                                fraction = if (pieces.isNotEmpty()) count.toFloat() / pieces.size else 0f,
                                color    = Amber
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ø Schwierigkeit: ${"%.1f".format(avgDifficulty)} / 5",
                            style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen)
                        )
                    }
                }

                // Top composers
                item {
                    val composerCounts = pieces.groupBy { it.composer }
                        .mapValues { it.value.size }
                        .entries.sortedByDescending { it.value }
                        .take(5)
                    StatSection("Häufigste Komponisten") {
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
                                Text("$count Stück${if (count != 1) "e" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen))
                            }
                            if (i < composerCounts.lastIndex)
                                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        }
                    }
                }

                // Recent sessions chart (last 7 sessions as bars)
                if (sessions.isNotEmpty()) {
                    item {
                        StatSection("Letzte Sessions (Minuten)") {
                            val recent = sessions.take(7).reversed()
                            val maxDur = recent.maxOf { it.durationMinutes }.coerceAtLeast(1)
                            Row(
                                Modifier.fillMaxWidth().height(80.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                recent.forEach { session ->
                                    val frac = session.durationMinutes.toFloat() / maxDur
                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Text(
                                            "${session.durationMinutes}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            modifier = Modifier.padding(bottom = 3.dp)
                                        )
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(frac)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(ForestGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text("Füge Stücke und Sessions hinzu, um Statistiken zu sehen.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        color     = PaperWhite,
        border    = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(color = ForestGreen),
                modifier = Modifier.padding(top = 4.dp))
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
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun StatusBar(label: String, count: Int, fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Text("$count", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
            modifier = Modifier.width(20.dp))
    }
}
