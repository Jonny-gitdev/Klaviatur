package de.klaviatur.ui.screens.chords

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                title = { Text("UG Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                label = { Text("Titel") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Künstler") },
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
                textStyle = MaterialTheme.typography.bodySmall,
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
                        errorMessage = "Fehler bei der Konvertierung: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                enabled = title.isNotBlank() && ugText.isNotBlank()
            ) {
                Text("Konvertieren & Speichern")
            }
        }
    }
}
