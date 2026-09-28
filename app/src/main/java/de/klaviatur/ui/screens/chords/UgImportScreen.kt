package de.klaviatur.ui.screens.chords

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.klaviatur.R
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.util.UgConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UgImportScreen(
    viewModel: ChordViewModel,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var ugText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filter_ug)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text(stringResource(R.string.field_composer)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ugText,
                onValueChange = {
                    ugText = it
                    errorMessage = null
                },
                label = { Text("Ultimate Guitar Text (Chord-over-lyrics)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                isError = errorMessage != null,
                supportingText = { errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            Button(
                onClick = {
                    try {
                        val chordPro = UgConverter.convert(ugText)
                        viewModel.saveUserSong(title, artist, chordPro, "ug_import")
                        onBack()
                    } catch (e: Exception) {
                        errorMessage = e.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                enabled = title.isNotBlank() && ugText.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
