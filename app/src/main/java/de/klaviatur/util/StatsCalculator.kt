package de.klaviatur.util

import de.klaviatur.data.model.Epoch
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.data.model.PracticeSession
import de.klaviatur.data.model.ProgressLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Reine Rechenlogik für den Statistik-Screen (keine Android-/Compose-Abhängigkeiten).
 * Alle Tage werden in der lokalen Zeitzone ausgewertet, Wochen beginnen am Montag.
 */

enum class StatsRange(val days: Int?) {
    WEEK(7), MONTH(30), YEAR(365), ALL(null)
}

/** Ein Balken: bei [monthly] steht [start] für den Monatsersten, sonst für den Tag. */
data class Bucket(val start: LocalDate, val value: Int, val monthly: Boolean)

data class RangeKpis(
    val totalMinutes: Int,
    val sessionCount: Int,
    val activeDays: Int,
    val avgSessionMinutes: Int,
    val longestSessionMinutes: Int,
    val sessionsPerWeek: Float,
    /** Prozentuale Veränderung gegenüber dem gleich langen Zeitraum davor; null bei [StatsRange.ALL] oder ohne Vergleichsdaten. */
    val trendPercent: Int?
)

data class WeekdayStat(val day: DayOfWeek, val totalMinutes: Int, val avgMinutes: Float)

data class HeatCell(val date: LocalDate, val minutes: Int)

data class WeekTrend(val thisWeekMinutes: Int, val previousWeekMinutes: Int) {
    val percent: Int? get() =
        if (previousWeekMinutes > 0) ((thisWeekMinutes - previousWeekMinutes) * 100f / previousWeekMinutes).toInt() else null
}

data class PieceUsage(val piece: Piece, val sessionCount: Int, val approxMinutes: Int)

data class PieceNeglect(val piece: Piece, val daysSince: Int, val neverPracticed: Boolean)

data class PieceMastery(val piece: Piece, val barsByLevel: Map<ProgressLevel, Int>, val score: Float)

data class RepertoireScope(
    val totalBars: Int,
    val keyCount: Int,
    val tags: List<Pair<String, Int>>
)

data class ComposerStatus(val composer: String, val total: Int, val byStatus: Map<PieceStatus, Int>)

enum class BadgeKind { STREAK, HOURS, SESSIONS, REPERTOIRE, SAFE_SECTION, MARATHON }

data class Badge(val kind: BadgeKind, val target: Int, val current: Int) {
    val unlocked: Boolean get() = current >= target
}

data class Records(
    val longestSessionMinutes: Int?, val longestSessionDate: LocalDate?,
    val bestDayMinutes: Int?, val bestDayDate: LocalDate?,
    val bestWeekMinutes: Int?, val bestWeekStart: LocalDate?,
    val longestStreak: Int,
    val mostPiecesInSession: Int?, val mostPiecesDate: LocalDate?
)

object StatsCalculator {

    const val NEGLECT_THRESHOLD_DAYS = 30

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun minutesPerDay(sessions: List<PracticeSession>): Map<LocalDate, Int> =
        sessions.groupBy { toLocalDate(it.dateMillis) }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ── Zeitraum ──────────────────────────────────────────────────────────────

    fun rangeStart(range: StatsRange, sessions: List<PracticeSession>, today: LocalDate): LocalDate =
        range.days?.let { today.minusDays(it - 1L) }
            ?: sessions.minOfOrNull { toLocalDate(it.dateMillis) }
            ?: today

    fun inRange(sessions: List<PracticeSession>, range: StatsRange, today: LocalDate): List<PracticeSession> {
        val start = rangeStart(range, sessions, today)
        return sessions.filter {
            val d = toLocalDate(it.dateMillis)
            !d.isBefore(start) && !d.isAfter(today)
        }
    }

    // ── Verhalten ─────────────────────────────────────────────────────────────

    /** Balken für die Aktivitätsgrafik: Tage (7 / 30 Tage) oder Monate (Jahr / Gesamt). */
    fun activityBuckets(sessions: List<PracticeSession>, range: StatsRange, today: LocalDate): List<Bucket> {
        val perDay = minutesPerDay(sessions)
        return when (range) {
            StatsRange.WEEK, StatsRange.MONTH -> {
                val start = rangeStart(range, sessions, today)
                (0..ChronoUnit.DAYS.between(start, today)).map { off ->
                    val d = start.plusDays(off)
                    Bucket(d, perDay[d] ?: 0, monthly = false)
                }
            }
            StatsRange.YEAR, StatsRange.ALL -> {
                val endMonth = YearMonth.from(today)
                val firstMonth = if (range == StatsRange.YEAR) {
                    endMonth.minusMonths(11)
                } else {
                    val first = sessions.minOfOrNull { toLocalDate(it.dateMillis) }?.let { YearMonth.from(it) } ?: endMonth
                    maxOf(first, endMonth.minusMonths(35))   // höchstens 36 Balken
                }
                val perMonth = perDay.entries.groupBy({ YearMonth.from(it.key) }, { it.value }).mapValues { it.value.sum() }
                generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(endMonth) }
                    .map { Bucket(it.atDay(1), perMonth[it] ?: 0, monthly = true) }.toList()
            }
        }
    }

    fun kpis(allSessions: List<PracticeSession>, range: StatsRange, today: LocalDate): RangeKpis {
        val sessions = inRange(allSessions, range, today)
        val total = sessions.sumOf { it.durationMinutes }
        val start = rangeStart(range, allSessions, today)
        val periodDays = (ChronoUnit.DAYS.between(start, today) + 1).coerceAtLeast(1)

        val trend = range.days?.let { len ->
            val prevEnd = start.minusDays(1)
            val prevStart = prevEnd.minusDays(len - 1L)
            val prev = allSessions.filter {
                val d = toLocalDate(it.dateMillis); !d.isBefore(prevStart) && !d.isAfter(prevEnd)
            }.sumOf { it.durationMinutes }
            if (prev > 0) ((total - prev) * 100f / prev).toInt() else null
        }

        return RangeKpis(
            totalMinutes = total,
            sessionCount = sessions.size,
            activeDays = sessions.map { toLocalDate(it.dateMillis) }.distinct().size,
            avgSessionMinutes = if (sessions.isEmpty()) 0 else (total.toFloat() / sessions.size).toInt(),
            longestSessionMinutes = sessions.maxOfOrNull { it.durationMinutes } ?: 0,
            sessionsPerWeek = sessions.size * 7f / periodDays,
            trendPercent = trend
        )
    }

    /** Durchschnittliche Minuten je Wochentag im Zeitraum (Summe / Anzahl dieses Wochentags im Zeitraum). */
    fun weekdayStats(allSessions: List<PracticeSession>, range: StatsRange, today: LocalDate): List<WeekdayStat> {
        val start = rangeStart(range, allSessions, today)
        val occurrences = IntArray(7)
        var d = start
        while (!d.isAfter(today)) { occurrences[d.dayOfWeek.value - 1]++; d = d.plusDays(1) }
        val totals = IntArray(7)
        inRange(allSessions, range, today).forEach { totals[toLocalDate(it.dateMillis).dayOfWeek.value - 1] += it.durationMinutes }
        return DayOfWeek.values().map { day ->
            val i = day.value - 1
            WeekdayStat(day, totals[i], if (occurrences[i] == 0) 0f else totals[i].toFloat() / occurrences[i])
        }
    }

    // ── Serien ────────────────────────────────────────────────────────────────

    /** Aktuelle Serie. Ist heute noch nichts geübt, zählt die Serie bis gestern weiter. */
    fun currentStreak(sessions: List<PracticeSession>, today: LocalDate = LocalDate.now()): Int {
        val days = sessions.map { toLocalDate(it.dateMillis) }.toSet()
        var cursor = if (days.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (days.contains(cursor)) { streak++; cursor = cursor.minusDays(1) }
        return streak
    }

    fun longestStreak(sessions: List<PracticeSession>): Int {
        val days = sessions.map { toLocalDate(it.dateMillis) }.toSortedSet().toList()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (ChronoUnit.DAYS.between(days[i - 1], days[i]) == 1L) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    fun daysPracticedInMonth(sessions: List<PracticeSession>, today: LocalDate): Pair<Int, Int> {
        val ym = YearMonth.from(today)
        val count = sessions.map { toLocalDate(it.dateMillis) }.filter { YearMonth.from(it) == ym }.distinct().size
        return count to ym.lengthOfMonth()
    }

    // ── Wochenziel & Heatmap ──────────────────────────────────────────────────

    fun minutesThisWeek(sessions: List<PracticeSession>, today: LocalDate): Int {
        val start = weekStart(today)
        return sessions.filter { !toLocalDate(it.dateMillis).isBefore(start) }.sumOf { it.durationMinutes }
    }

    fun weekTrend(sessions: List<PracticeSession>, today: LocalDate): WeekTrend {
        fun sum(from: LocalDate, to: LocalDate) = sessions.filter {
            val d = toLocalDate(it.dateMillis); !d.isBefore(from) && !d.isAfter(to)
        }.sumOf { it.durationMinutes }
        return WeekTrend(
            thisWeekMinutes = sum(today.minusDays(6), today),
            previousWeekMinutes = sum(today.minusDays(13), today.minusDays(7))
        )
    }

    /** Spalten = Wochen (älteste zuerst), je 7 Zeilen Mo–So. Zellen nach heute sind null. */
    fun heatmap(sessions: List<PracticeSession>, today: LocalDate, weeks: Int = 16): List<List<HeatCell?>> {
        val perDay = minutesPerDay(sessions)
        val firstMonday = weekStart(today).minusWeeks(weeks - 1L)
        return (0 until weeks).map { w ->
            (0 until 7).map { d ->
                val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
                if (date.isAfter(today)) null else HeatCell(date, perDay[date] ?: 0)
            }
        }
    }

    // ── Stücke ────────────────────────────────────────────────────────────────

    /** Sessions je Stück; die Minuten einer Session werden gleichmäßig auf die geübten Stücke verteilt ("ca."). */
    fun pieceUsage(sessions: List<PracticeSession>, pieces: List<Piece>, limit: Int = 5): List<PieceUsage> {
        val byId = pieces.associateBy { it.id }
        val counts = HashMap<Long, Int>()
        val minutes = HashMap<Long, Float>()
        sessions.forEach { s ->
            val ids = s.pieceIds.mapNotNull { it.toLongOrNull() }.filter { byId.containsKey(it) }.distinct()
            if (ids.isEmpty()) return@forEach
            val share = s.durationMinutes.toFloat() / ids.size
            ids.forEach { id ->
                counts[id] = (counts[id] ?: 0) + 1
                minutes[id] = (minutes[id] ?: 0f) + share
            }
        }
        return counts.map { (id, c) -> PieceUsage(byId.getValue(id), c, (minutes[id] ?: 0f).toInt()) }
            .sortedWith(compareByDescending<PieceUsage> { it.sessionCount }.thenByDescending { it.approxMinutes })
            .take(limit)
    }

    fun neglectedPieces(pieces: List<Piece>, today: LocalDate, limit: Int = 5): List<PieceNeglect> =
        pieces.mapNotNull { p ->
            val ref = p.lastPracticedAt ?: p.createdAt
            val days = ChronoUnit.DAYS.between(toLocalDate(ref), today).toInt()
            if (days >= NEGLECT_THRESHOLD_DAYS) PieceNeglect(p, days, p.lastPracticedAt == null) else null
        }.sortedByDescending { it.daysSince }.take(limit)

    private fun barsOf(section: de.klaviatur.data.model.PieceSection): Int =
        (section.endBar - section.startBar + 1).coerceAtLeast(0)

    /** Takte je Fortschrittsstufe über alle Stücke. */
    fun levelDistribution(pieces: List<Piece>): Map<ProgressLevel, Int> {
        val result = ProgressLevel.entries.associateWith { 0 }.toMutableMap()
        pieces.forEach { p -> p.sections.forEach { s -> result[s.level] = (result[s.level] ?: 0) + barsOf(s) } }
        return result
    }

    private fun levelWeight(level: ProgressLevel): Float = when (level) {
        ProgressLevel.NOT_LEARNED -> 0f
        ProgressLevel.WIP -> 1f / 3f
        ProgressLevel.GOOD -> 2f / 3f
        ProgressLevel.SAFE -> 1f
    }

    /** Stücke mit Sections, sortiert nach gewichtetem Fortschritt (absteigend). */
    fun pieceMastery(pieces: List<Piece>, limit: Int = 8): List<PieceMastery> =
        pieces.mapNotNull { p ->
            val bars = ProgressLevel.entries.associateWith { lvl -> p.sections.filter { it.level == lvl }.sumOf { barsOf(it) } }
            val total = bars.values.sum()
            if (total == 0) null
            else PieceMastery(p, bars, bars.entries.sumOf { (lvl, b) -> (levelWeight(lvl) * b).toDouble() }.toFloat() / total)
        }.sortedByDescending { it.score }.take(limit)

    fun repertoireScope(pieces: List<Piece>, tagLimit: Int = 10): RepertoireScope {
        val tagCounts = LinkedHashMap<String, Pair<String, Int>>()   // lowercase -> (Anzeigename, Anzahl)
        pieces.forEach { p ->
            p.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { tag ->
                val key = tag.lowercase()
                val prev = tagCounts[key]
                tagCounts[key] = (prev?.first ?: tag) to ((prev?.second ?: 0) + 1)
            }
        }
        return RepertoireScope(
            totalBars = pieces.sumOf { it.totalBars },
            keyCount = pieces.map { it.keySignature.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size,
            tags = tagCounts.values.sortedByDescending { it.second }.take(tagLimit)
        )
    }

    /** Neu hinzugefügte Stücke je Monat (letzte [months] Monate, älteste zuerst). */
    fun addedPerMonth(pieces: List<Piece>, today: LocalDate, months: Int = 12): List<Bucket> {
        val end = YearMonth.from(today)
        val perMonth = pieces.groupingBy { YearMonth.from(toLocalDate(it.createdAt)) }.eachCount()
        return (months - 1 downTo 0).map { back ->
            val ym = end.minusMonths(back.toLong())
            Bucket(ym.atDay(1), perMonth[ym] ?: 0, monthly = true)
        }
    }

    /** Anzahl Stücke je Epoche und Schwierigkeit 1–5 (Index 0 = Schwierigkeit 1). */
    fun epochDifficultyMatrix(pieces: List<Piece>): Map<Epoch, IntArray> {
        val result = LinkedHashMap<Epoch, IntArray>()
        pieces.forEach { p ->
            val epoch = p.epoch ?: return@forEach
            val row = result.getOrPut(epoch) { IntArray(5) }
            row[(p.difficulty - 1).coerceIn(0, 4)]++
        }
        return result
    }

    fun composerStatus(pieces: List<Piece>, limit: Int = 5): List<ComposerStatus> =
        pieces.filter { it.composer.isNotBlank() }.groupBy { it.composer.trim() }.map { (name, list) ->
            ComposerStatus(name, list.size, PieceStatus.entries.associateWith { s -> list.count { it.status == s } })
        }.sortedByDescending { it.total }.take(limit)

    // ── Erfolge ───────────────────────────────────────────────────────────────

    fun badges(sessions: List<PracticeSession>, pieces: List<Piece>): List<Badge> {
        val longestStreak = longestStreak(sessions)
        val hours = sessions.sumOf { it.durationMinutes } / 60
        val repertoire = pieces.count { it.status == PieceStatus.REPERTOIRE }
        val safeSections = pieces.sumOf { p -> p.sections.count { it.level == ProgressLevel.SAFE } }
        val longest = sessions.maxOfOrNull { it.durationMinutes } ?: 0
        return buildList {
            listOf(7, 30, 100).forEach { add(Badge(BadgeKind.STREAK, it, longestStreak)) }
            listOf(10, 50, 100).forEach { add(Badge(BadgeKind.HOURS, it, hours)) }
            listOf(10, 50).forEach { add(Badge(BadgeKind.SESSIONS, it, sessions.size)) }
            listOf(1, 10).forEach { add(Badge(BadgeKind.REPERTOIRE, it, repertoire)) }
            add(Badge(BadgeKind.SAFE_SECTION, 1, safeSections))
            add(Badge(BadgeKind.MARATHON, 60, longest))
        }
    }

    fun records(sessions: List<PracticeSession>): Records {
        val longest = sessions.maxByOrNull { it.durationMinutes }
        val perDay = minutesPerDay(sessions)
        val bestDay = perDay.maxByOrNull { it.value }
        val perWeek = perDay.entries.groupBy({ weekStart(it.key) }, { it.value }).mapValues { it.value.sum() }
        val bestWeek = perWeek.maxByOrNull { it.value }
        val mostPieces = sessions.maxByOrNull { s -> s.pieceIds.distinct().size }?.takeIf { it.pieceIds.isNotEmpty() }
        return Records(
            longestSessionMinutes = longest?.durationMinutes,
            longestSessionDate = longest?.let { toLocalDate(it.dateMillis) },
            bestDayMinutes = bestDay?.value,
            bestDayDate = bestDay?.key,
            bestWeekMinutes = bestWeek?.value,
            bestWeekStart = bestWeek?.key,
            longestStreak = longestStreak(sessions),
            mostPiecesInSession = mostPieces?.pieceIds?.distinct()?.size,
            mostPiecesDate = mostPieces?.let { toLocalDate(it.dateMillis) }
        )
    }
}
