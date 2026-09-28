package de.klaviatur.ui.screens.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.R
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceSection
import de.klaviatur.data.model.ProgressLevel
import de.klaviatur.data.model.TempoEntry
import de.klaviatur.ui.components.BarProgress
import de.klaviatur.ui.components.DifficultyDots
import de.klaviatur.ui.components.StatusBadge
import de.klaviatur.ui.components.localizedLabel
import de.klaviatur.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PieceDetailScreen(
    pieceId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartPractice: (Piece) -> Unit = {},
    viewModel: RepertoireViewModel = hiltViewModel()
) {
    val allPieces by viewModel.allPieces.collectAsState()
    val piece = allPieces.find { it.id == pieceId }

    var showTempoDialog by remember { mutableStateOf(false) }
    var tempoBpm by remember { mutableStateOf("") }
    var tempoNote by remember { mutableStateOf("") }

    var showSectionDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableStateOf<PieceSection?>(null) }
    var editingSectionIdx by remember { mutableStateOf(-1) }

    var showAutoSplitDialog by remember { mutableStateOf(false) }
    var barsPerSection by remember { mutableStateOf("8") }

    var isPracticing by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var practiceSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPracticing) {
        if (isPracticing) {
            startTime = System.currentTimeMillis()
            while (isPracticing) {
                delay(1000)
                practiceSeconds++
            }
        }
    }

    if (showAutoSplitDialog) {
        AlertDialog(
            onDismissRequest = { showAutoSplitDialog = false },
            title = { Text(stringResource(R.string.auto_split_title)) },
            text = {
                OutlinedTextField(
                    value = barsPerSection, onValueChange = { barsPerSection = it },
                    label = { Text(stringResource(R.string.bars_per_section)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val bars = barsPerSection.toIntOrNull() ?: 8
                    if (piece != null) {
                        viewModel.autoSplitSections(piece, bars)
                        showAutoSplitDialog = false
                    }
                }) { Text(stringResource(R.string.split_button), color = ForestGreen) }
            },
            dismissButton = { TextButton(onClick = { showAutoSplitDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showTempoDialog) {
        AlertDialog(
            onDismissRequest = { showTempoDialog = false },
            title = { Text(stringResource(R.string.add_tempo_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tempoBpm, onValueChange = { tempoBpm = it },
                        label = { Text(stringResource(R.string.bpm)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempoNote, onValueChange = { tempoNote = it },
                        label = { Text(stringResource(R.string.note_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val bpm = tempoBpm.toIntOrNull()
                    if (bpm != null && piece != null) {
                        viewModel.addTempoEntry(piece, TempoEntry(bpm = bpm, note = tempoNote))
                        showTempoDialog = false
                        tempoBpm = ""; tempoNote = ""
                    }
                }) { Text(stringResource(R.string.action_save), color = ForestGreen) }
            },
            dismissButton = { TextButton(onClick = { showTempoDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (piece != null && showSectionDialog) {
        SectionEditDialog(
            section = editingSection,
            totalBars = piece.totalBars,
            onDismiss = { showSectionDialog = false; editingSection = null; editingSectionIdx = -1 },
            onConfirm = { newSection ->
                val currentSections = piece.sections.toMutableList()
                if (editingSectionIdx >= 0) {
                    currentSections[editingSectionIdx] = newSection
                } else {
                    currentSections.add(newSection)
                }
                viewModel.updateSections(piece, currentSections)
                showSectionDialog = false; editingSection = null; editingSectionIdx = -1
            }
        )
    }

    if (piece == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ForestGreen)
        }
        return
    }

    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit), tint = ForestGreen) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = PaperWhite, border = ButtonDefaults.outlinedButtonBorder) {
                    Column(Modifier.padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(piece.title, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    piece.composer, style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            StatusBadge(piece.status)
                        }

                        Spacer(Modifier.height(16.dp))
                        PracticeTimer(
                            isPracticing = isPracticing,
                            seconds = practiceSeconds,
                            onStart = {
                                isPracticing = true
                                practiceSeconds = 0
                            },
                            onStop = {
                                isPracticing = false
                                val durationMinutes = (practiceSeconds / 60).coerceAtLeast(1)
                                viewModel.savePracticeSession(piece, durationMinutes)
                                practiceSeconds = 0
                            },
                            onModeSwitch = {
                                isPracticing = false
                                practiceSeconds = 0
                                onStartPractice(piece)
                            }
                        )

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DifficultyDots(piece.difficulty)
                            if (piece.epoch != null) {
                                Text("· ${piece.epoch.localizedLabel()}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (piece.keySignature.isNotBlank()) {
                                Text("· ${piece.keySignature}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (piece.lastPracticedAt != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.last_practiced, sdf.format(Date(piece.lastPracticedAt))),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (System.currentTimeMillis() - piece.lastPracticedAt > 30 * 24 * 60 * 60 * 1000L)
                                    MaterialTheme.colorScheme.error
                                else
                                    TextMuted
                            )
                        }
                    }
                }
            }

            // Progress
            if (piece.totalBars > 0) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = PaperWhite, border = ButtonDefaults.outlinedButtonBorder) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.progress_header), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 10.dp))
                            BarProgress(piece.sections, piece.totalBars, Modifier.fillMaxWidth())
                            if (piece.targetBpm > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text("Ziel: ${piece.targetBpm} BPM", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = PaperWhite, border = ButtonDefaults.outlinedButtonBorder) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sections_header), style = MaterialTheme.typography.labelSmall)
                                Row {
                                    TextButton(onClick = { showAutoSplitDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text(stringResource(R.string.auto_split), fontSize = 12.sp, color = Amber)
                                    }
                                    TextButton(onClick = { showSectionDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text(stringResource(R.string.add_section), fontSize = 12.sp, color = ForestGreen)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (piece.sections.isEmpty()) {
                                Text(stringResource(R.string.no_sections_defined), style = MaterialTheme.typography.bodySmall)
                            } else {
                                piece.sections.sortedBy { it.startBar }.forEachIndexed { idx, section ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                editingSection = section
                                                editingSectionIdx = piece.sections.indexOf(section)
                                                showSectionDialog = true
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(section.level.color))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (section.name.isNotBlank()) section.name else "Takt ${section.startBar}–${section.endBar}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500)
                                            )
                                            if (section.name.isNotBlank()) {
                                                Text("Takt ${section.startBar}–${section.endBar}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (section.notes.isNotBlank()) {
                                                Text(section.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                            }
                                        }
                                        IconButton(onClick = {
                                            val currentSections = piece.sections.toMutableList()
                                            currentSections.remove(section)
                                            viewModel.updateSections(piece, currentSections)
                                        }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = TextHint, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    if (idx < piece.sections.size - 1) {
                                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tempo log
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = PaperWhite, border = ButtonDefaults.outlinedButtonBorder) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.tempo_log_header), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { showTempoDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.add_entry), fontSize = 12.sp, color = ForestGreen)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (piece.tempoLog.isEmpty()) {
                            Text(stringResource(R.string.no_tempo_entries), style = MaterialTheme.typography.bodySmall)
                        } else {
                            piece.tempoLog.asReversed().forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sdf.format(Date(entry.dateMillis)), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${entry.bpm} BPM", style = MaterialTheme.typography.bodySmall.copy(
                                            color = ForestGreen, fontWeight = FontWeight.W500
                                        )
                                    )
                                    Text(entry.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                }
                                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Notes
            if (piece.notes.isNotBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = PaperWhite, border = ButtonDefaults.outlinedButtonBorder) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.field_notes), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
                            Text(piece.notes, style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PracticeTimer(
    isPracticing: Boolean,
    seconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onModeSwitch: () -> Unit
) {
    val mins = seconds / 60
    val secs = seconds % 60
    val timeStr = "%02d:%02d".format(mins, secs)

    Surface(
        color = if (isPracticing) ForestGreenBg else PaperBeige,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (isPracticing) stringResource(R.string.practice_running) else stringResource(R.string.ready_to_practice),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPracticing) ForestGreenText else TextMuted
                    )
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isPracticing) ForestGreen else TextPrimary
                    )
                }
                Button(
                    onClick = if (isPracticing) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPracticing) MaterialTheme.colorScheme.error else ForestGreen
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        if (isPracticing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPracticing) stringResource(R.string.action_stop) else stringResource(R.string.action_start))
                }
            }
            if (!isPracticing) {
                TextButton(
                    onClick = onModeSwitch,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.switch_to_focus), fontSize = 12.sp, color = ForestGreen)
                }
            }
        }
    }
}

@Composable
fun SectionEditDialog(
    section: PieceSection?,
    totalBars: Int,
    onDismiss: () -> Unit,
    onConfirm: (PieceSection) -> Unit
) {
    var name by remember { mutableStateOf(section?.name ?: "") }
    var startBar by remember { mutableStateOf(section?.startBar?.toString() ?: "") }
    var endBar by remember { mutableStateOf(section?.endBar?.toString() ?: "") }
    var level by remember { mutableStateOf(section?.level ?: ProgressLevel.NOT_LEARNED) }
    var notes by remember { mutableStateOf(section?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (section == null) stringResource(R.string.add_section) else stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (z.B. Thema A)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startBar, onValueChange = { startBar = it },
                        label = { Text("Start Takt") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = endBar, onValueChange = { endBar = it },
                        label = { Text("Ende Takt") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Text("Status", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProgressLevel.entries.forEach { l ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { level = l }) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (level == l) Color(l.color) else Color(l.color).copy(alpha = 0.2f))
                                    .border(if (level == l) 2.dp else 0.dp, TextPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (level == l) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(l.localizedLabel(), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startBar.toIntOrNull() ?: 1
                val end = endBar.toIntOrNull() ?: totalBars
                onConfirm(PieceSection(name, start, end, level, notes))
            }) { Text(stringResource(R.string.action_save), color = ForestGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
