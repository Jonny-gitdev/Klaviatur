package de.klaviatur.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class PieceStatus(val label: String) {
    NEW("Neu"),
    LEARNING("In Arbeit"),
    POLISHING("Polishing"),
    REPERTOIRE("Repertoire")
}

enum class ProgressLevel(val label: String, val color: Long) {
    NOT_LEARNED("Noch nicht gelernt", 0xFFBDBDBD), // Gray
    WIP("In Arbeit", 0xFFEF5350),                 // Red
    GOOD("Gut", 0xFF26A69A),                      // Turquoise
    SAFE("Sicher", 0xFF66BB6A)                    // Green
}

enum class Epoch(val label: String) {
    BAROCK("Barock"),
    KLASSIK("Klassik"),
    ROMANTIK("Romantik"),
    MODERNE("Moderne"),
    ZEITGENOESSISCH("Zeitgenössisch")
}

// ── Tempo log entry ───────────────────────────────────────────────────────────

data class TempoEntry(
    val bpm: Int,
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = ""
)

// ── Piece section ─────────────────────────────────────────────────────────────

data class PieceSection(
    val name: String = "",
    val startBar: Int,
    val endBar: Int,
    val level: ProgressLevel = ProgressLevel.NOT_LEARNED,
    val notes: String = ""
)

// ── Room type converters ──────────────────────────────────────────────────────

class Converters {
    private val gson = Gson()

    @TypeConverter fun statusToString(s: PieceStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): PieceStatus = PieceStatus.valueOf(s)

    @TypeConverter fun epochToString(e: Epoch?): String? = e?.name
    @TypeConverter fun stringToEpoch(s: String?): Epoch? = s?.let { Epoch.valueOf(it) }

    @TypeConverter fun tempoListToJson(list: List<TempoEntry>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToTempoList(json: String): List<TempoEntry> =
        gson.fromJson(json, object : TypeToken<List<TempoEntry>>() {}.type) ?: emptyList()

    @TypeConverter fun stringListToJson(list: List<String>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToStringList(json: String): List<String> =
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter fun longListToJson(list: List<Long>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToLongList(json: String): List<Long> =
        gson.fromJson(json, object : TypeToken<List<Long>>() {}.type) ?: emptyList()

    @TypeConverter fun sectionListToJson(list: List<PieceSection>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToSectionList(json: String): List<PieceSection> =
        gson.fromJson(json, object : TypeToken<List<PieceSection>>() {}.type) ?: emptyList()
}

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "pieces")
@TypeConverters(Converters::class)
data class Piece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val composer: String,
    val epoch: Epoch? = null,
    val keySignature: String = "",
    val totalBars: Int = 0,
    val sections: List<PieceSection> = emptyList(),
    val tags: List<String> = emptyList(),
    val targetBpm: Int = 0,
    val status: PieceStatus = PieceStatus.NEW,
    val difficulty: Int = 1,           // 1–5
    val notes: String = "",
    val tempoLog: List<TempoEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null
)

@Entity(tableName = "practice_sessions")
@TypeConverters(Converters::class)
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val pieceIds: List<String> = emptyList(),   // stored as string IDs
    val notes: String = ""
)

@Entity(tableName = "piece_lists")
@TypeConverters(Converters::class)
data class PieceList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val pieceIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
