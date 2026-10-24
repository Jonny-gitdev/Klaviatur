package de.klaviatur.ui.screens.repertoire

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import de.klaviatur.ui.tutorial.tutorialTarget

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditPieceScreen(
    pieceId: Long?,
    onBack: () -> Unit,
    viewModel: RepertoireViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val allPieces by viewModel.allPieces.collectAsState()
    val existing = remember(pieceId, allPieces) { pieceId?.let { id -> allPieces.find { it.id == id } } }

    // Form state
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var composer by remember(existing) { mutableStateOf(existing?.composer ?: "") }
    var difficulty by remember(existing) { mutableStateOf(existing?.difficulty ?: 1) }

    var epoch by remember(existing) { mutableStateOf(existing?.epoch) }
    var tags by remember(existing) { mutableStateOf(existing?.tags ?: emptyList<String>()) }
    var tagInput by remember { mutableStateOf("") }

    var status by remember(existing) { mutableStateOf(existing?.status ?: PieceStatus.NEW) }
    var targetBpm by remember(existing) { mutableStateOf(existing?.targetBpm?.takeIf { it > 0 }?.toString() ?: "") }

    var sheetFiles by remember(existing) { mutableStateOf(existing?.sheetFiles ?: emptyList<SheetFile>()) }
    var totalBars by remember(existing) { mutableStateOf(existing?.totalBars?.takeIf { it > 0 }?.toString() ?: "") }

    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    // OpenOpus
    var ooQuery by remember { mutableStateOf("") }
    val ooResults by viewModel.ooResults.collectAsState()
    val ooLoading by viewModel.ooLoading.collectAsState()
    var showOoResults by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var compError by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val mime = context.contentResolver.getType(uri) ?: ""
            val type = if (mime.contains("pdf", ignoreCase = true)) SheetFileType.PDF else SheetFileType.IMAGE
            val name = getFileName(context, uri) ?: "Noten"
            val newFile = SheetFile(uriString = uri.toString(), name = name, type = type)
            sheetFiles = sheetFiles + newFile
        }
    }

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
                    TextButton(modifier = Modifier.tutorialTarget("add_save"), onClick = {
                        titleError = title.isBlank()
                        compError = composer.isBlank()
                        if (titleError || compError) return@TextButton
                        val bars = totalBars.toIntOrNull() ?: 0
                        val newSections = if (existing == null && bars > 0) {
                            val level = if (status == PieceStatus.REPERTOIRE) ProgressLevel.SAFE else ProgressLevel.NOT_LEARNED
                            listOf(
                                PieceSection(
                                    startBar = 1,
                                    endBar = bars,
                                    level = level
                                )
                            )
                        } else {
                            existing?.sections ?: emptyList()
                        }

                        val piece = (existing ?: Piece(title = "", composer = "")).copy(
                            title = title.trim(),
                            composer = composer.trim(),
                            epoch = epoch,
                            keySignature = existing?.keySignature ?: "",
                            totalBars = bars,
                            sections = newSections,
                            tags = tags,
                            targetBpm = targetBpm.toIntOrNull() ?: 0,
                            status = status,
                            difficulty = difficulty,
                            sheetFiles = sheetFiles,
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
            FormCard(stringResource(R.string.openopus_search), Modifier.tutorialTarget("add_openopus")) {
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

            // ── Stück Details ──────────────────────────────────────────────────
            FormCard(stringResource(R.string.pieces_stat), Modifier.tutorialTarget("add_details")) {
                FormField(stringResource(R.string.field_title)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it; titleError = false },
                        isError = titleError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
                }
                Spacer(Modifier.height(10.dp))
                FormField(stringResource(R.string.field_composer)) {
                    OutlinedTextField(
                        value = composer, onValueChange = { composer = it; compError = false },
                        isError = compError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
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

            // ── Kategorien ────────────────────────────────────────────────────
            FormCard("KATEGORIEN") {
                FormField(stringResource(R.string.field_epoch)) {
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
                Spacer(Modifier.height(10.dp))
                FormField(stringResource(R.string.field_tags)) {
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
            }

            // ── Fortschritt ───────────────────────────────────────────────────
            FormCard(stringResource(R.string.progress_header), Modifier.tutorialTarget("add_progress")) {
                FormField(stringResource(R.string.field_status)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(PieceStatus.NEW, PieceStatus.LEARNING, PieceStatus.REPERTOIRE).forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s.localizedLabel()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreenBg,
                                    selectedLabelColor = ForestGreen
                                )
                            )
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

            // ── Noten ─────────────────────────────────────────────────────────
            FormCard("NOTEN", Modifier.tutorialTarget("add_sheets")) {
                FormField("Notenblätter / PDF / Bild") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { documentLauncher.launch(arrayOf("application/pdf", "image/*")) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = ForestGreen)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF / Bild hinzufügen", color = ForestGreen)
                        }
                    }
                    if (sheetFiles.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sheetFiles.forEach { sheet ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PaperBeige)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (sheet.type == SheetFileType.PDF) Icons.Default.Description else Icons.Default.Image,
                                            contentDescription = null,
                                            tint = ForestGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(sheet.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    }
                                    IconButton(onClick = {
                                        sheetFiles = sheetFiles.filter { it.id != sheet.id }
                                    }) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = TextHint, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormField(stringResource(R.string.field_total_bars)) {
                    OutlinedTextField(
                        value = totalBars, onValueChange = { totalBars = it },
                        placeholder = { Text(stringResource(R.string.field_total_bars_placeholder)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = outlinedTextFieldColors()
                    )
                }
            }

            // ── Notizen ───────────────────────────────────────────────────────
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

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = it.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
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
private fun FormCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
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
