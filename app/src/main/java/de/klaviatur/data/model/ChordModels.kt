package de.klaviatur.data.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.klaviatur.R
import de.klaviatur.util.AppSettings

@Entity(tableName = "olga_songs")
data class OlgaSong(
    @PrimaryKey val id: String, // Stable ID based on filepath hash
    val title: String,
    val artist: String,
    val filepath: String,
    val format: String,
    val category: String
)

@Entity(tableName = "user_songs")
data class UserSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val chordproText: String,
    val source: String, // "ug_import" or "manual"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chord_favorites")
data class ChordFavorite(
    @PrimaryKey val songIdentifier: String // "olga:id" or "user:id"
)

@Entity(tableName = "chord_playlists")
data class ChordPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songIdentifiers: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** Instrument, zu dem ein Stück oder Song gehört. */
data class ChordInstrument(
    val id: String,
    @StringRes val nameRes: Int? = null,
    val defaultName: String = "",
    val emoji: String = "🎵",
    val isCustom: Boolean = false
) {
    val name: String get() = id

    companion object {
        val PIANO = ChordInstrument("PIANO", R.string.instrument_piano, "Klavier", "🎹")
        val ORGAN = ChordInstrument("ORGAN", R.string.instrument_organ, "Orgel", "🎹")
        val HARPSICHORD = ChordInstrument("HARPSICHORD", R.string.instrument_harpsichord, "Cembalo", "🎹")
        val VIOLIN = ChordInstrument("VIOLIN", R.string.instrument_violin, "Violine / Geige", "🎻")
        val VIOLA = ChordInstrument("VIOLA", R.string.instrument_viola, "Viola / Bratsche", "🎻")
        val CELLO = ChordInstrument("CELLO", R.string.instrument_cello, "Violoncello", "🎻")
        val DOUBLE_BASS = ChordInstrument("DOUBLE_BASS", R.string.instrument_double_bass, "Kontrabass", "🎻")
        val FLUTE = ChordInstrument("FLUTE", R.string.instrument_flute, "Querflöte", "🪈")
        val RECORDER = ChordInstrument("RECORDER", R.string.instrument_recorder, "Blockflöte", "🪈")
        val CLARINET = ChordInstrument("CLARINET", R.string.instrument_clarinet, "Klarinette", "🎷")
        val OBOE = ChordInstrument("OBOE", R.string.instrument_oboe, "Oboe", "🎷")
        val BASSOON = ChordInstrument("BASSOON", R.string.instrument_bassoon, "Fagott", "🎷")
        val SAXOPHONE = ChordInstrument("SAXOPHONE", R.string.instrument_saxophone, "Saxophon", "🎷")
        val TRUMPET = ChordInstrument("TRUMPET", R.string.instrument_trumpet, "Trompete", "🎺")
        val FRENCH_HORN = ChordInstrument("FRENCH_HORN", R.string.instrument_french_horn, "Waldhorn", "📯")
        val TROMBONE = ChordInstrument("TROMBONE", R.string.instrument_trombone, "Posaune", "🎺")
        val GUITAR = ChordInstrument("GUITAR", R.string.instrument_guitar, "Gitarre", "🎸")
        val UKULELE = ChordInstrument("UKULELE", R.string.instrument_ukulele, "Ukulele", "🪕")
        val MANDOLIN = ChordInstrument("MANDOLIN", R.string.instrument_mandolin, "Mandoline", "🪕")
        val HARP = ChordInstrument("HARP", R.string.instrument_harp, "Harfe", "🪕")
        val ACCORDION = ChordInstrument("ACCORDION", R.string.instrument_accordion, "Akkordeon", "🪗")
        val VOICE = ChordInstrument("VOICE", R.string.instrument_voice, "Gesang", "🎤")
        val DRUMS = ChordInstrument("DRUMS", R.string.instrument_drums, "Schlagzeug", "🥁")

        val builtInList = listOf(
            PIANO, ORGAN, HARPSICHORD, VIOLIN, VIOLA, CELLO, DOUBLE_BASS,
            FLUTE, RECORDER, CLARINET, OBOE, BASSOON, SAXOPHONE,
            TRUMPET, FRENCH_HORN, TROMBONE, GUITAR, UKULELE, MANDOLIN, HARP,
            ACCORDION, VOICE, DRUMS
        )

        val chordSectionInstruments = listOf(PIANO, GUITAR, UKULELE)
        val defaultEnabledIds = setOf(PIANO.id, GUITAR.id, VIOLIN.id)

        private val builtInMap = builtInList.associateBy { it.id }

        fun fromId(id: String?): ChordInstrument? {
            if (id.isNullOrBlank()) return null
            builtInMap[id]?.let { return it }
            AppSettings.findCustomInstrument(id)?.let { return it }
            return ChordInstrument(id = id, defaultName = id, emoji = "🎵", isCustom = true)
        }
    }
}

/**
 * Zuordnung Song -> Instrument. Bewusst eine eigene Tabelle (wie chord_favorites):
 * So überlebt die Zuordnung auch das Neu-Indizieren des OLGA-Ordners.
 */
@Entity(tableName = "chord_song_instruments")
data class ChordSongInstrument(
    @PrimaryKey val songIdentifier: String, // "olga:id" or "user:id"
    val instrument: String                  // ChordInstrument.id
)

class ChordConverters {
    private val gson = Gson()

    @TypeConverter fun stringListToJson(list: List<String>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToStringList(json: String): List<String> =
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
}
