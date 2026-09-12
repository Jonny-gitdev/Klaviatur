package de.klaviatur.ui.screens.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.klaviatur.data.model.PieceList
import de.klaviatur.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onListClick: (Long) -> Unit,
    viewModel: ListViewModel = hiltViewModel()
) {
    val lists by viewModel.lists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newListName      by remember { mutableStateOf("") }
    var newListDesc      by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Neue Liste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        label = { Text("Name *") },
                        placeholder = { Text("z.B. Vorspiel 2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = listFieldColors()
                    )
                    OutlinedTextField(
                        value = newListDesc,
                        onValueChange = { newListDesc = it },
                        label = { Text("Beschreibung") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = listFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newListName.isNotBlank()) {
                        viewModel.upsertList(PieceList(name = newListName.trim(), description = newListDesc.trim()))
                        newListName = ""; newListDesc = ""
                        showCreateDialog = false
                    }
                }) { Text("Erstellen", color = ForestGreen) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Abbrechen") } }
        )
    }

    Scaffold(
        containerColor = PaperBeige,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Listen", style = MaterialTheme.typography.headlineMedium)
                        Text("${lists.size} Listen", style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ForestGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Default.Add, "Liste erstellen") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lists.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📁", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("Keine Listen", style = MaterialTheme.typography.titleMedium)
                            Text("Erstelle Listen z.B. für Vorspielprogramme",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                items(lists, key = { it.id }) { list ->
                    val count = viewModel.piecesForList(list).size
                    ListCard(list = list, pieceCount = count, onClick = { onListClick(list.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ListCard(list: PieceList, pieceCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PaperWhite,
        border = ButtonDefaults.outlinedButtonBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium)
                if (list.description.isNotBlank()) {
                    Text(list.description, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    "$pieceCount Stück${if (pieceCount != 1) "e" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(color = ForestGreen),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge.copy(color = TextHint))
        }
    }
}

@Composable
private fun listFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = ForestGreen,
    unfocusedBorderColor    = BorderColor,
    focusedLabelColor       = ForestGreen,
    cursorColor             = ForestGreen,
    focusedContainerColor   = PaperWhite,
    unfocusedContainerColor = PaperWhite
)
