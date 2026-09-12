package de.klaviatur.util

object UgConverter {
    /**
     * Converts Ultimate Guitar (chord-over-lyrics) format to ChordPro.
     */
    fun convert(ugText: String): String {
        val lines = ugText.lines()
        val chordPro = StringBuilder()
        
        var i = 0
        while (i < lines.size) {
            val currentLine = lines[i]
            val nextLine = if (i + 1 < lines.size) lines[i + 1] else null
            
            if (isChordLine(currentLine) && nextLine != null && !isChordLine(nextLine)) {
                // Merge chord line into the lyric line
                chordPro.append(mergeLines(currentLine, nextLine)).append("\n")
                i += 2 // Skip next line as it was merged
            } else {
                // Keep line as is (directives or single lines)
                if (isChordLine(currentLine)) {
                    chordPro.append(formatPureChordLine(currentLine)).append("\n")
                } else {
                    chordPro.append(currentLine).append("\n")
                }
                i++
            }
        }
        
        return chordPro.toString()
    }

    private fun isChordLine(line: String): Boolean {
        if (line.isBlank()) return false
        // Chords usually contain A-G, #, b, m, 7, maj, etc. and lots of spaces
        val words = line.trim().split(Regex("\\s+"))
        val chordRegex = Regex("^[A-G][b#]?(m|maj|dim|aug|sus)?([0-9])?(add[0-9])?(/[A-G][b#]?)?$")
        
        return words.all { it.matches(chordRegex) || it.isEmpty() }
    }

    private fun mergeLines(chordLine: String, lyricLine: String): String {
        val result = StringBuilder()
        val chords = mutableListOf<Pair<Int, String>>()
        
        // Extract chords and their positions
        var currentPos = 0
        while (currentPos < chordLine.length) {
            if (chordLine[currentPos] != ' ') {
                val end = chordLine.indexOf(' ', currentPos).let { if (it == -1) chordLine.length else it }
                chords.add(currentPos to chordLine.substring(currentPos, end))
                currentPos = end
            } else {
                currentPos++
            }
        }

        var lyricIdx = 0
        chords.forEach { (pos, chord) ->
            // Add lyrics before this chord
            while (lyricIdx < pos && lyricIdx < lyricLine.length) {
                result.append(lyricLine[lyricIdx])
                lyricIdx++
            }
            // Add the chord
            result.append("[").append(chord).append("]")
        }
        
        // Add remaining lyrics
        if (lyricIdx < lyricLine.length) {
            result.append(lyricLine.substring(lyricIdx))
        }
        
        return result.toString()
    }

    private fun formatPureChordLine(line: String): String {
        val result = StringBuilder()
        var currentPos = 0
        while (currentPos < line.length) {
            if (line[currentPos] != ' ') {
                val end = line.indexOf(' ', currentPos).let { if (it == -1) line.length else it }
                result.append("[").append(line.substring(currentPos, end)).append("]")
                currentPos = end
            } else {
                result.append(" ")
                currentPos++
            }
        }
        return result.toString()
    }
}
