package de.klaviatur.ui.screens.stats

/** Abschnitte des Statistik-Screens (für das Ausblenden leerer Überschriften). */
enum class StatSection { OVERVIEW, BEHAVIOR, PIECES, ACHIEVEMENTS }

/**
 * Alle Kacheln des Statistik-Screens. Die Namen werden in den Einstellungen gespeichert –
 * bestehende Einträge also nicht umbenennen.
 */
enum class StatTile(val section: StatSection) {
    OVERVIEW_PIECES(StatSection.OVERVIEW),
    OVERVIEW_TIME(StatSection.OVERVIEW),
    OVERVIEW_STREAK(StatSection.OVERVIEW),
    OVERVIEW_SESSIONS(StatSection.OVERVIEW),

    WEEKLY_GOAL(StatSection.BEHAVIOR),
    KPIS(StatSection.BEHAVIOR),
    STREAKS(StatSection.BEHAVIOR),
    ACTIVITY(StatSection.BEHAVIOR),
    HEATMAP(StatSection.BEHAVIOR),
    WEEKDAYS(StatSection.BEHAVIOR),

    MOST_PRACTICED(StatSection.PIECES),
    NEGLECTED(StatSection.PIECES),
    SECTION_PROGRESS(StatSection.PIECES),
    SCOPE(StatSection.PIECES),
    ADDED_PER_MONTH(StatSection.PIECES),
    STATUS(StatSection.PIECES),
    EPOCHS(StatSection.PIECES),
    DIFFICULTY(StatSection.PIECES),
    EPOCH_MATRIX(StatSection.PIECES),
    TOP_COMPOSERS(StatSection.PIECES),
    COMPOSER_STATUS(StatSection.PIECES),

    RECORDS(StatSection.ACHIEVEMENTS),
    BADGES(StatSection.ACHIEVEMENTS)
}
