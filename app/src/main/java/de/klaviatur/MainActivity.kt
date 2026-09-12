package de.klaviatur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import de.klaviatur.ui.Screen
import de.klaviatur.ui.components.MetronomeBottomSheet
import de.klaviatur.ui.screens.chords.ChordSheetDetailScreen
import de.klaviatur.ui.screens.chords.ChordSheetsScreen
import de.klaviatur.ui.screens.chords.ChordViewModel
import de.klaviatur.ui.screens.chords.ManualEntryScreen
import de.klaviatur.ui.screens.chords.UgImportScreen
import de.klaviatur.ui.screens.lists.ListDetailScreen
import de.klaviatur.ui.screens.lists.ListsScreen
import de.klaviatur.ui.screens.repertoire.AddEditPieceScreen
import de.klaviatur.ui.screens.repertoire.PieceDetailScreen
import de.klaviatur.ui.screens.repertoire.RepertoireScreen
import de.klaviatur.ui.screens.settings.SettingsScreen
import de.klaviatur.ui.screens.sessions.AddSessionScreen
import de.klaviatur.ui.screens.sessions.PracticeModeScreen
import de.klaviatur.ui.screens.sessions.SessionViewModel
import de.klaviatur.ui.screens.sessions.SessionsScreen
import de.klaviatur.ui.screens.stats.StatsScreen
import de.klaviatur.ui.theme.KlaviatürTheme
import de.klaviatur.util.MetronomeManager
import javax.inject.Inject
import de.klaviatur.ui.theme.BorderColor
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextHint
import de.klaviatur.ui.theme.TextPrimary

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var metronomeManager: MetronomeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KlaviatürTheme {
                KlaviaturAppUI(metronomeManager)
            }
        }
    }
}

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Repertoire, "Repertoire", Icons.Default.MusicNote),
    BottomNavItem(Screen.Sessions,   "Übungen",    Icons.Default.Schedule),
    BottomNavItem(Screen.Lists,      "Listen",     Icons.Default.List),
    BottomNavItem(Screen.Stats,      "Statistik",  Icons.Default.BarChart),
    BottomNavItem(Screen.ChordSheets, "Akkorde",    Icons.Default.LibraryMusic),
)

@Composable
private fun KlaviaturAppUI(metronomeManager: MetronomeManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val sessionViewModel: SessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val chordViewModel: ChordViewModel = hiltViewModel()
    var showMetronome by remember { mutableStateOf(false) }

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    if (showMetronome) {
        MetronomeBottomSheet(
            manager = metronomeManager,
            onDismiss = { showMetronome = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = de.klaviatur.ui.theme.PaperBeige,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = PaperWhite,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = ForestGreen,
                                selectedTextColor   = ForestGreen,
                                indicatorColor      = de.klaviatur.ui.theme.ForestGreenBg,
                                unselectedIconColor = TextHint,
                                unselectedTextColor = TextHint
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Repertoire.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            // ── Bottom tabs ────────────────────────────────────────────────────
            composable(Screen.Repertoire.route) {
                RepertoireScreen(
                    onPieceClick = { id -> navController.navigate(Screen.PieceDetail.route(id)) },
                    onAddPiece   = { navController.navigate(Screen.AddEditPiece.route()) },
                    onShowMetronome = { showMetronome = true },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Sessions.route) {
                SessionsScreen(
                    onAddSession = { navController.navigate(Screen.AddSession.route) },
                    onStartPractice = { pieceId ->
                        navController.navigate(Screen.PracticeMode.route)
                    },
                    onShowMetronome = { showMetronome = true },
                    viewModel = sessionViewModel
                )
            }
            composable(Screen.Lists.route) {
                ListsScreen(
                    onListClick = { id -> navController.navigate(Screen.ListDetail.route(id)) }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen()
            }
            composable(Screen.ChordSheets.route) {
                ChordSheetsScreen(
                    viewModel = chordViewModel,
                    onSongClick = { type, id ->
                        navController.navigate(Screen.ChordSheetDetail.route(type, id))
                    },
                    onAddManual = { navController.navigate(Screen.ManualEntry.route) },
                    onImportUg = { navController.navigate(Screen.UgImport.route) }
                )
            }

            // ── Detail screens ─────────────────────────────────────────────────
            composable(
                route     = Screen.PieceDetail.route,
                arguments = listOf(navArgument("pieceId") { type = NavType.LongType })
            ) { backStack ->
                val pieceId = backStack.arguments?.getLong("pieceId") ?: return@composable
                PieceDetailScreen(
                    pieceId = pieceId,
                    onBack  = { navController.popBackStack() },
                    onEdit  = { navController.navigate(Screen.AddEditPiece.route(pieceId)) },
                    onStartPractice = { piece ->
                        sessionViewModel.startSession(piece)
                        navController.navigate(Screen.PracticeMode.route)
                    }
                )
            }
            composable(
                route     = Screen.AddEditPiece.route,
                arguments = listOf(navArgument("pieceId") {
                    type     = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStack ->
                val raw     = backStack.arguments?.getLong("pieceId") ?: -1L
                val pieceId = if (raw == -1L) null else raw
                AddEditPieceScreen(
                    pieceId = pieceId,
                    onBack  = { navController.popBackStack() }
                )
            }
            composable(Screen.AddSession.route) {
                AddSessionScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route     = Screen.ListDetail.route,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { backStack ->
                val listId = backStack.arguments?.getLong("listId") ?: return@composable
                ListDetailScreen(
                    listId      = listId,
                    onBack      = { navController.popBackStack() },
                    onPieceClick = { id -> navController.navigate(Screen.PieceDetail.route(id)) }
                )
            }
            composable(Screen.PracticeMode.route) {
                PracticeModeScreen(
                    viewModel = sessionViewModel,
                    metronomeManager = metronomeManager,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.ChordSheetDetail.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType }
                )
            ) { backStack ->
                val type = backStack.arguments?.getString("type") ?: return@composable
                val id = backStack.arguments?.getString("id") ?: return@composable
                ChordSheetDetailScreen(
                    type = type,
                    id = id,
                    viewModel = chordViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.UgImport.route) {
                UgImportScreen(
                    viewModel = chordViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ManualEntry.route) {
                ManualEntryScreen(
                    viewModel = chordViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
