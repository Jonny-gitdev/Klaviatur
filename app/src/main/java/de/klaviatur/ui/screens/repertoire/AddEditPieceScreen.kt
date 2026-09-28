package de.klaviatur.ui.screens.repertoire

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.R
import de.klaviatur.data.model.*
import de.klaviatur.ui.components.localizedLabel
import de.klaviatur.ui.theme.*
import de.klaviatur.util.AppSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditPieceScreen(
    pieceId: Long?,
    onBack: () -> Unit,
    viewModel: RepertoireViewModel = hiltViewModel()
) {
    val allPieces by viewModel.allPieces.collectAsState()
    val existing = remember(pieceId, allPieces) { pieceId?.let { id -> allPieces.find { it.id == id } } }

    // Form state
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var composer by remember(existing) { mutableStateOf(existing?.composer ?: "") }
    var epoch by remember(existing) { mutableStateOf(existing?.epoch) }
    var keySignature by remember(existing) { mutableStateOf(existing?.keySignature ?: "") }
    var totalBars by remember(existing) { mutableStateOf(existing?.totalBars?.takeIf { it > 0 }?.toString() ?: "") }
    var initialProgress by remember { mutableStateOf(ProgressLevel.NOT_LEARNED) }
    var tags by remember(existing) { mutableStateOf(existing?.tags ?: emptyList<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var targetBpm by remember(existing) { mutableStateOf(existing?.targetBpm?.takeIf { it > 0 }?.toString() ?: "") }
    var status by remember(existing) { mutableStateOf(existing?.status ?: PieceStatus.NEW) }
    var difficulty by remember(existing) { mutableStateOf(existing?.difficulty ?: 1) }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    // OpenOpus
    var ooQuery by remember { mutableStateOf("") }
    val ooResults by viewModel.ooResults.collectAsState()
    val ooLoading by viewModel.ooLoading.collectAsState()
    var showOoResults by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var compError by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_piece_title)) },
            text = { Text(stringResource(R.string.delete_piece_message)) },
            confirmButton = {
                TextButton(onClick = {
                    existing?.let { viewModel.deletePiece(it) }
                    showDeleteDialog = false
                    onBack()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text(if (pieceId == null) stringResource(R.string.new_piece) else stringResource(R.string.edit_piece)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (pieceId != null) {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        titleError = title.isBlank()
                        compError = composer.isBlank()
                        if (titleError || compError) return@TextButton
                        val bars = totalBars.toIntOrNull() ?: 0
                        val newSections = if (existing == null && bars > 0) {
                            listOf(
                                PieceSection(
                                    startBar = 1,
                                    endBar = bars,
                                    level = initialProgress
                                )
                            )
                        } else {
                            existing?.sections ?: emptyList()
                        }

                        val newStatus = if (existing == null && initialProgress == ProgressLevel.SAFE) {
                            PieceStatus.REPERTOIRE
                        } else {
                            status
                        }

                        val piece = (existing ?: Piece(title = "", composer = "")).copy(
                            title = title.trim(),
                            composer = composer.trim(),
                            epoch = epoch,
                            keySignature = keySignature.trim(),
                            totalBars = bars,
                            sections = newSections,
                            tags = tags,
                            targetBpm = targetBpm.toIntOrNull() ?: 0,
                            status = newStatus,
                            difficulty = difficulty,
                            notes = notes.trim()
                        )
                        viewModel.upsertPiece(piece)
                        onBack()
                    }) { Text(stringResource(R.string.action_save), color = ForestGreen) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── OpenOpus search ───────────────────────────────────────────────
            FormCard(stringResource(R.string.openopus_search)) {
                OutlinedTextField(
                    value = ooQuery,
                    onValueChange = {
                        ooQuery = it
                        showOoResults = it.length >= 2
                        viewModel.searchOpenOpus(it)
                    },
                    placeholder = { Text(stringResource(R.string.openopus_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (ooQuery.isNotEmpty()) {
                            IconButton(onClick = { ooQuery = ""; viewModel.clearOoResults(); showOoResults = false }) {
                                Icon(Icons.Default.Close, stringResource(R.string.action_delete))
                            }
                        }
                    },
                    colors = outlinedTextFieldColors()
                )
                if (ooLoading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = ForestGreen,
                        trackColor = BorderColor
                    )
                }
                if (showOoResults && ooResults.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ooResults.forEach { result ->
                        Surface(
                            onClick = {
                                title = result.title + (result.subtitle?.let { " – $it" } ?: "")
                                composer = result.composer
                                ooQuery = ""
                                showOoResults = false
                                viewModel.clearOoResults()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = PaperBeige,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    result.title + (result.subtitle?.let { " – $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(result.composer, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                    }
                }
            }

            // ── Basic info ────────────────────────────────────────────────────
            FormCard(stringResource(R.string.pieces_stat)) {
                FormField(stringResource(R.string.field_title)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it; titleError = false },
                        isError = titleError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(stringResource(R.string.field_composer), Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = composer, onValueChange = { composer = it; compError = false },
                            isError = compError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = outlinedTextFieldColors()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField(stringResource(R.string.field_key), Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = keySignature, onValueChange = { keySignature = it },
                            placeholder = { Text(stringResource(R.string.field_key_placeholder)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = outlinedTextFieldColors()
                        )
                    }
                    FormField(stringResource(R.string.field_epoch), Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = epoch?.localizedLabel() ?: stringResource(R.string.select_epoch),
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                colors = outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.no_epoch)) }, onClick = { epoch = null; expanded = false })
                                AppSettings.activeEpochs.forEach { e ->
                                    DropdownMenuItem(text = { Text(e.localizedLabel()) }, onClick = { epoch = e; expanded = false })
                                }
                            }
                        }
                    }
                }
            }

            // ── Status & difficulty ───────────────────────────────────────────
            FormCard("${stringResource(R.string.field_status)} & ${stringResource(R.string.field_difficulty)}") {
                FormField(stringResource(R.string.field_status)) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = status.localizedLabel(), onValueChange = {}, readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            colors = outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            PieceStatus.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.localizedLabel()) }, onClick = { status = s; expanded = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormField(stringResource(R.string.field_difficulty)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { i ->
                                DifficultyChip(i, difficulty) { difficulty = it }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DifficultyChip(5, difficulty) { difficulty = it }
                        }
                    }
                }
            }

            // ── Progress ──────────────────────────────────────────────────────
            FormCard(stringResource(R.string.progress_header)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    FormField(stringResource(R.string.field_total_bars), Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = totalBars, onValueChange = { totalBars = it },
                            placeholder = { Text(stringResource(R.string.field_total_bars_placeholder)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), colors = outlinedTextFieldColors()
                        )
                    }
                    if (existing == null) {
                        FormField(stringResource(R.string.field_start_state), Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = initialProgress == ProgressLevel.NOT_LEARNED,
                                    onClick = { initialProgress = ProgressLevel.NOT_LEARNED },
                                    label = { Text(stringResource(R.string.state_not_learned)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ProgressLevel.NOT_LEARNED.color.let { Color(it) }, selectedLabelColor = Color.White)
                                )
                                FilterChip(
                                    selected = initialProgress == ProgressLevel.SAFE,
                                    onClick = { initialProgress = ProgressLevel.SAFE },
                                    label = { Text(stringResource(R.string.state_safe)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ProgressLevel.SAFE.color.let { Color(it) }, selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormField(stringResource(R.string.field_target_bpm)) {
                    OutlinedTextField(
                        value = targetBpm, onValueChange = { targetBpm = it },
                        placeholder = { Text(stringResource(R.string.field_target_bpm_placeholder)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = outlinedTextFieldColors()
                    )
                }
            }

            // ── Tags ────────────────────────────────────────────────────────────
            FormCard(stringResource(R.string.field_tags)) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text(stringResource(R.string.field_tag_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (tagInput.isNotBlank()) {
                            IconButton(onClick = {
                                if (tagInput.isNotBlank() && !tags.contains(tagInput.trim())) {
                                    tags = tags + tagInput.trim()
                                    tagInput = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, stringResource(R.string.add_piece))
                            }
                        }
                    },
                    colors = outlinedTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (tagInput.isNotBlank() && !tags.contains(tagInput.trim())) {
                            tags = tags + tagInput.trim()
                            tagInput = ""
                        }
                    })
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { tags = tags - tag },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            // ── Notes ─────────────────────────────────────────────────────────
            FormCard(stringResource(R.string.field_notes)) {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    placeholder = { Text(stringResource(R.string.field_notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    minLines = 3, colors = outlinedTextFieldColors()
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DifficultyChip(level: Int, current: Int, onClick: (Int) -> Unit) {
    FilterChip(
        selected = current == level,
        onClick = { onClick(level) },
        label = { Text("★".repeat(level)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmberBg,
            selectedLabelColor = AmberText
        )
    )
}

@Composable
private fun FormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun FormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ForestGreen,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = ForestGreen,
    cursorColor = ForestGreen,
    focusedContainerColor = PaperWhite,
    unfocusedContainerColor = PaperWhite
)
