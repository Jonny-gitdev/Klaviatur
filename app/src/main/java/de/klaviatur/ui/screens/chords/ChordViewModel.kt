package de.klaviatur.ui.screens.chords

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.klaviatur.data.model.ChordPlaylist
import de.klaviatur.data.model.UserSong
import de.klaviatur.data.repository.ChordRepository
import de.klaviatur.data.repository.ChordSong
import de.klaviatur.data.repository.IndexingStatus
import de.klaviatur.data.repository.OlgaIndexer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChordViewModel @Inject constructor(
    private val repository: ChordRepository,
    private val indexer: OlgaIndexer
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)
    val sourceFilter = _sourceFilter.asStateFlow()

    private val _formatFilter = MutableStateFlow<String?>(null)
    val formatFilter = _formatFilter.asStateFlow()

    private val _playlistFilter = MutableStateFlow<Long?>(null)
    val playlistFilter = _playlistFilter.asStateFlow()

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites = _onlyFavorites.asStateFlow()

    val playlists = repository.getPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds = repository.getFavoritesFlow()
        .map { favs -> favs.map { it.songIdentifier }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val indexingStatus = indexer.status

    private val filters = combine(
        _searchQuery,
        _sourceFilter,
        _formatFilter,
        _playlistFilter,
        _onlyFavorites
    ) { query, source, format, playlistId, favOnly ->
        ChordFilters(query, source, format, playlistId, favOnly)
    }

    val songs = combine(
        repository.getAllSongsFlow(),
        filters,
        favoriteIds,
        playlists
    ) { allSongs, f, favIds, allPlaylists ->
        val currentPlaylist = allPlaylists.find { it.id == f.playlistId }
        
        allSongs.filter { song ->
            val matchesQuery = song.title.contains(f.query, ignoreCase = true) ||
                    song.artist.contains(f.query, ignoreCase = true)
            
            val matchesSource = when (f.source) {
                "OLGA" -> song is ChordSong.Olga
                "Imported" -> song is ChordSong.User && song.song.source == "ug_import"
                "Manual" -> song is ChordSong.User && song.song.source == "manual"
                else -> true
            }

            val matchesFormat = when (f.format) {
                "ChordPro" -> (song is ChordSong.Olga && (song.song.format == "pro" || song.song.format == "chopro")) || (song is ChordSong.User)
                "UG/CRD" -> (song is ChordSong.Olga && song.song.format == "crd")
                else -> true
            }

            val matchesPlaylist = currentPlaylist == null || 
                    currentPlaylist.songIdentifiers.contains(song.identifier)

            val matchesFavorites = !f.favoritesOnly || favIds.contains(song.identifier)

            matchesQuery && matchesSource && matchesFormat && matchesPlaylist && matchesFavorites
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSourceFilter(source: String?) {
        _sourceFilter.value = source
    }

    fun updateFormatFilter(format: String?) {
        _formatFilter.value = format
    }

    fun updatePlaylistFilter(playlistId: Long?) {
        _playlistFilter.value = playlistId
    }

    fun toggleFavoritesFilter() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun toggleFavorite(songIdentifier: String) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(songIdentifier)) {
                repository.removeFavorite(songIdentifier)
            } else {
                repository.addFavorite(songIdentifier)
            }
        }
    }

    fun isFavorite(songIdentifier: String): Flow<Boolean> = repository.isFavoriteFlow(songIdentifier)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.savePlaylist(ChordPlaylist(name = name))
        }
    }

    fun addSongToPlaylist(playlist: ChordPlaylist, songIdentifier: String) {
        if (playlist.songIdentifiers.contains(songIdentifier)) return
        viewModelScope.launch {
            repository.savePlaylist(
                playlist.copy(songIdentifiers = playlist.songIdentifiers + songIdentifier)
            )
        }
    }

    fun removeSongFromPlaylist(playlist: ChordPlaylist, songIdentifier: String) {
        viewModelScope.launch {
            repository.savePlaylist(
                playlist.copy(songIdentifiers = playlist.songIdentifiers - songIdentifier)
            )
        }
    }

    fun resetIndexingStatus() {
        indexer.resetStatus()
    }

    fun indexOlgaArchive(context: Context, uri: Uri) {
        viewModelScope.launch {
            indexer.indexArchive(context, uri)
        }
    }

    suspend fun loadSongContent(context: Context, type: String, id: String): Pair<ChordSong, String>? {
        val song = when (type) {
            "olga" -> repository.getOlgaSongById(id)?.let { ChordSong.Olga(it) }
            "user" -> repository.getUserSongById(id.toLong())?.let { ChordSong.User(it) }
            else -> null
        } ?: return null

        val content = repository.loadSongContent(context, song) ?: return null
        return song to content
    }

    fun saveUserSong(title: String, artist: String, content: String, source: String) {
        viewModelScope.launch {
            repository.saveUserSong(
                UserSong(
                    title = title,
                    artist = artist,
                    chordproText = content,
                    source = source
                )
            )
        }
    }

    private data class ChordFilters(
        val query: String,
        val source: String?,
        val format: String?,
        val playlistId: Long?,
        val favoritesOnly: Boolean
    )
}
