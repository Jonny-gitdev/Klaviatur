package de.klaviatur.ui.screens.repertoire

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.ui.components.PieceCard
import de.klaviatur.ui.components.SectionHeader
import de.klaviatur.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepertoireScreen(
    onPieceClick: (Long) -> Unit,
    onAddPiece: () -> Unit,
    onShowMetronome: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: RepertoireViewModel = hiltViewModel()
) {
    val pieces by viewModel.pieces.collectAsState()
    val allPieces by viewModel.allPieces.collectAsState()
    val needsPractice by viewModel.needsPracticePieces.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    val allTags = remember(allPieces) {
        allPieces.flatMap { it.tags }.distinct().sorted()
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = filter.query,
                            onValueChange = { viewModel.setFilter(filter.copy(query = it)) },
                            placeholder = { Text("Suchen…", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ForestGreen,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    } else {
                        Column {
                            Text("Repertoire", style = MaterialTheme.typography.headlineMedium)
                            Text("${pieces.size} Stücke", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.pickShufflePiece()?.let { onPieceClick(it) }
                    }) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = ForestGreen)
                    }
                    IconButton(onClick = onShowMetronome) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Metronom", tint = TextMuted)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = TextMuted)
                    }
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.setFilter(filter.copy(query = ""))
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Suchen", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPiece,
                containerColor = ForestGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Stück hinzufügen")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Filter chips
            item {
                FilterChipRow(
                    filter = filter,
                    allTags = allTags,
                    onFilterChange = { viewModel.setFilter(it) }
                )
            }

            // Reminders
            if (needsPractice.isNotEmpty() && !showSearch && filter.query.isBlank() && filter.status == null && filter.epoch == null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = ForestGreenBg.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Zeit zum Auffrischen!",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ForestGreenText
                                )
                            }
                            Text(
                                "Diese Stücke hast du schon länger nicht mehr geübt:",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                needsPractice.take(5).forEach { piece ->
                                    SuggestionChip(
                                        onClick = { onPieceClick(piece.id) },
                                        label = { Text(piece.title) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = PaperWhite,
                                            labelColor = TextPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (pieces.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎼", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("Noch keine Stücke", style = MaterialTheme.typography.titleMedium)
                            Text("Tippe auf + um dein erstes Stück anzulegen", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                // In Arbeit
                val inProgress = pieces.filter { it.status == PieceStatus.LEARNING || it.status == PieceStatus.POLISHING }
                if (inProgress.isNotEmpty()) {
                    item { SectionHeader("In Arbeit") }
                    items(inProgress, key = { it.id }) { piece ->
                        PieceCard(piece = piece, onClick = { onPieceClick(piece.id) })
                    }
                }

                // Repertoire
                val repertoire = pieces.filter { it.status == PieceStatus.REPERTOIRE }
                if (repertoire.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)); SectionHeader("Repertoire") }
                    items(repertoire, key = { it.id }) { piece ->
                        PieceCard(piece = piece, onClick = { onPieceClick(piece.id) })
                    }
                }

                // Neu
                val newPieces = pieces.filter { it.status == PieceStatus.NEW }
                if (newPieces.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)); SectionHeader("Neu") }
                    items(newPieces, key = { it.id }) { piece ->
                        PieceCard(piece = piece, onClick = { onPieceClick(piece.id) })
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun FilterChipRow(
    filter: RepertoireFilter,
    allTags: List<String>,
    onFilterChange: (RepertoireFilter) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FilterList, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        
        PieceStatus.entries.forEach { status ->
            FilterChip(
                selected = filter.status == status,
                onClick = {
                    onFilterChange(filter.copy(status = if (filter.status == status) null else status))
                },
                label = { Text(status.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForestGreenBg,
                    selectedLabelColor = ForestGreenText
                )
            )
        }
        
        allTags.forEach { tag ->
            val selected = filter.selectedTags.contains(tag)
            FilterChip(
                selected = selected,
                onClick = {
                    val newTags = if (selected) filter.selectedTags - tag else filter.selectedTags + tag
                    onFilterChange(filter.copy(selectedTags = newTags))
                },
                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ForestGreenBg.copy(alpha = 0.5f),
                    selectedLabelColor = ForestGreenText
                )
            )
        }

        Epoch.entries.forEach { epoch ->
            FilterChip(
                selected = filter.epoch == epoch,
                onClick = {
                    onFilterChange(filter.copy(epoch = if (filter.epoch == epoch) null else epoch))
                },
                label = { Text(epoch.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberBg,
                    selectedLabelColor = AmberText
                )
            )
        }
    }
}
