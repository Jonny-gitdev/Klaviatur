package de.klaviatur.ui.screens.chords

import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.klaviatur.R
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.ui.components.ChordDiagramSheet
import de.klaviatur.ui.components.InstrumentChips
import de.klaviatur.data.repository.ChordSong
import de.klaviatur.ui.components.ChordSheetRenderer
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextMuted
import de.klaviatur.util.ChordProParser
import de.klaviatur.util.ChordSheet
import de.klaviatur.util.ChordTheory
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
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isFav = songData?.let { (song, _) -> favoriteIds.contains(song.identifier) } ?: false

    // Instrument des Songs (Standard Gitarre) + Transponierung + angetippter Akkord
    val instrumentMap by viewModel.instrumentMap.collectAsState()
    val songIdentifier = songData?.first?.identifier
    val instrument = songIdentifier?.let { instrumentMap[it] } ?: ChordInstrument.GUITAR
    var transpose by rememberSaveable(type, id) { mutableStateOf(0) }
    var selectedChord by remember { mutableStateOf<String?>(null) }
    val shownSheet = remember(parsedSheet, transpose) {
        parsedSheet?.let { ChordTheory.transposeSheet(it, transpose) }
    }

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
            Column {
            TopAppBar(
                title = {
                    songData?.let { (song, _) ->
                        Column {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    } ?: Text("...")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    songData?.let { (song, _) ->
                        if (song is ChordSong.User) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Titel/Künstler bearbeiten", tint = TextMuted)
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(song.identifier) }) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.filter_favorites),
                                tint = if (isFav) ForestGreen else LocalContentColor.current
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
            if (!isLoading && parsedSheet != null) {
                Surface(color = PaperWhite, tonalElevation = 0.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        InstrumentChips(
                            selected = instrument,
                            onSelect = { inst ->
                                if (inst != null && songIdentifier != null) viewModel.setInstrument(songIdentifier, inst)
                            },
                            instruments = ChordInstrument.chordSectionInstruments
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.chord_transpose),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { transpose = (transpose - 1).coerceAtLeast(-11) }) {
                                Icon(Icons.Default.Remove, "-1")
                            }
                            Text(
                                text = if (transpose > 0) "+$transpose" else "$transpose",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 40.dp)
                            )
                            IconButton(onClick = { transpose = (transpose + 1).coerceAtMost(11) }) {
                                Icon(Icons.Default.Add, "+1")
                            }
                            TextButton(onClick = { transpose = 0 }, enabled = transpose != 0) {
                                Text(stringResource(R.string.chord_transpose_reset), color = if (transpose != 0) ForestGreen else TextMuted)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
            }
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
                        contentDescription = if (isAutoScrolling) stringResource(R.string.action_pause) else stringResource(R.string.action_start)
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
                            Text("Tempo", style = MaterialTheme.typography.labelMedium, color = TextMuted)
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
            shownSheet?.let { sheet ->
                ChordSheetRenderer(
                    chordSheet = sheet,
                    isAutoScrolling = isAutoScrolling,
                    scrollSpeed = scrollSpeed,
                    scrollState = scrollState,
                    onChordClick = { selectedChord = it },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_pieces_title))
            }
        }
    }

    selectedChord?.let { chord ->
        ChordDiagramSheet(
            chord = chord,
            instrument = instrument,
            onDismiss = { selectedChord = null }
        )
    }

    if (showEditDialog) {
        val userSong = (songData?.first as? ChordSong.User)?.song
        var editTitle by remember(userSong) { mutableStateOf(userSong?.title ?: "") }
        var editArtist by remember(userSong) { mutableStateOf(userSong?.artist ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Titel & Künstler bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Künstler") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        userSong?.let { s ->
                            viewModel.updateUserSongMeta(s.id, editTitle.trim(), editArtist.trim())
                            songData = songData?.copy(first = ChordSong.User(s.copy(title = editTitle.trim(), artist = editArtist.trim())))
                        }
                        showEditDialog = false
                    },
                    enabled = editTitle.isNotBlank()
                ) {
                    Text("Speichern", color = ForestGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Chord Sheet löschen") },
            text = { Text("Möchtest du dieses Chord Sheet wirklich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        songData?.let { (song, _) ->
                            viewModel.deleteSong(song)
                        }
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
