package de.klaviatur.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import de.klaviatur.R
import de.klaviatur.data.model.Piece
import de.klaviatur.ui.Screen
import de.klaviatur.util.AppSettings
import kotlinx.coroutines.delay

/** Die Bildschirme, zwischen denen das Tutorial springt. */
enum class TutorialScreen {
    WELCOME, REPERTOIRE, ADD_PIECE, PIECE_A, SHEET_A, PIECE_B, SHEET_B, CHORDS, UG_IMPORT, FINISH
}

/** Ereignisse, die die echte App-UI meldet, wenn der Nutzer eine geforderte Aktion ausführt. */
enum class TutorialEvent { SECTIONS_HIDDEN, EDIT_TOOL_SELECTED, DETECTION_DONE }

enum class TutorialKind {
    /** Erklärung mit "Weiter"-Button. */
    INFO,
    /** Nutzer muss das hervorgehobene Element selbst bedienen. */
    ACTION,
    /** Ja/Nein-Frage. */
    CHOICE,
    WELCOME,
    FINISH
}

/** Welches Tutorial-Stück ein Schritt voraussetzt (A = Pathétique, B = Moonlight). */
enum class TutorialPiece { A, B }

data class TutorialStep(
    val id: String,
    val screen: TutorialScreen,
    val kind: TutorialKind = TutorialKind.INFO,
    /** ID eines mit Modifier.tutorialTarget(...) markierten Elements; null = nichts hervorheben. */
    val target: String? = null,
    val titleRes: Int,
    val textRes: Int,
    /** Bei ACTION: Navigation auf diesen Screen schließt den Schritt ab. */
    val advanceOnScreen: TutorialScreen? = null,
    /** Bei ACTION: dieses Ereignis schließt den Schritt ab. */
    val advanceOnEvent: TutorialEvent? = null,
    /** Schritt wird übersprungen, wenn das Stück nicht (mehr) existiert. */
    val needs: TutorialPiece? = null
)

object TutorialManager {

    // ── Schrittliste ──────────────────────────────────────────────────────────
    val steps: List<TutorialStep> = listOf(
        // 1) Willkommen
        TutorialStep("welcome", TutorialScreen.WELCOME, TutorialKind.WELCOME,
            titleRes = R.string.tut_welcome_title, textRes = R.string.tut_welcome_text),

        // 2) Repertoire-Übersicht
        TutorialStep("repertoire_intro", TutorialScreen.REPERTOIRE, target = "piece_card_a",
            titleRes = R.string.tut_repertoire_intro_title, textRes = R.string.tut_repertoire_intro_text),
        TutorialStep("repertoire_progress", TutorialScreen.REPERTOIRE, target = "piece_progress_a",
            titleRes = R.string.tut_repertoire_progress_title, textRes = R.string.tut_repertoire_progress_text),
        TutorialStep("repertoire_filters", TutorialScreen.REPERTOIRE, target = "filter_chips",
            titleRes = R.string.tut_repertoire_filters_title, textRes = R.string.tut_repertoire_filters_text),
        TutorialStep("repertoire_topbar", TutorialScreen.REPERTOIRE, target = "repertoire_topbar",
            titleRes = R.string.tut_repertoire_topbar_title, textRes = R.string.tut_repertoire_topbar_text),
        TutorialStep("repertoire_nav", TutorialScreen.REPERTOIRE, target = "bottom_nav",
            titleRes = R.string.tut_repertoire_nav_title, textRes = R.string.tut_repertoire_nav_text),

        // 3) Neues Stück hinzufügen
        TutorialStep("add_fab", TutorialScreen.REPERTOIRE, TutorialKind.ACTION, target = "fab_add",
            titleRes = R.string.tut_add_fab_title, textRes = R.string.tut_add_fab_text,
            advanceOnScreen = TutorialScreen.ADD_PIECE),
        TutorialStep("add_openopus", TutorialScreen.ADD_PIECE, target = "add_openopus",
            titleRes = R.string.tut_add_openopus_title, textRes = R.string.tut_add_openopus_text),
        TutorialStep("add_details", TutorialScreen.ADD_PIECE, target = "add_details",
            titleRes = R.string.tut_add_details_title, textRes = R.string.tut_add_details_text),
        TutorialStep("add_progress", TutorialScreen.ADD_PIECE, target = "add_progress",
            titleRes = R.string.tut_add_progress_title, textRes = R.string.tut_add_progress_text),
        TutorialStep("add_sheets", TutorialScreen.ADD_PIECE, target = "add_sheets",
            titleRes = R.string.tut_add_sheets_title, textRes = R.string.tut_add_sheets_text),
        TutorialStep("add_save", TutorialScreen.ADD_PIECE, target = "add_save",
            titleRes = R.string.tut_add_save_title, textRes = R.string.tut_add_save_text),

        // 4) Pathétique: Stückseite + Notenansicht + Abschnitte
        TutorialStep("a_open_card", TutorialScreen.REPERTOIRE, TutorialKind.ACTION, target = "piece_card_a",
            titleRes = R.string.tut_a_open_card_title, textRes = R.string.tut_a_open_card_text,
            advanceOnScreen = TutorialScreen.PIECE_A, needs = TutorialPiece.A),
        TutorialStep("a_detail_header", TutorialScreen.PIECE_A, target = "detail_header",
            titleRes = R.string.tut_a_detail_header_title, textRes = R.string.tut_a_detail_header_text,
            needs = TutorialPiece.A),
        TutorialStep("a_detail_progress", TutorialScreen.PIECE_A, target = "detail_progress",
            titleRes = R.string.tut_a_detail_progress_title, textRes = R.string.tut_a_detail_progress_text,
            needs = TutorialPiece.A),
        TutorialStep("a_detail_sections", TutorialScreen.PIECE_A, target = "detail_sections",
            titleRes = R.string.tut_a_detail_sections_title, textRes = R.string.tut_a_detail_sections_text,
            needs = TutorialPiece.A),
        TutorialStep("a_detail_tempo", TutorialScreen.PIECE_A, target = "detail_tempo",
            titleRes = R.string.tut_a_detail_tempo_title, textRes = R.string.tut_a_detail_tempo_text,
            needs = TutorialPiece.A),
        TutorialStep("a_open_sheets", TutorialScreen.PIECE_A, TutorialKind.ACTION, target = "detail_sheet_button",
            titleRes = R.string.tut_a_open_sheets_title, textRes = R.string.tut_a_open_sheets_text,
            advanceOnScreen = TutorialScreen.SHEET_A, needs = TutorialPiece.A),
        TutorialStep("a_sheet_top", TutorialScreen.SHEET_A, target = "sheet_topbar",
            titleRes = R.string.tut_a_sheet_top_title, textRes = R.string.tut_a_sheet_top_text,
            needs = TutorialPiece.A),
        TutorialStep("a_sheet_tools", TutorialScreen.SHEET_A, target = "sheet_tools",
            titleRes = R.string.tut_a_sheet_tools_title, textRes = R.string.tut_a_sheet_tools_text,
            needs = TutorialPiece.A),
        TutorialStep("a_sheet_sections", TutorialScreen.SHEET_A, target = "sheet_sections_bar",
            titleRes = R.string.tut_a_sheet_sections_title, textRes = R.string.tut_a_sheet_sections_text,
            needs = TutorialPiece.A),
        TutorialStep("a_hide_sections", TutorialScreen.SHEET_A, TutorialKind.ACTION, target = "tool_sections",
            titleRes = R.string.tut_a_hide_sections_title, textRes = R.string.tut_a_hide_sections_text,
            advanceOnEvent = TutorialEvent.SECTIONS_HIDDEN, needs = TutorialPiece.A),
        TutorialStep("a_sections_done", TutorialScreen.SHEET_A, target = "tool_sections",
            titleRes = R.string.tut_a_sections_done_title, textRes = R.string.tut_a_sections_done_text,
            needs = TutorialPiece.A),

        // 5) Moonlight: automatische Takterkennung
        TutorialStep("b_open_card", TutorialScreen.REPERTOIRE, TutorialKind.ACTION, target = "piece_card_b",
            titleRes = R.string.tut_b_open_card_title, textRes = R.string.tut_b_open_card_text,
            advanceOnScreen = TutorialScreen.PIECE_B, needs = TutorialPiece.B),
        TutorialStep("b_open_sheets", TutorialScreen.PIECE_B, TutorialKind.ACTION, target = "detail_sheet_button",
            titleRes = R.string.tut_b_open_sheets_title, textRes = R.string.tut_b_open_sheets_text,
            advanceOnScreen = TutorialScreen.SHEET_B, needs = TutorialPiece.B),
        TutorialStep("b_detect_intro", TutorialScreen.SHEET_B, target = "tool_edit",
            titleRes = R.string.tut_b_detect_intro_title, textRes = R.string.tut_b_detect_intro_text,
            needs = TutorialPiece.B),
        TutorialStep("b_detect_limits", TutorialScreen.SHEET_B,
            titleRes = R.string.tut_b_detect_limits_title, textRes = R.string.tut_b_detect_limits_text,
            needs = TutorialPiece.B),
        TutorialStep("b_select_edit", TutorialScreen.SHEET_B, TutorialKind.ACTION, target = "tool_edit",
            titleRes = R.string.tut_b_select_edit_title, textRes = R.string.tut_b_select_edit_text,
            advanceOnEvent = TutorialEvent.EDIT_TOOL_SELECTED, needs = TutorialPiece.B),
        TutorialStep("b_run_detect", TutorialScreen.SHEET_B, TutorialKind.ACTION, target = "tool_detect",
            titleRes = R.string.tut_b_run_detect_title, textRes = R.string.tut_b_run_detect_text,
            advanceOnEvent = TutorialEvent.DETECTION_DONE, needs = TutorialPiece.B),
        TutorialStep("b_result", TutorialScreen.SHEET_B, target = "sheet_content",
            titleRes = R.string.tut_b_result_title, textRes = R.string.tut_b_result_text,
            needs = TutorialPiece.B),
        TutorialStep("b_edit_tools", TutorialScreen.SHEET_B, target = "edit_actions",
            titleRes = R.string.tut_b_edit_tools_title, textRes = R.string.tut_b_edit_tools_text,
            needs = TutorialPiece.B),
        TutorialStep("b_create_sections", TutorialScreen.SHEET_B, target = "tool_scroll",
            titleRes = R.string.tut_b_create_sections_title, textRes = R.string.tut_b_create_sections_text,
            needs = TutorialPiece.B),

        // 6) Akkorde
        TutorialStep("c_ask", TutorialScreen.CHORDS, TutorialKind.CHOICE,
            titleRes = R.string.tut_c_ask_title, textRes = R.string.tut_c_ask_text),
        TutorialStep("c_add", TutorialScreen.CHORDS, target = "chords_fab",
            titleRes = R.string.tut_c_add_title, textRes = R.string.tut_c_add_text),
        TutorialStep("c_ug_meta", TutorialScreen.UG_IMPORT, target = "ug_title",
            titleRes = R.string.tut_c_ug_meta_title, textRes = R.string.tut_c_ug_meta_text),
        TutorialStep("c_ug_text", TutorialScreen.UG_IMPORT, target = "ug_text",
            titleRes = R.string.tut_c_ug_text_title, textRes = R.string.tut_c_ug_text_text),
        TutorialStep("c_ug_save", TutorialScreen.UG_IMPORT, target = "ug_save",
            titleRes = R.string.tut_c_ug_save_title, textRes = R.string.tut_c_ug_save_text),
        TutorialStep("c_db", TutorialScreen.CHORDS, target = "chords_folder",
            titleRes = R.string.tut_c_db_title, textRes = R.string.tut_c_db_text),

        // Ende
        TutorialStep("finish", TutorialScreen.FINISH, TutorialKind.FINISH,
            titleRes = R.string.tut_finish_title, textRes = R.string.tut_finish_text)
    )

    // ── Zustand ───────────────────────────────────────────────────────────────
    var active by mutableStateOf(false)
        private set
    private var index by mutableIntStateOf(0)
    private var launchChecked = false

    var pieceAId by mutableStateOf<Long?>(null)
        private set
    var pieceBId by mutableStateOf<Long?>(null)
        private set

    val currentStep: TutorialStep? get() = if (active) steps.getOrNull(index) else null
    val progress: Float get() = index.toFloat() / (steps.size - 1).coerceAtLeast(1)
    val canGoBack: Boolean get() = previousIndex() != null

    // ── Start / Ende ──────────────────────────────────────────────────────────
    /** Beim App-Start aufrufen: startet das Tutorial genau einmal, solange es noch nicht gesehen wurde. */
    fun startIfFirstLaunch() {
        if (launchChecked) return
        launchChecked = true
        if (!AppSettings.hasSeenTutorial) start()
    }

    fun start() {
        index = 0
        active = true
    }

    fun finish() {
        active = false
        AppSettings.hasSeenTutorial = true
    }

    // ── Navigation durch die Schritte ─────────────────────────────────────────
    private fun isAvailable(step: TutorialStep): Boolean = when (step.needs) {
        TutorialPiece.A -> pieceAId != null
        TutorialPiece.B -> pieceBId != null
        null -> true
    }

    private fun nextIndex(): Int? {
        var i = index + 1
        while (i < steps.size) {
            if (isAvailable(steps[i])) return i
            i++
        }
        return null
    }

    private fun previousIndex(): Int? {
        var i = index - 1
        while (i >= 0) {
            if (isAvailable(steps[i])) return i
            i--
        }
        return null
    }

    fun next() {
        val n = nextIndex()
        if (n == null) finish() else index = n
    }

    fun previous() {
        previousIndex()?.let { index = it }
    }

    fun jumpTo(stepId: String) {
        val i = steps.indexOfFirst { it.id == stepId }
        if (i >= 0) index = i
    }

    // ── Signale aus der App-UI ────────────────────────────────────────────────
    fun onEvent(event: TutorialEvent) {
        val step = currentStep ?: return
        if (step.kind == TutorialKind.ACTION && step.advanceOnEvent == event) next()
    }

    /** MainActivity meldet jede Änderung der aktuellen (konkreten) Route. */
    fun onScreenReached(route: String?) {
        val step = currentStep ?: return
        val goal = step.advanceOnScreen ?: return
        if (step.kind == TutorialKind.ACTION && route != null && route == routeFor(goal)) next()
    }

    // ── Stücke ────────────────────────────────────────────────────────────────
    /** Erkennt die beiden mitgelieferten Beispielstücke anhand ihres Titels. */
    fun updatePieces(pieces: List<Piece>) {
        pieceAId = pieces.firstOrNull { it.title.contains("Pathétique", ignoreCase = true) }?.id
        pieceBId = pieces.firstOrNull { it.title.contains("Moonlight", ignoreCase = true) }?.id
    }

    /** "a"/"b" für die Tutorial-Stücke, sonst null (für Modifier.tutorialTarget in der Stückliste). */
    fun pieceKey(pieceId: Long): String? = when (pieceId) {
        pieceAId -> "a"
        pieceBId -> "b"
        else -> null
    }

    /** Konkrete Navigationsroute zu einem Tutorial-Screen (null = Screen bleibt, wie er ist). */
    fun routeFor(screen: TutorialScreen): String? = when (screen) {
        TutorialScreen.WELCOME, TutorialScreen.REPERTOIRE -> Screen.Repertoire.route
        TutorialScreen.ADD_PIECE -> Screen.AddEditPiece.route()
        TutorialScreen.PIECE_A -> pieceAId?.let { Screen.PieceDetail.route(it) }
        TutorialScreen.SHEET_A -> pieceAId?.let { Screen.SheetViewer.route(it) }
        TutorialScreen.PIECE_B -> pieceBId?.let { Screen.PieceDetail.route(it) }
        TutorialScreen.SHEET_B -> pieceBId?.let { Screen.SheetViewer.route(it) }
        TutorialScreen.CHORDS -> Screen.ChordSheets.route
        TutorialScreen.UG_IMPORT -> Screen.UgImport.route
        TutorialScreen.FINISH -> null
    }

    // ── Zielrechtecke der hervorgehobenen Elemente ────────────────────────────
    private val targets = mutableStateMapOf<String, Rect>()

    fun targetRect(id: String): Rect? = targets[id]

    fun registerTarget(id: String, rect: Rect) {
        if (targets[id] != rect) targets[id] = rect
    }

    fun unregisterTarget(id: String) {
        targets.remove(id)
    }
}

/**
 * Markiert ein Composable als mögliches Tutorial-Ziel.
 * - meldet seine Position an den [TutorialManager] (das Overlay schneidet dort ein Loch in die Abdunkelung),
 * - scrollt es in den sichtbaren Bereich, sobald es der aktuelle Tutorial-Schritt ist.
 * Mit id = null passiert nichts (praktisch für optionale Ziele).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tutorialTarget(id: String?): Modifier =
    if (id == null) this else composed {
        val requester = remember { BringIntoViewRequester() }
        val isCurrent = TutorialManager.currentStep?.target == id
        LaunchedEffect(isCurrent) {
            if (isCurrent) {
                delay(350)
                requester.bringIntoView()
            }
        }
        DisposableEffect(id) { onDispose { TutorialManager.unregisterTarget(id) } }
        this
            .bringIntoViewRequester(requester)
            .onGloballyPositioned { TutorialManager.registerTarget(id, it.boundsInWindow()) }
    }
