package de.klaviatur.ui.screens.chords

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.klaviatur.data.repository.ChordSong
import de.klaviatur.ui.components.ChordSheetRenderer
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextMuted
import de.klaviatur.util.ChordProParser
import de.klaviatur.util.ChordSheet
import de.klaviatur.util.UgConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordSheetDetailScreen(
    type: String,
    id: String,
    viewModel: ChordViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var songData by remember { mutableStateOf<Pair<ChordSong, String>?>(null) }
    var parsedSheet by remember { mutableStateOf<ChordSheet?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1f) }
    val scrollState = rememberScrollState()
    
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isFav = songData?.let { (song, _) -> favoriteIds.contains(song.identifier) } ?: false

    LaunchedEffect(type, id) {
        val data = viewModel.loadSongContent(context, type, id)
        songData = data
        data?.let { (song, content) ->
            val finalContent = if (song is ChordSong.Olga && song.song.format == "crd") {
                UgConverter.convert(content)
            } else {
                content
            }
            parsedSheet = ChordProParser.parse(finalContent)
        }
        isLoading = false
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { 
                    songData?.let { (song, _) ->
                        Column {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    } ?: Text("Loading...")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    songData?.let { (song, _) ->
                        IconButton(onClick = { viewModel.toggleFavorite(song.identifier) }) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorit",
                                tint = if (isFav) ForestGreen else LocalContentColor.current
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            if (!isLoading && parsedSheet != null) {
                FloatingActionButton(
                    onClick = { isAutoScrolling = !isAutoScrolling },
                    containerColor = ForestGreen,
                    contentColor = PaperWhite
                ) {
                    Icon(
                        imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isAutoScrolling) "Pause" else "Play"
                    )
                }
            }
        },
        bottomBar = {
            if (isAutoScrolling) {
                Surface(
                    color = PaperWhite,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Geschwindigkeit", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            Spacer(Modifier.width(16.dp))
                            Slider(
                                value = scrollSpeed,
                                onValueChange = { scrollSpeed = it },
                                valueRange = 0.5f..5f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = ForestGreen, activeTrackColor = ForestGreen)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("%.1f".format(scrollSpeed), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            parsedSheet?.let { sheet ->
                ChordSheetRenderer(
                    chordSheet = sheet,
                    isAutoScrolling = isAutoScrolling,
                    scrollSpeed = scrollSpeed,
                    scrollState = scrollState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Song not found or could not be loaded")
            }
        }
    }
}
