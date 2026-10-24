package de.klaviatur.ui.screens.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import de.klaviatur.R
import de.klaviatur.ui.components.PieceCard
import de.klaviatur.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    onPieceClick: (Long) -> Unit,
    viewModel: ListViewModel = hiltViewModel()
) {
    val lists by viewModel.lists.collectAsState()
    val allPieces by viewModel.allPieces.collectAsState()
    val list = lists.find { it.id == listId }

    var showAddPieceDialog by remember { mutableStateOf(false) }
    var showDeleteListDialog by remember { mutableStateOf(false) }

    var initialCheckDone by remember { mutableStateOf(false) }
    LaunchedEffect(lists) {
        if (lists.isNotEmpty() || initialCheckDone) {
            initialCheckDone = true
        } else {
            delay(300)
            initialCheckDone = true
        }
    }

    LaunchedEffect(list, initialCheckDone) {
        if (initialCheckDone && list == null) {
            onBack()
        }
    }

    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ForestGreen)
        }
        return
    }

    val piecesInList = viewModel.piecesForList(list)
    val piecesNotInList = allPieces.filter { !list.pieceIds.contains(it.id) }

    // Add piece dialog
    if (showAddPieceDialog) {
        AlertDialog(
            onDismissRequest = { showAddPieceDialog = false },
            title = { Text(stringResource(R.string.add_piece)) },
            text = {
                if (piecesNotInList.isEmpty()) {
                    Text(stringResource(R.string.no_pieces_title))
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(piecesNotInList) { piece ->
                            TextButton(
                                onClick = {
                                    viewModel.addPieceToList(listId, piece.id)
                                    showAddPieceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(piece.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Text(piece.composer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddPieceDialog = false }) { Text(stringResource(R.string.action_close)) } }
        )
    }

    // Delete list dialog
    if (showDeleteListDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteListDialog = false },
            title = { Text(stringResource(R.string.delete_list_title)) },
            text = { Text(stringResource(R.string.delete_list_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteList(list)
                    showDeleteListDialog = false
                    onBack()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteListDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(list.name, style = MaterialTheme.typography.titleLarge)
                        if (list.description.isNotBlank())
                            Text(list.description, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showDeleteListDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete_list_title), tint = TextHint)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPieceDialog = true },
                containerColor = ForestGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) { Text("+", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onPrimary)) }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (piecesInList.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎼", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.no_pieces_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.no_pieces_subtitle),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(piecesInList, key = { it.id }) { piece ->
                    Box {
                        PieceCard(piece = piece, onClick = { onPieceClick(piece.id) })
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            IconButton(
                                onClick = { viewModel.removePieceFromList(listId, piece.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, stringResource(R.string.remove_from_list),
                                    tint = TextHint, modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
