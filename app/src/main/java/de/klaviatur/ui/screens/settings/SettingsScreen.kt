package de.klaviatur.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.R
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.repository.IndexingStatus
import de.klaviatur.ui.screens.chords.ChordViewModel
import de.klaviatur.ui.theme.*
import de.klaviatur.util.AppSettings
import de.klaviatur.util.DatabaseBackupHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onStartTutorial: () -> Unit = {},
    viewModel: ChordViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val indexingStatus by viewModel.indexingStatus.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeExportDb by remember { mutableStateOf<String?>(null) }
    var activeImportDb by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && activeExportDb != null) {
            val success = DatabaseBackupHelper.exportDatabase(context, activeExportDb!!, uri)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (success) context.getString(R.string.export_success) else context.getString(R.string.export_error)
                )
            }
            activeExportDb = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activeImportDb != null) {
            val success = DatabaseBackupHelper.importDatabase(context, activeImportDb!!, uri)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (success) context.getString(R.string.import_success) else context.getString(R.string.import_error)
                )
            }
            activeImportDb = null
        }
    }

    Scaffold(
        containerColor = PaperBeige,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
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
            // Language Selection
            SettingsCard(stringResource(R.string.language_setting)) {
                AppSettings.languages.forEach { lang ->
                    val displayLabel = if (lang == "System") {
                        stringResource(R.string.system_language)
                    } else {
                        lang
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = AppSettings.language == lang,
                            onClick = { AppSettings.language = lang },
                            colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                        )
                        Text(displayLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Epoch Management
            SettingsCard(stringResource(R.string.active_epochs)) {
                Text(
                    stringResource(R.string.active_epochs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Epoch.entries.forEach { epoch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = AppSettings.activeEpochs.contains(epoch),
                            onCheckedChange = { AppSettings.toggleEpoch(epoch) },
                            colors = CheckboxDefaults.colors(checkedColor = ForestGreen)
                        )
                        Text(epoch.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Database Export & Import
            SettingsCard(stringResource(R.string.export_import_header)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            activeExportDb = "klaviatur.db"
                            exportLauncher.launch("klaviatur_backup.db")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_repertoire), color = ForestGreen)
                    }
                    OutlinedButton(
                        onClick = {
                            activeImportDb = "klaviatur.db"
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.import_repertoire), color = ForestGreen)
                    }

                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = {
                            activeExportDb = "chords.db"
                            exportLauncher.launch("chords_backup.db")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_chords), color = ForestGreen)
                    }
                    OutlinedButton(
                        onClick = {
                            activeImportDb = "chords.db"
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.import_chords), color = ForestGreen)
                    }
                }
            }

            // Chord Sheet Management
            SettingsCard(stringResource(R.string.chord_sheets_setting)) {
                Text(
                    stringResource(R.string.reindex_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        AppSettings.olgaFolderUri?.let { uri ->
                            viewModel.indexOlgaArchive(context, uri)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    enabled = AppSettings.olgaFolderUri != null && indexingStatus is IndexingStatus.Idle
                ) {
                    val text = when (indexingStatus) {
                        is IndexingStatus.Indexing -> stringResource(R.string.indexing_button)
                        else -> stringResource(R.string.reindex_button)
                    }
                    Text(text)
                }
            }

            // Tutorial Section
            SettingsCard(stringResource(R.string.tutorial_setting)) {
                Text(
                    stringResource(R.string.tutorial_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onStartTutorial,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text(stringResource(R.string.start_tutorial_btn))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
