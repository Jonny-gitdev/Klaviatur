package de.klaviatur.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Zwischenablage für Inhalte, die per "Teilen" von anderen Apps (z. B. Ultimate Guitar) ankommen. */
object ShareInbox {
    /** Geteilter Text; wird vom Import-Screen verbraucht. */
    var pendingText by mutableStateOf<String?>(null)
}

/**
 * Konvertiert geteilten Text (z. B. aus Ultimate Guitar) in ChordPro.
 * Kein Web-Scraping, nutzt ausschließlich den geteilten Text.
 */
object UgShareImporter {
    class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Chord-über-Text -> ChordPro. */
    fun toChordPro(text: String): String = UgConverter.convert(normalizeUgContent(text))

    fun convertPlainText(text: String): String {
        val chordPro = toChordPro(text)
        if (!chordPro.contains('[')) throw ImportException("Im geteilten Text wurden keine Akkorde gefunden.")
        return chordPro
    }

    /** Titel-Vorschlag aus geteiltem Text. */
    fun suggestTitle(text: String): String {
        val lines = text.lines().map { it.trim() }
        val firstHeader = lines.indexOfFirst { it.startsWith("[") }
        return lines.drop(if (firstHeader >= 0) firstHeader + 1 else 0).firstOrNull {
            it.isNotEmpty() && !it.startsWith("[") && !it.startsWith("{") && !UgConverter.isChordLine(it) && !it.contains("http")
        }.orEmpty()
    }

    /** UG-Markup -> Chord-über-Text mit {comment:}-Abschnittsüberschriften. */
    internal fun normalizeUgContent(content: String): String {
        val text = content
            .replace("\r\n", "\n").replace('\r', '\n')
            .replace("[tab]", "").replace("[/tab]", "")
            .replace("[ch]", "").replace("[/ch]", "")
        val header = Regex("^\\s*\\[([^\\]]+)]\\s*(\\(.*\\))?\\s*$")
        return text.lines().joinToString("\n") { line ->
            header.matchEntire(line)?.let { "{comment: ${(it.groupValues[1] + " " + it.groupValues[2]).trim()}}" }
                ?: line.replace('[', '(').replace(']', ')')
        }
    }
}
