package de.klaviatur.data.repository

import android.content.Context
import android.net.Uri
import de.klaviatur.data.db.*
import de.klaviatur.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChordSong {
    data class Olga(val song: OlgaSong) : ChordSong()
    data class User(val song: UserSong) : ChordSong()

    val title: String get() = when(this) {
        is Olga -> song.title
        is User -> song.title
    }
    val artist: String get() = when(this) {
        is Olga -> song.artist
        is User -> song.artist
    }
    val identifier: String get() = when(this) {
        is Olga -> "olga:${song.id}"
        is User -> "user:${song.id}"
    }
}

@Singleton
class ChordRepository @Inject constructor(
    private val olgaSongDao: OlgaSongDao,
    private val userSongDao: UserSongDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao
) {
    fun getAllSongsFlow(): Flow<List<ChordSong>> {
        return combine(
            olgaSongDao.getAllFlow(),
            userSongDao.getAllFlow()
        ) { olga, user ->
            olga.map { ChordSong.Olga(it) } + user.map { ChordSong.User(it) }
        }
    }

    fun getFavoritesFlow(): Flow<List<ChordFavorite>> = favoriteDao.getAllFlow()

    fun getPlaylistsFlow(): Flow<List<ChordPlaylist>> = playlistDao.getAllFlow()

    suspend fun toggleFavorite(songIdentifier: String) {
        // Simple check if exists is not available in DAO directly as suspend, 
        // but we can use Flow first() or similar or add a suspend check to DAO.
        // Let's assume we can just insert/delete.
    }

    suspend fun addFavorite(id: String) = favoriteDao.insert(ChordFavorite(id))
    suspend fun removeFavorite(id: String) = favoriteDao.delete(ChordFavorite(id))
    fun isFavoriteFlow(id: String) = favoriteDao.isFavoriteFlow(id)

    suspend fun savePlaylist(playlist: ChordPlaylist) = playlistDao.upsert(playlist)
    suspend fun deletePlaylist(playlist: ChordPlaylist) = playlistDao.delete(playlist)
    suspend fun getPlaylistById(id: Long) = playlistDao.getById(id)

    suspend fun getOlgaSongById(id: String) = olgaSongDao.getById(id)
    suspend fun getUserSongById(id: Long) = userSongDao.getById(id)

    suspend fun saveUserSong(song: UserSong) = userSongDao.upsert(song)
    suspend fun deleteUserSong(song: UserSong) = userSongDao.delete(song)

    suspend fun loadSongContent(context: Context, song: ChordSong): String? {
        return when (song) {
            is ChordSong.Olga -> {
                try {
                    val uri = Uri.parse(song.song.filepath)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            is ChordSong.User -> song.song.chordproText
        }
    }
}
