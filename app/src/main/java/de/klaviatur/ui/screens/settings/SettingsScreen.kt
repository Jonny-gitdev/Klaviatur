package de.klaviatur.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.repository.IndexingStatus
import de.klaviatur.ui.screens.chords.ChordViewModel
import de.klaviatur.ui.theme.*
import de.klaviatur.util.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: ChordViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val indexingStatus by viewModel.indexingStatus.collectAsState()
    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
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
            SettingsCard("Sprache") {
                AppSettings.languages.forEach { lang ->
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
                        Text(lang, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Epoch Management
            SettingsCard("Aktive Epochen") {
                Text(
                    "Wähle aus, welche Epochen in der Auswahl erscheinen sollen.",
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

            // Chord Sheet Management
            SettingsCard("Chord Sheets") {
                Text(
                    "OLGA Index neu aufbauen, falls Dateien hinzugefügt wurden.",
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
                        is IndexingStatus.Indexing -> "Indiziere..."
                        else -> "OLGA Index aktualisieren"
                    }
                    Text(text)
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
