package de.klaviatur.ui.screens.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.PracticeSession
import de.klaviatur.ui.components.StatTile
import de.klaviatur.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onAddSession: () -> Unit,
    onStartPractice: (Long?) -> Unit,
    onShowMetronome: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val sessions     by viewModel.sessions.collectAsState()
    val totalMinutes by viewModel.totalMinutes.collectAsState()
    val streak       by viewModel.streak.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val totalHours   = totalMinutes / 60f

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Übungssessions", style = MaterialTheme.typography.headlineMedium)
                        Text("${sessions.size} Einträge", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = onShowMetronome) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Metronom", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSession,
                containerColor = ForestGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Default.Add, "Session hinzufügen") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Button(
                    onClick = { 
                        if (!isSessionActive) viewModel.startSession()
                        onStartPractice(null) 
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSessionActive) Amber else ForestGreen
                    )
                ) {
                    Icon(if (isSessionActive) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSessionActive) "Zur laufenden Session" else "Jetzt üben (Timer)", 
                        style = MaterialTheme.typography.titleMedium, 
                        color = Color.White
                    )
                }
            }

            // Stats row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = "🔥 $streak",
                        label = "Tage Streak",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${String.format("%.1f", totalHours)}h",
                        label = "Gesamt",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${sessions.size}",
                        label = "Sessions",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📋", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("Keine Sessions", style = MaterialTheme.typography.titleMedium)
                            Text("Tippe auf + um deine erste Session einzutragen",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        pieceNames = viewModel.allPieces.value
                            .filter { session.pieceIds.contains(it.id.toString()) }
                            .map { it.title },
                        onDelete = { viewModel.delete(session) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SessionCard(
    session: PracticeSession,
    pieceNames: List<String>,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            text = { Text("Session löschen?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } }
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        sdf.format(Date(session.dateMillis)),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "${session.durationMinutes} Minuten",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ForestGreen,
                            fontWeight = FontWeight.W500
                        )
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Löschen", tint = TextHint, modifier = Modifier.size(18.dp))
                }
            }

            if (pieceNames.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pieceNames.forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PaperBeige,
                            border = ButtonDefaults.outlinedButtonBorder
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (session.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    session.notes,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
    }
}
