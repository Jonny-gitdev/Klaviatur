package de.klaviatur.ui.screens.chords

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.klaviatur.data.model.ChordInstrument
import de.klaviatur.data.model.ChordPlaylist
import de.klaviatur.data.model.UserSong
import de.klaviatur.data.repository.ChordRepository
import de.klaviatur.data.repository.ChordSong
import de.klaviatur.data.repository.IndexingStatus
import de.klaviatur.data.repository.OlgaIndexer
import de.klaviatur.util.UgShareImporter
import okhttp3.OkHttpClient
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

    private val _instrumentFilter = MutableStateFlow<ChordInstrument?>(null)
    val instrumentFilter = _instrumentFilter.asStateFlow()

    /** songIdentifier -> Instrument (nur Songs, denen ein Instrument zugeordnet wurde). */
    val instrumentMap = repository.getInstrumentsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allSongs = repository.getAllSongsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists = repository.getPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds = repository.getFavoritesFlow()
        .map { favs -> favs.map { it.songIdentifier }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val indexingStatus = indexer.status

    private val baseFilters = combine(
        _searchQuery,
        _sourceFilter,
        _formatFilter,
        _playlistFilter,
        _onlyFavorites
    ) { query, source, format, playlistId, favOnly ->
        ChordFilters(query, source, format, playlistId, favOnly)
    }

    private val filters = combine(baseFilters, _instrumentFilter) { f, instrument ->
        f.copy(instrument = instrument)
    }

    val songs = combine(
        repository.getAllSongsFlow(),
        filters,
        favoriteIds,
        playlists,
        instrumentMap
    ) { allSongs, f, favIds, allPlaylists, instruments ->
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

            val matchesInstrument = f.instrument == null || instruments[song.identifier]?.id == f.instrument.id

            matchesQuery && matchesSource && matchesFormat && matchesPlaylist && matchesFavorites && matchesInstrument
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

    fun updateInstrumentFilter(instrument: ChordInstrument?) {
        _instrumentFilter.value = instrument
    }

    fun setInstrument(songIdentifier: String, instrument: ChordInstrument?) {
        viewModelScope.launch { repository.setInstrument(songIdentifier, instrument) }
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

    fun saveUserSong(
        title: String,
        artist: String,
        content: String,
        source: String,
        instrument: ChordInstrument? = null
    ) {
        viewModelScope.launch {
            val id = repository.saveUserSong(
                UserSong(
                    title = title,
                    artist = artist,
                    chordproText = content,
                    source = source
                )
            )
            if (instrument != null) repository.setInstrument("user:$id", instrument)
        }
    }

    /** Geteilter Akkordtext ohne Link (Titel/Künstler/Instrument kommen vom Nutzer). Liefert die neue Song-ID. */
    suspend fun saveSharedText(text: String, title: String, artist: String, instrument: ChordInstrument?): String {
        val chordPro = UgShareImporter.convertPlainText(text)
        val id = repository.saveUserSong(
            UserSong(title = title.ifBlank { "Ultimate Guitar" }, artist = artist, chordproText = chordPro, source = "ug_import")
        )
        if (instrument != null) repository.setInstrument("user:$id", instrument)
        return id.toString()
    }



    fun updateUserSongMeta(songId: Long, title: String, artist: String) {
        viewModelScope.launch {
            repository.updateUserSongMeta(songId, title, artist)
        }
    }

    fun deleteSong(song: ChordSong) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    private data class ChordFilters(
        val query: String,
        val source: String?,
        val format: String?,
        val playlistId: Long?,
        val favoritesOnly: Boolean,
        val instrument: ChordInstrument? = null
    )
}
