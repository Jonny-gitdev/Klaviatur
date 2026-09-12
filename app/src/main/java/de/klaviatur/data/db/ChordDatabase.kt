package de.klaviatur.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.klaviatur.data.model.*

@Database(
    entities = [OlgaSong::class, UserSong::class, ChordFavorite::class, ChordPlaylist::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(ChordConverters::class)
abstract class ChordDatabase : RoomDatabase() {
    abstract fun olgaSongDao(): OlgaSongDao
    abstract fun userSongDao(): UserSongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
}
