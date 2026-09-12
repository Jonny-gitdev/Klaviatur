package de.klaviatur.ui

sealed class Screen(val route: String) {
    // Bottom nav tabs
    object Repertoire : Screen("repertoire")
    object Sessions   : Screen("sessions")
    object Lists      : Screen("lists")
    object Stats      : Screen("stats")
    object ChordSheets : Screen("chord_sheets")

    // Detail screens
    object PieceDetail : Screen("piece/{pieceId}") {
        fun route(id: Long) = "piece/$id"
    }
    object AddEditPiece : Screen("piece_edit?pieceId={pieceId}") {
        fun route(id: Long? = null) = if (id != null) "piece_edit?pieceId=$id" else "piece_edit"
    }
    object ListDetail : Screen("list/{listId}") {
        fun route(id: Long) = "list/$id"
    }
    object AddEditList : Screen("list_edit?listId={listId}") {
        fun route(id: Long? = null) = if (id != null) "list_edit?listId=$id" else "list_edit"
    }
    object AddSession : Screen("session_add")
    object PracticeMode : Screen("practice_mode")
    object Settings : Screen("settings")

    object ChordSheetDetail : Screen("chord_sheet/{type}/{id}") {
        fun route(type: String, id: String) = "chord_sheet/$type/$id"
    }
    object UgImport : Screen("ug_import")
    object ManualEntry : Screen("manual_entry")
}
