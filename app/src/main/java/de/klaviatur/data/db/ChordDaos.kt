package de.klaviatur.data.db

import androidx.room.*
import de.klaviatur.data.model.ChordFavorite
import de.klaviatur.data.model.ChordPlaylist
import de.klaviatur.data.model.OlgaSong
import de.klaviatur.data.model.UserSong
import kotlinx.coroutines.flow.Flow

@Dao
interface OlgaSongDao {
    @Query("SELECT * FROM olga_songs")
    fun getAllFlow(): Flow<List<OlgaSong>>

    @Query("SELECT * FROM olga_songs WHERE id = :id")
    suspend fun getById(id: String): OlgaSong?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<OlgaSong>)

    @Query("DELETE FROM olga_songs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(songs: List<OlgaSong>) {
        deleteAll()
        insertAll(songs)
    }
}

@Dao
interface UserSongDao {
    @Query("SELECT * FROM user_songs ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<UserSong>>

    @Query("SELECT * FROM user_songs WHERE id = :id")
    suspend fun getById(id: Long): UserSong?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: UserSong): Long

    @Delete
    suspend fun delete(song: UserSong)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM chord_favorites")
    fun getAllFlow(): Flow<List<ChordFavorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fav: ChordFavorite)

    @Delete
    suspend fun delete(fav: ChordFavorite)

    @Query("SELECT EXISTS(SELECT 1 FROM chord_favorites WHERE songIdentifier = :id)")
    fun isFavoriteFlow(id: String): Flow<Boolean>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM chord_playlists ORDER BY name ASC")
    fun getAllFlow(): Flow<List<ChordPlaylist>>

    @Query("SELECT * FROM chord_playlists WHERE id = :id")
    suspend fun getById(id: Long): ChordPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: ChordPlaylist): Long

    @Delete
    suspend fun delete(playlist: ChordPlaylist)
}
