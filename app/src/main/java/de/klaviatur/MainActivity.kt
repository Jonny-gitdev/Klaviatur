package de.klaviatur

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import de.klaviatur.ui.Screen
import de.klaviatur.ui.components.MetronomeBottomSheet
import de.klaviatur.ui.components.TutorialDialog
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
import de.klaviatur.ui.screens.repertoire.SheetViewerScreen
import de.klaviatur.ui.screens.sessions.AddSessionScreen
import de.klaviatur.ui.screens.sessions.PracticeModeScreen
import de.klaviatur.ui.screens.sessions.SessionViewModel
import de.klaviatur.ui.screens.sessions.SessionsScreen
import de.klaviatur.ui.screens.settings.SettingsScreen
import de.klaviatur.ui.screens.stats.StatsScreen
import de.klaviatur.ui.theme.ForestGreen
import de.klaviatur.ui.theme.ForestGreenBg
import de.klaviatur.ui.theme.KlaviatürTheme
import de.klaviatur.ui.theme.PaperBeige
import de.klaviatur.ui.theme.PaperWhite
import de.klaviatur.ui.theme.TextHint
import de.klaviatur.util.AppSettings
import de.klaviatur.util.MetronomeManager
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var metronomeManager: MetronomeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val localizedContext = rememberLocalizedContext(AppSettings.language)
            val localizedConfig = localizedContext.resources.configuration
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                KlaviatürTheme {
                    KlaviaturAppUI(metronomeManager)
                }
            }
        }
    }
}

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

@Composable
private fun rememberLocalizedContext(language: String): Context {
    val context = LocalContext.current
    return remember(language, context) {
        val locale = when (language) {
            "Deutsch" -> Locale.GERMAN
            "English" -> Locale.ENGLISH
            "Français" -> Locale.FRENCH
            "Español" -> Locale("es")
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    Resources.getSystem().configuration.locale ?: Locale.getDefault()
                }
            }
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedConfigContext = context.createConfigurationContext(config)
        LocalizedContextWrapper(context, localizedConfigContext.resources)
    }
}

private data class BottomNavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Repertoire, R.string.nav_repertoire, Icons.Default.MusicNote),
    BottomNavItem(Screen.Sessions, R.string.nav_sessions, Icons.Default.Schedule),
    BottomNavItem(Screen.Lists, R.string.nav_lists, Icons.Default.List),
    BottomNavItem(Screen.Stats, R.string.nav_stats, Icons.Default.BarChart),
    BottomNavItem(Screen.ChordSheets, R.string.nav_chords, Icons.Default.LibraryMusic),
)

@Composable
private fun KlaviaturAppUI(metronomeManager: MetronomeManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val sessionViewModel: SessionViewModel = viewModel()
    val chordViewModel: ChordViewModel = hiltViewModel()
    var showMetronome by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(!AppSettings.hasSeenTutorial) }

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    if (showTutorial) {
        TutorialDialog(onDismiss = { showTutorial = false })
    }

    if (showMetronome) {
        MetronomeBottomSheet(
            manager = metronomeManager,
            onDismiss = { showMetronome = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PaperBeige,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = PaperWhite,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        val labelText = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, labelText) },
                            label = { Text(labelText, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ForestGreen,
                                selectedTextColor = ForestGreen,
                                indicatorColor = ForestGreenBg,
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
            navController = navController,
            startDestination = Screen.Repertoire.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Bottom tabs ────────────────────────────────────────────────────
            composable(Screen.Repertoire.route) {
                RepertoireScreen(
                    onPieceClick = { id -> navController.navigate(Screen.PieceDetail.route(id)) },
                    onAddPiece = { navController.navigate(Screen.AddEditPiece.route()) },
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
                route = Screen.PieceDetail.route,
                arguments = listOf(navArgument("pieceId") { type = NavType.LongType })
            ) { backStack ->
                val pieceId = backStack.arguments?.getLong("pieceId") ?: return@composable
                PieceDetailScreen(
                    pieceId = pieceId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.AddEditPiece.route(pieceId)) },
                    onOpenSheetViewer = { id -> navController.navigate(Screen.SheetViewer.route(id)) },
                    onStartPractice = { piece ->
                        sessionViewModel.startSession(piece)
                        navController.navigate(Screen.PracticeMode.route)
                    }
                )
            }
            composable(
                route = Screen.SheetViewer.route,
                arguments = listOf(navArgument("pieceId") { type = NavType.LongType })
            ) { backStack ->
                val pieceId = backStack.arguments?.getLong("pieceId") ?: return@composable
                SheetViewerScreen(
                    pieceId = pieceId,
                    onBack = { navController.popBackStack() },
                    onStartPractice = { piece ->
                        sessionViewModel.startSession(piece)
                        navController.navigate(Screen.PracticeMode.route)
                    }
                )
            }
            composable(
                route = Screen.AddEditPiece.route,
                arguments = listOf(navArgument("pieceId") {
                    type = NavType.LongType
                    defaultValue = -1L
                })
            ) { backStack ->
                val raw = backStack.arguments?.getLong("pieceId") ?: -1L
                val pieceId = if (raw == -1L) null else raw
                AddEditPieceScreen(
                    pieceId = pieceId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AddSession.route) {
                AddSessionScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.ListDetail.route,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { backStack ->
                val listId = backStack.arguments?.getLong("listId") ?: return@composable
                ListDetailScreen(
                    listId = listId,
                    onBack = { navController.popBackStack() },
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
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onStartTutorial = { showTutorial = true }
                )
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
