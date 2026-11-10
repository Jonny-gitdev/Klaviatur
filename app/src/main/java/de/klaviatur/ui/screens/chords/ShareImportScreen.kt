package de.klaviatur.ui.screens.chords

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.ui.components.ExistingChordSongSuggestion
import de.klaviatur.ui.components.InstrumentChips
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klaviatur.R
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.util.ShareInbox
import de.klaviatur.util.UgShareImporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareImportScreen(
    viewModel: ChordViewModel,
    onImported: (songId: String) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit
) {
    val sharedText = remember { ShareInbox.pendingText }.also { ShareInbox.pendingText = null }
    var title by rememberSaveable(sharedText) { mutableStateOf(sharedText?.let { UgShareImporter.suggestTitle(it) }.orEmpty()) }
    var artist by rememberSaveable(sharedText) { mutableStateOf("") }
    var instrument by remember { mutableStateOf<ChordInstrument?>(ChordInstrument.GUITAR) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val allSongs by viewModel.allSongs.collectAsState()

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        }
    ) { padding ->
        if (sharedText.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Kein geteilter Text vorhanden.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onManual, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) {
                        Text(stringResource(R.string.share_import_manual))
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExistingChordSongSuggestion(title, allSongs)

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Künstler") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.instrument_label), style = MaterialTheme.typography.labelMedium)
                InstrumentChips(
                    selected = instrument,
                    onSelect = { instrument = it },
                    allowNone = true,
                    instruments = ChordInstrument.chordSectionInstruments
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    enabled = !saving && title.isNotBlank(),
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val songId = viewModel.saveSharedText(sharedText, title.trim(), artist.trim(), instrument)
                                onImported(songId)
                            } catch (e: Exception) {
                                error = e.message ?: e.javaClass.simpleName
                                saving = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}
