package de.klaviatur.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

class ChordConverters {
    private val gson = Gson()

    @TypeConverter fun stringListToJson(list: List<String>): String =
        gson.toJson(list)
    @TypeConverter fun jsonToStringList(json: String): List<String> =
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
}
