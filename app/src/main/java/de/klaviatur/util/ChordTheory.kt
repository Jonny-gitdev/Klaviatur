package de.klaviatur.util

import de.klaviatur.data.model.ChordInstrument

/** Zerlegter Akkordname, z. B. "F#m7/C#" -> root "F", accidental '#', quality "m7", bass "C#". */
data class ChordParts(
    val root: String,        // "C".."B"
    val accidental: Char?,   // '#', 'b' oder null
    val quality: String,     // Rest zwischen Grundton und Bass, unverändert
    val bassRoot: String?,
    val bassAccidental: Char?
) {
    val rootPc: Int get() = ChordTheory.pcOf(root, accidental)
    val bassPc: Int? get() = bassRoot?.let { ChordTheory.pcOf(it, bassAccidental) }
}

/** Ein Griffbild für Gitarre/Ukulele. frets: pro Saite (tief -> hoch); -1 = gedämpft, 0 = leer. */
data class FretVoicing(
    val frets: List<Int>,
    val baseFret: Int,       // erster angezeigter Bund (1 = Nullposition sichtbar)
    val barreFret: Int?,     // Bund des Barrés oder null
    val barreFrom: Int,      // Saitenindex (inkl.)
    val barreTo: Int,        // Saitenindex (inkl.)
    val fingers: Int
)

/** Ein Klavier-Griff: absolute Halbtonpositionen ab dem linken C der Anzeige. */
data class PianoVoicing(
    val notes: List<Int>,    // aufsteigend
    val rootNote: Int?,      // Position des Grundtons (oder null)
    val octaves: Int         // anzuzeigende Oktaven (ab C)
)

object ChordTheory {
    private val SHARP = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val NATURAL_PC = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)

    private val CHORD_REGEX = Regex("^([A-G])([#b]?)(.*?)(?:/([A-G])([#b]?))?$")

    fun pcOf(root: String, accidental: Char?): Int {
        val base = NATURAL_PC[root] ?: 0
        return when (accidental) {
            '#' -> (base + 1) % 12
            'b' -> (base + 11) % 12
            else -> base
        }
    }

    fun noteName(pc: Int, preferFlat: Boolean): String =
        (if (preferFlat) FLAT else SHARP)[((pc % 12) + 12) % 12]

    fun split(name: String): ChordParts? {
        val m = CHORD_REGEX.matchEntire(name.trim()) ?: return null
        val (root, acc, quality, bass, bassAcc) = m.destructured
        return ChordParts(
            root = root,
            accidental = acc.firstOrNull(),
            quality = quality,
            bassRoot = bass.ifEmpty { null },
            bassAccidental = bassAcc.firstOrNull()
        )
    }

    // ── Transponieren ────────────────────────────────────────────────────────

    private fun isMinorQuality(q: String): Boolean =
        (q.startsWith("m") && !q.startsWith("maj")) || q.startsWith("-") || q.startsWith("min")

    /** Schreibweise für einen Ton nach dem Transponieren. */
    private fun spell(pc: Int, originalAcc: Char?, minor: Boolean): String {
        val flat = when (originalAcc) {
            '#' -> false
            'b' -> true
            else -> if (minor) pc in intArrayOf(3, 10) else pc in intArrayOf(1, 3, 8, 10)
        }
        return noteName(pc, flat)
    }

    fun transposeChord(name: String, semitones: Int): String {
        if (semitones % 12 == 0) return name
        val p = split(name) ?: return name
        val minor = isMinorQuality(p.quality)
        val shift = ((semitones % 12) + 12) % 12
        val newRoot = spell((p.rootPc + shift) % 12, p.accidental, minor)
        val bass = p.bassPc?.let { b ->
            val pc = (b + shift) % 12
            val flat = when {
                newRoot.endsWith("b") -> true
                newRoot.endsWith("#") -> false
                // natürlicher Grundton: Tonarten mit Been (F-Dur, d-/g-/c-/f-Moll) -> Been
                else -> if (minor) newRoot[0] in "DGCF" else newRoot[0] == 'F'
            }
            noteName(pc, flat)
        }
        return newRoot + p.quality + (if (bass != null) "/$bass" else "")
    }

    fun transposeSheet(sheet: ChordSheet, semitones: Int): ChordSheet {
        if (semitones % 12 == 0) return sheet
        return sheet.copy(lines = sheet.lines.map { line ->
            line.copy(segments = line.segments.map { seg ->
                seg.copy(chord = seg.chord?.let { transposeChord(it, semitones) })
            })
        })
    }

    // ── Akkord-Bedeutung ─────────────────────────────────────────────────────

    /** required = müssen klingen, optional = dürfen (z. B. die Quinte) – jeweils Halbtöne über dem Grundton. */
    data class Spec(val tones: List<Int>, val required: Set<Int>, val optional: Set<Int>)

    private fun spec(vararg tones: Int, required: IntArray, optional: IntArray = intArrayOf()) =
        Spec(tones.toList(), required.toSet(), optional.toSet())

    private val QUALITIES: Map<String, Spec> = mapOf(
        "" to spec(0, 4, 7, required = intArrayOf(0, 4), optional = intArrayOf(7)),
        "m" to spec(0, 3, 7, required = intArrayOf(0, 3), optional = intArrayOf(7)),
        "5" to spec(0, 7, required = intArrayOf(0, 7)),
        "dim" to spec(0, 3, 6, required = intArrayOf(0, 3, 6)),
        "dim7" to spec(0, 3, 6, 9, required = intArrayOf(0, 3, 6, 9)),
        "m7b5" to spec(0, 3, 6, 10, required = intArrayOf(0, 3, 6, 10)),
        "aug" to spec(0, 4, 8, required = intArrayOf(0, 4, 8)),
        "aug7" to spec(0, 4, 8, 10, required = intArrayOf(0, 4, 8, 10)),
        "sus2" to spec(0, 2, 7, required = intArrayOf(0, 2), optional = intArrayOf(7)),
        "sus4" to spec(0, 5, 7, required = intArrayOf(0, 5), optional = intArrayOf(7)),
        "7sus4" to spec(0, 5, 7, 10, required = intArrayOf(0, 5, 10), optional = intArrayOf(7)),
        "6" to spec(0, 4, 7, 9, required = intArrayOf(0, 4, 9), optional = intArrayOf(7)),
        "m6" to spec(0, 3, 7, 9, required = intArrayOf(0, 3, 9), optional = intArrayOf(7)),
        "7" to spec(0, 4, 7, 10, required = intArrayOf(0, 4, 10), optional = intArrayOf(7)),
        "maj7" to spec(0, 4, 7, 11, required = intArrayOf(0, 4, 11), optional = intArrayOf(7)),
        "m7" to spec(0, 3, 7, 10, required = intArrayOf(0, 3, 10), optional = intArrayOf(7)),
        "mmaj7" to spec(0, 3, 7, 11, required = intArrayOf(0, 3, 11), optional = intArrayOf(7)),
        "9" to spec(0, 4, 7, 10, 14, required = intArrayOf(0, 4, 10, 2), optional = intArrayOf(7)),
        "m9" to spec(0, 3, 7, 10, 14, required = intArrayOf(0, 3, 10, 2), optional = intArrayOf(7)),
        "maj9" to spec(0, 4, 7, 11, 14, required = intArrayOf(0, 4, 11, 2), optional = intArrayOf(7)),
        "add9" to spec(0, 4, 7, 14, required = intArrayOf(0, 4, 2), optional = intArrayOf(7)),
        "madd9" to spec(0, 3, 7, 14, required = intArrayOf(0, 3, 2), optional = intArrayOf(7)),
        "69" to spec(0, 4, 7, 9, 14, required = intArrayOf(0, 4, 9, 2), optional = intArrayOf(7)),
        "11" to spec(0, 4, 7, 10, 14, 17, required = intArrayOf(0, 4, 10, 5), optional = intArrayOf(7, 2)),
        "m11" to spec(0, 3, 7, 10, 14, 17, required = intArrayOf(0, 3, 10, 5), optional = intArrayOf(7, 2)),
        "13" to spec(0, 4, 7, 10, 14, 21, required = intArrayOf(0, 4, 10, 9), optional = intArrayOf(7, 2)),
        "m13" to spec(0, 3, 7, 10, 14, 21, required = intArrayOf(0, 3, 10, 9), optional = intArrayOf(7, 2)),
        "7b9" to spec(0, 4, 7, 10, 13, required = intArrayOf(0, 4, 10, 1), optional = intArrayOf(7)),
        "7#9" to spec(0, 4, 7, 10, 15, required = intArrayOf(0, 4, 10, 3), optional = intArrayOf(7)),
        "7b5" to spec(0, 4, 6, 10, required = intArrayOf(0, 4, 6, 10)),
    )

    private fun normalizeQuality(q: String): String {
        var s = q.replace("(", "").replace(")", "").replace(" ", "")
        s = s.replace("min", "m").replace("MAJ", "maj").replace("Maj", "maj")
        s = s.replace("Δ", "maj7").replace("°", "dim").replace("ø", "m7b5")
        s = s.replace("+", "aug").replace("6/9", "69")
        if (s.startsWith("-")) s = "m" + s.substring(1)
        // "M7" (großes M) = maj7, "mM7" = mmaj7
        if (s.startsWith("M")) s = "maj" + s.substring(1)
        s = s.replace("mM", "mmaj")
        if (s == "sus") s = "sus4"
        if (s == "7#5" || s == "7+5") s = "aug7"
        if (s == "maj") s = ""
        if (s == "m7-5") s = "m7b5"
        return s
    }

    fun specFor(parts: ChordParts): Spec? {
        var q = normalizeQuality(parts.quality)
        while (true) {
            QUALITIES[q]?.let { return it }
            if (q.isEmpty()) return null
            q = q.dropLast(1)
            if (q.isEmpty()) return null // unbekannte Qualität – lieber nichts zeigen als Falsches
        }
    }

    // ── Klavier ──────────────────────────────────────────────────────────────

    fun pianoVoicings(chordName: String): List<PianoVoicing> {
        val parts = split(chordName) ?: return emptyList()
        val spec = specFor(parts) ?: return emptyList()
        val root = parts.rootPc
        val bass = parts.bassPc

        val stack = spec.tones.map { root + it }.sorted()           // Grundstellung, aufsteigend
        val raw = mutableListOf<List<Int>>()
        for (inv in stack.indices) {
            val rotated = stack.drop(inv) + stack.take(inv).map { it + 12 }
            raw += rotated
        }
        // Bei Slash-Akkorden: Umkehrungen mit passendem Bass zuerst, dann Bass-Ton zusätzlich links
        val result = mutableListOf<List<Int>>()
        if (bass != null && bass != root % 12) {
            raw.filter { it.first() % 12 == bass }.forEach { result += it }
            val base = raw.first()
            var b = bass
            while (b >= base.first()) b -= 12
            result += listOf(b) + base
        } else {
            result += raw
        }
        return result.distinct().map { notes ->
            var n = notes
            while (n.min() >= 12) n = n.map { it - 12 }
            while (n.min() < 0) n = n.map { it + 12 }
            val sorted = n.sorted()
            val octaves = if (sorted.max() < 24) 2 else 3
            PianoVoicing(sorted, sorted.firstOrNull { it % 12 == root % 12 }, octaves)
        }.distinctBy { it.notes }
    }

    // ── Gitarre / Ukulele ────────────────────────────────────────────────────

    private val GUITAR_MIDI = intArrayOf(40, 45, 50, 55, 59, 64)   // E2 A2 D3 G3 B3 E4
    private val UKULELE_MIDI = intArrayOf(67, 60, 64, 69)          // G4 C4 E4 A4 (re-entrant)

    fun fretVoicings(chordName: String, instrument: ChordInstrument, max: Int = 5): List<FretVoicing> {
        val parts = split(chordName) ?: return emptyList()
        val spec = specFor(parts) ?: return emptyList()
        val tuning = when (instrument.id) {
            ChordInstrument.GUITAR.id -> GUITAR_MIDI
            ChordInstrument.UKULELE.id -> UKULELE_MIDI
            else -> return emptyList()
        }
        val root = parts.rootPc
        val bass = parts.bassPc
        val required = spec.required.map { (root + it) % 12 }.toMutableSet()
        if (bass != null) required += bass
        val allowed = (required + spec.optional.map { (root + it) % 12 } + spec.tones.map { (root + it) % 12 }).toSet()
        val fifthPc = (root + 7) % 12

        val isGuitar = instrument.id == ChordInstrument.GUITAR.id
        var found = search(tuning, required, allowed, fifthPc, instrument, bassPc = bass ?: root, strictBass = isGuitar)
        if (found.isEmpty() && isGuitar) {
            found = search(tuning, required, allowed, fifthPc, instrument, bassPc = bass ?: root, strictBass = false)
        }
        val curated = curatedFor(parts, instrument)
        val generated = pickDiverse(found, max)
        return (curated + generated.filter { g -> curated.none { it.frets == g.frets } }).take(max)
    }

    // ── Übliche Standard-Griffe (zuerst gezeigt; Generator liefert die weiteren Varianten) ──────

    private val GUITAR_CURATED = mapOf(
        "C" to "x32010", "Cmaj7" to "x32000", "C7" to "x32310", "Cadd9" to "x32030",
        "D" to "xx0232", "Dm" to "xx0231", "D7" to "xx0212", "Dmaj7" to "xx0222", "Dm7" to "xx0211",
        "Dsus4" to "xx0233", "Dsus2" to "xx0230",
        "E" to "022100", "Em" to "022000", "E7" to "020100", "Em7" to "022030", "Emaj7" to "021100", "Esus4" to "022200",
        "Fmaj7" to "xx3210", "F" to "133211",
        "G" to "320003", "G7" to "320001", "Gmaj7" to "320002", "Gsus4" to "330013",
        "A" to "x02220", "Am" to "x02210", "A7" to "x02020", "Am7" to "x02010", "Amaj7" to "x02120",
        "Asus2" to "x02200", "Asus4" to "x02230",
        "B7" to "x21202", "Bm" to "x24432", "B" to "x24442"
    )

    private val UKULELE_CURATED = mapOf(
        "C" to "0003", "Cm" to "0333", "C7" to "0001", "Cmaj7" to "0002", "Cadd9" to "0203",
        "D" to "2220", "Dm" to "2210", "D7" to "2223", "Dm7" to "2213", "Dmaj7" to "2224",
        "E" to "4442", "Em" to "0432", "E7" to "1202",
        "F" to "2010", "Fm" to "1013", "F7" to "2313", "Fmaj7" to "2413",
        "G" to "0232", "Gm" to "0231", "G7" to "0212", "Gmaj7" to "0222",
        "A" to "2100", "Am" to "2000", "A7" to "0100", "Am7" to "0000", "Amaj7" to "1100",
        "B" to "4322", "Bm" to "4222", "B7" to "2322", "Bb" to "3211"
    )

    internal fun curatedTable(instrument: ChordInstrument) =
        if (instrument.id == ChordInstrument.GUITAR.id) GUITAR_CURATED else UKULELE_CURATED

    private fun parseFrets(code: String): List<Int> {
        // Zweistellige Bünde werden hier nicht gebraucht (nur Griffe bis Bund 9)
        return code.map { if (it == 'x') -1 else it.digitToInt() }
    }

    private fun curatedFor(parts: ChordParts, instrument: ChordInstrument): List<FretVoicing> {
        if (parts.bassRoot != null) return emptyList()
        val q = normalizeQuality(parts.quality)
        val isGuitar = instrument.id == ChordInstrument.GUITAR.id
        val table = curatedTable(instrument)
        val tuning = if (isGuitar) GUITAR_MIDI else UKULELE_MIDI
        val code = table[SHARP[parts.rootPc] + q] ?: table[FLAT[parts.rootPc] + q] ?: return emptyList()
        val frets = parseFrets(code)
        if (frets.size != tuning.size) return emptyList()
        val fretted = frets.filter { it > 0 }
        val minF = fretted.minOrNull() ?: 0
        val maxF = fretted.maxOrNull() ?: 0
        var barreFret: Int? = null
        var bFrom = 0
        var bTo = 0
        val atMin = frets.indices.filter { frets[it] == minF && minF > 0 }
        if (atMin.size >= 2) {
            val needed = if (isGuitar) 4 else 3
            val a = atMin.min(); val b = atMin.max()
            if (b - a + 1 >= needed && (a..b).none { frets[it] in 0 until minF }) { barreFret = minF; bFrom = a; bTo = b }
        }
        val fingers = if (barreFret != null) 1 + fretted.count { it > minF } else fretted.size
        return listOf(FretVoicing(frets, if (maxF <= 4) 1 else minF, barreFret, bFrom, bTo, fingers))
    }

    private data class Scored(val v: FretVoicing, val score: Double, val position: Int)

    private fun search(
        tuning: IntArray, required: Set<Int>, allowed: Set<Int>, fifthPc: Int,
        instrument: ChordInstrument, bassPc: Int, strictBass: Boolean
    ): List<Scored> {
        val n = tuning.size
        val maxFret = 12
        val out = mutableListOf<Scored>()
        val frets = IntArray(n)
        val isGuitar = instrument.id == ChordInstrument.GUITAR.id
        val allowMutes = isGuitar
        val minSounded = 4

        fun finish() {
            val sounded = frets.indices.filter { frets[it] >= 0 }
            if (sounded.size < minSounded) return
            val pcs = sounded.map { (tuning[it] + frets[it]) % 12 }.toSet()
            if (!pcs.containsAll(required)) return
            if (strictBass) {
                val lowest = sounded.minByOrNull { tuning[it] + frets[it] }!!
                if ((tuning[lowest] + frets[lowest]) % 12 != bassPc) return
            }
            val fretted = sounded.filter { frets[it] > 0 }
            val minF = fretted.minOfOrNull { frets[it] } ?: 0
            val maxF = fretted.maxOfOrNull { frets[it] } ?: 0
            val hasOpen = sounded.any { frets[it] == 0 }
            // Leersaiten sind nur nahe am Sattel sinnvoll
            if (hasOpen && maxF > 5) return

            // Der Zeigefinger (tiefster Bund) kann mehrere Saiten nur als zusammenhängendes Barré greifen:
            // keine Leersaite / kein tieferer Bund dazwischen.
            val atMin = fretted.filter { frets[it] == minF }
            var barreFret: Int? = null
            var bFrom = 0
            var bTo = 0
            var minGroupFingers = atMin.size
            if (atMin.size >= 2) {
                val a2 = atMin.min(); val b2 = atMin.max()
                val blocked = (a2..b2).any { frets[it] in 0 until minF }
                if (blocked) return
                minGroupFingers = 1
                val span = b2 - a2 + 1
                val needed = if (isGuitar) 4 else 3
                if (span >= needed) { barreFret = minF; bFrom = a2; bTo = b2 }
            }
            val fingers = minGroupFingers + fretted.count { frets[it] > minF }
            if (fingers > 4) return

            val muted = n - sounded.size
            var score = 0.0
            score += minF * 0.6
            score += muted * 2.0
            score += fingers * 0.5
            if (barreFret != null) score += 1.5
            score += (maxF - minF) * 0.5
            if (pcs.contains(fifthPc)) score -= 1.0
            val pos = if (hasOpen && maxF <= 4) 0 else minF
            val base = if (maxF <= 4) 1 else minF
            out += Scored(FretVoicing(frets.toList(), base, barreFret, bFrom, bTo, fingers), score, pos)
        }

        fun rec(i: Int, lo: Int, hi: Int) {
            if (i == n) { finish(); return }
            // gedämpft: nur als zusammenhängendes tiefes Ende (Gitarre)
            if (allowMutes && (i == 0 || frets[i - 1] == -1)) {
                frets[i] = -1
                rec(i + 1, lo, hi)
            }
            for (f in 0..maxFret) {
                if ((tuning[i] + f) % 12 !in allowed) continue
                var nlo = lo; var nhi = hi
                if (f > 0) {
                    nlo = if (lo == 0) f else minOf(lo, f)
                    nhi = maxOf(hi, f)
                    if (nhi - nlo > 3) continue
                }
                frets[i] = f
                rec(i + 1, nlo, nhi)
            }
        }
        rec(0, 0, 0)
        return out
    }

    private fun pickDiverse(all: List<Scored>, max: Int): List<FretVoicing> {
        val sorted = all.sortedWith(compareBy({ it.score }, { it.v.frets.joinToString(",") }))
        val picked = mutableListOf<Scored>()
        val best = sorted.firstOrNull()?.score ?: return emptyList()
        for (c in sorted) {
            if (picked.size >= max) break
            if (c.score > best + 9.0) break
            if (picked.none { kotlin.math.abs(it.position - c.position) < 2 }) picked += c
        }
        return picked.sortedBy { it.position }.map { it.v }
    }
}
