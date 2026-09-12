package de.klaviatur.util

data class ChordSheet(
    val title: String? = null,
    val artist: String? = null,
    val lines: List<ChordLine>
)

data class ChordLine(
    val segments: List<ChordSegment>
)

data class ChordSegment(
    val chord: String? = null,
    val text: String = ""
)

object ChordProParser {
    fun parse(content: String): ChordSheet {
        val lines = mutableListOf<ChordLine>()
        var title: String? = null
        var artist: String? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("{title:") || trimmed.startsWith("{t:") -> {
                    title = trimmed.substringAfter(":").substringBefore("}").trim()
                }
                trimmed.startsWith("{artist:") || trimmed.startsWith("{a:") -> {
                    artist = trimmed.substringAfter(":").substringBefore("}").trim()
                }
                trimmed.startsWith("{") -> {
                    // Ignore other directives for now or handle them as comments
                }
                else -> {
                    lines.add(parseLine(line))
                }
            }
        }
        return ChordSheet(title, artist, lines)
    }

    private fun parseLine(line: String): ChordLine {
        val segments = mutableListOf<ChordSegment>()
        var currentChord: String? = null
        var currentText = StringBuilder()
        
        var i = 0
        while (i < line.length) {
            if (line[i] == '[') {
                // Save current segment before starting a new chord
                if (currentChord != null || currentText.isNotEmpty()) {
                    segments.add(ChordSegment(currentChord, currentText.toString()))
                    currentText = StringBuilder()
                }
                
                val end = line.indexOf(']', i)
                if (end != -1) {
                    currentChord = line.substring(i + 1, end)
                    i = end + 1
                } else {
                    currentText.append(line[i])
                    i++
                }
            } else {
                currentText.append(line[i])
                i++
            }
        }
        
        // Add last segment
        if (currentChord != null || currentText.isNotEmpty()) {
            segments.add(ChordSegment(currentChord, currentText.toString()))
        }
        
        return ChordLine(segments)
    }
}
