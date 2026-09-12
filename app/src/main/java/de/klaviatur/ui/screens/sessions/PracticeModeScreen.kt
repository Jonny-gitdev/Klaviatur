package de.klaviatur.ui.screens.sessions

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.PieceSection
import de.klaviatur.ui.screens.repertoire.RepertoireViewModel
import de.klaviatur.ui.screens.repertoire.SectionEditDialog
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.util.MetronomeManager
import kotlinx.coroutines.delay

@Composable
fun PracticeModeScreen(
    viewModel: SessionViewModel,
    metronomeManager: MetronomeManager,
    onBack: () -> Unit,
    repertoireViewModel: RepertoireViewModel = hiltViewModel()
) {
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val currentPiece by viewModel.currentPiece.collectAsState()
    val practicedPieces by viewModel.practicedPieces.collectAsState()
    val currentBars by viewModel.currentBars.collectAsState()
    val allPieces by viewModel.allPieces.collectAsState()
    val selectedSections by viewModel.selectedSections.collectAsState()
    
    val metronomeBpm by metronomeManager.bpm.collectAsState()
    val metronomeIsPlaying by metronomeManager.isPlaying.collectAsState()

    var showPiecePicker by remember { mutableStateOf(false) }
    var showMetronomeSettings by remember { mutableStateOf(false) }
    var showSectionEditor by remember { mutableStateOf(false) }

    var editingSection by remember { mutableStateOf<PieceSection?>(null) }
    var editingSectionIdx by remember { mutableStateOf(-1) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog && currentPiece != null) {
        SectionEditDialog(
            section = editingSection,
            totalBars = currentPiece!!.totalBars,
            onDismiss = { showEditDialog = false },
            onConfirm = { section ->
                repertoireViewModel.upsertSection(currentPiece!!, section, editingSectionIdx)
                showEditDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestGreen)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, "Beenden", tint = PaperWhite)
                }
                Text(
                    text = "ÜBUNGSSESSION",
                    style = MaterialTheme.typography.labelLarge,
                    color = PaperWhite.copy(alpha = 0.7f)
                )
                IconButton(onClick = { showMetronomeSettings = !showMetronomeSettings }) {
                    Icon(
                        if (metronomeIsPlaying) Icons.Default.MusicNote else Icons.Default.MusicNote, 
                        "Metronom", 
                        tint = if (metronomeIsPlaying) Color.Yellow else PaperWhite
                    )
                }
            }

            // Main Content: Timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) PaperWhite.copy(alpha = 0.5f) else PaperWhite
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FilledIconButton(
                        onClick = { viewModel.togglePause() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = PaperWhite.copy(alpha = 0.2f), contentColor = PaperWhite),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Fortsetzen" else "Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Piece and Bars Info
                Card(
                    onClick = { showPiecePicker = true },
                    colors = CardDefaults.cardColors(containerColor = PaperWhite.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentPiece?.title ?: "Tippe zum Auswählen",
                            style = MaterialTheme.typography.titleLarge,
                            color = PaperWhite
                        )
                        if (currentBars.isNotEmpty()) {
                            Text(
                                text = "Takte: $currentBars",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PaperWhite.copy(alpha = 0.8f)
                            )
                        }
                        if (currentPiece != null) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showSectionEditor = true }) {
                                Icon(Icons.Default.List, null, tint = PaperWhite, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Abschnitte verwalten", color = PaperWhite)
                            }
                        }
                    }
                }

                // Session History
                if (practicedPieces.size > 1 || (practicedPieces.size == 1 && currentPiece == null)) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "In dieser Session geübt:",
                        style = MaterialTheme.typography.labelMedium,
                        color = PaperWhite.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    practicedPieces.forEach { piece ->
                        if (piece.id != currentPiece?.id) {
                            Text(piece.title, color = PaperWhite.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.stopSession(); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForestGreen),
                    modifier = Modifier.height(56.dp).fillMaxWidth(0.7f)
                ) {
                    Text("Session beenden", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Piece & Bars Picker Sheet/Dialog
        if (showPiecePicker) {
            AlertDialog(
                onDismissRequest = { showPiecePicker = false },
                title = { Text("Was übst du gerade?") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = currentBars,
                            onValueChange = { viewModel.updateCurrentBars(it) },
                            label = { Text("Takte (z.B. 1-8)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Stück auswählen:", style = MaterialTheme.typography.titleSmall)
                        Box(modifier = Modifier.height(300.dp)) {
                            LazyColumn {
                                items(allPieces) { piece ->
                                    ListItem(
                                        headlineContent = { Text(piece.title) },
                                        supportingContent = { Text(piece.composer) },
                                        trailingContent = {
                                            if (currentPiece?.id == piece.id) {
                                                Icon(Icons.Default.Check, null)
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.updateCurrentPiece(piece)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPiecePicker = false }) { Text("Fertig") }
                }
            )
        }

        if (showSectionEditor && currentPiece != null) {
            AlertDialog(
                onDismissRequest = { showSectionEditor = false },
                title = { 
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Abschnitte")
                        IconButton(onClick = { 
                            editingSection = null
                            editingSectionIdx = -1
                            showEditDialog = true
                        }) { Icon(Icons.Default.Add, null) }
                    }
                },
                text = {
                    Box(modifier = Modifier.height(400.dp)) {
                        LazyColumn {
                            itemsIndexed(currentPiece!!.sections) { index, section ->
                                val isSelected = selectedSections.contains(index)
                                ListItem(
                                    headlineContent = { Text(section.name.ifBlank { "Takt ${section.startBar}-${section.endBar}" }) },
                                    supportingContent = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(Color(section.level.color), RoundedCornerShape(4.dp)))
                                            Spacer(Modifier.width(8.dp))
                                            Text(section.level.label)
                                        }
                                    },
                                    leadingContent = {
                                        Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleSection(index) })
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = {
                                                editingSection = section
                                                editingSectionIdx = index
                                                showEditDialog = true
                                            }) { Icon(Icons.Default.Edit, null) }
                                            IconButton(onClick = {
                                                repertoireViewModel.deleteSection(currentPiece!!, section)
                                            }) { Icon(Icons.Default.Delete, null) }
                                        }
                                    },
                                    modifier = Modifier.clickable { viewModel.toggleSection(index) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSectionEditor = false }) { Text("Schließen") }
                }
            )
        }

        // Metronome Overlay
        if (showMetronomeSettings) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 16.dp)
                    .width(200.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Metronom", style = MaterialTheme.typography.titleMedium)
                    Text("$metronomeBpm BPM", style = MaterialTheme.typography.headlineSmall)
                    Slider(
                        value = metronomeBpm.toFloat(),
                        onValueChange = { metronomeManager.setBpm(it.toInt()) },
                        valueRange = 30f..300f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { metronomeManager.setBpm(metronomeBpm - 1) }) { Icon(Icons.Default.Remove, null) }
                        IconButton(onClick = { metronomeManager.setBpm(metronomeBpm + 1) }) { Icon(Icons.Default.Add, null) }
                    }
                    Button(
                        onClick = { metronomeManager.toggle() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (metronomeIsPlaying) Color.Red else ForestGreen
                        )
                    ) {
                        Text(if (metronomeIsPlaying) "Stop" else "Start")
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
