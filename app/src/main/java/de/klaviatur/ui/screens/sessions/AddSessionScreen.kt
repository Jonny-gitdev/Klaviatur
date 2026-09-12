package de.klaviatur.ui.screens.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.PracticeSession
import de.klaviatur.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionScreen(
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val allPieces by viewModel.allPieces.collectAsState()

    var durationText    by remember { mutableStateOf("") }
    var notes           by remember { mutableStateOf("") }
    var selectedPieceIds by remember { mutableStateOf(setOf<Long>()) }
    var durationError   by remember { mutableStateOf(false) }

    // Date picker state
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker  by remember { mutableStateOf(false) }

    val selectedDateMs = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    val displayDate = remember(selectedDateMs) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
        "%02d.%02d.%04d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK", color = ForestGreen) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text("Neue Session") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    TextButton(onClick = {
                        val dur = durationText.toIntOrNull()
                        if (dur == null || dur <= 0) { durationError = true; return@TextButton }
                        viewModel.upsert(
                            PracticeSession(
                                dateMillis      = selectedDateMs,
                                durationMinutes = dur,
                                pieceIds        = selectedPieceIds.map { it.toString() },
                                notes           = notes.trim()
                            )
                        )
                        onBack()
                    }) { Text("Speichern", color = ForestGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Date & duration
            FormSurface {
                Text("DATUM & DAUER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = displayDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Datum") },
                        modifier = Modifier.weight(1f),
                        colors = sessionFieldColors(),
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("📅", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it; durationError = false },
                        label = { Text("Minuten") },
                        isError = durationError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = sessionFieldColors()
                    )
                }
            }

            // Pieces
            if (allPieces.isNotEmpty()) {
                FormSurface {
                    Text("GEÜBTE STÜCKE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
                    allPieces.forEach { piece ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(piece.title, style = MaterialTheme.typography.bodyMedium)
                                Text(piece.composer, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = selectedPieceIds.contains(piece.id),
                                onCheckedChange = { checked ->
                                    selectedPieceIds = if (checked)
                                        selectedPieceIds + piece.id
                                    else
                                        selectedPieceIds - piece.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = ForestGreen)
                            )
                        }
                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                    }
                }
            }

            // Notes
            FormSurface {
                Text("NOTIZEN", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Was lief gut? Was war schwierig?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    minLines = 3,
                    colors = sessionFieldColors()
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun sessionFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = ForestGreen,
    unfocusedBorderColor    = BorderColor,
    focusedLabelColor       = ForestGreen,
    cursorColor             = ForestGreen,
    focusedContainerColor   = PaperWhite,
    unfocusedContainerColor = PaperWhite
)
