package de.klaviatur.ui.screens.chords

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.klaviatur.data.repository.ChordSong
import de.klaviatur.data.repository.IndexingStatus
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.util.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordSheetsScreen(
    viewModel: ChordViewModel,
    onSongClick: (String, String) -> Unit,
    onAddManual: () -> Unit,
    onImportUg: () -> Unit
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val indexingStatus by viewModel.indexingStatus.collectAsState()
    val onlyFavorites by viewModel.onlyFavorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistFilter by viewModel.playlistFilter.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            AppSettings.olgaFolderUri = it
            viewModel.indexOlgaArchive(context, it)
        }
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = { Text("Chord Sheets") },
                actions = {
                    IconButton(onClick = { showPlaylistDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Playlist erstellen")
                    }
                    IconButton(onClick = { folderLauncher.launch(null) }) {
                        Icon(Icons.Default.Folder, "OLGA Folder wählen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                FloatingActionButton(
                    onClick = { showMenu = true },
                    containerColor = ForestGreen,
                    contentColor = PaperWhite
                ) {
                    Icon(Icons.Default.Add, "Hinzufügen")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Manuelle Eingabe") },
                        onClick = { showMenu = false; onAddManual() }
                    )
                    DropdownMenuItem(
                        text = { Text("UG Import") },
                        onClick = { showMenu = false; onImportUg() }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(padding)) {
                if (indexingStatus is IndexingStatus.Indexing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ForestGreen
                    )
                    Text(
                        text = "Indiziere OLGA: ${(indexingStatus as IndexingStatus.Indexing).currentCategory}...",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Suchen...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )

                // Basic Filter Chips
                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = sourceFilter == null && !onlyFavorites && playlistFilter == null,
                            onClick = { 
                                viewModel.updateSourceFilter(null)
                                viewModel.updatePlaylistFilter(null)
                                if (onlyFavorites) viewModel.toggleFavoritesFilter()
                            },
                            label = { Text("Alle") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { viewModel.toggleFavoritesFilter() },
                            label = { Text("Favoriten") },
                            leadingIcon = { Icon(if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = sourceFilter == "OLGA",
                            onClick = { viewModel.updateSourceFilter("OLGA") },
                            label = { Text("OLGA") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = sourceFilter == "Imported",
                            onClick = { viewModel.updateSourceFilter("Imported") },
                            label = { Text("UG") }
                        )
                    }
                }

                if (playlists.isNotEmpty()) {
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists) { playlist ->
                            FilterChip(
                                selected = playlistFilter == playlist.id,
                                onClick = { 
                                    viewModel.updatePlaylistFilter(if (playlistFilter == playlist.id) null else playlist.id)
                                },
                                label = { Text(playlist.name) }
                            )
                        }
                    }
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    items(songs) { song ->
                        var showSongMenu by remember { mutableStateOf(false) }
                        val isFav = favoriteIds.contains(song.identifier)

                        ListItem(
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text(song.artist) },
                            leadingContent = {
                                IconButton(onClick = { viewModel.toggleFavorite(song.identifier) }) {
                                    Icon(
                                        if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorit",
                                        tint = if (isFav) ForestGreen else LocalContentColor.current
                                    )
                                }
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { showSongMenu = true }) {
                                        Icon(Icons.Default.MoreVert, null)
                                    }
                                    DropdownMenu(expanded = showSongMenu, onDismissRequest = { showSongMenu = false }) {
                                        if (playlists.isNotEmpty()) {
                                            Text("Zu Playlist hinzufügen:", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                                            playlists.forEach { p ->
                                                DropdownMenuItem(
                                                    text = { Text(p.name) },
                                                    onClick = {
                                                        viewModel.addSongToPlaylist(p, song.identifier)
                                                        showSongMenu = false
                                                    }
                                                )
                                            }
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Keine Playlists vorhanden") },
                                                onClick = { showSongMenu = false }
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                val type = if (song is ChordSong.Olga) "olga" else "user"
                                val idStr = when(song) {
                                    is ChordSong.Olga -> song.song.id
                                    is ChordSong.User -> song.song.id.toString()
                                }
                                onSongClick(type, idStr)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            if (showPlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showPlaylistDialog = false },
                    title = { Text("Neue Playlist") },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName)
                                newPlaylistName = ""
                                showPlaylistDialog = false
                            }
                        }) { Text("Erstellen") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPlaylistDialog = false }) { Text("Abbrechen") }
                    }
                )
            }

            if (indexingStatus is IndexingStatus.Error) {
                AlertDialog(
                    onDismissRequest = { viewModel.resetIndexingStatus() },
                    title = { Text("Fehler beim Import") },
                    text = { Text((indexingStatus as IndexingStatus.Error).message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetIndexingStatus() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}
