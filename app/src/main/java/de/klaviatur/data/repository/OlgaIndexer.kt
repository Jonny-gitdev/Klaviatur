package de.klaviatur.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import de.klaviatur.data.db.OlgaSongDao
import de.klaviatur.data.model.OlgaSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class IndexingStatus {
    object Idle : IndexingStatus()
    data class Indexing(val songsFound: Int, val currentCategory: String = "") : IndexingStatus()
    object Completed : IndexingStatus()
    data class Error(val message: String) : IndexingStatus()
}

@Singleton
class OlgaIndexer @Inject constructor(
    private val olgaSongDao: OlgaSongDao
) {
    private val _status = MutableStateFlow<IndexingStatus>(IndexingStatus.Idle)
    val status = _status.asStateFlow()

    suspend fun indexArchive(context: Context, rootUri: Uri) = withContext(Dispatchers.IO) {
        try {
            _status.value = IndexingStatus.Indexing(0, "Bereite Scan vor...")
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            if (rootDoc == null || !rootDoc.isDirectory) {
                _status.value = IndexingStatus.Error("Ungültiger Ordner")
                return@withContext
            }

            val songs = mutableListOf<OlgaSong>()
            // Nicht mehr sofort löschen!
            
            Log.d("OlgaIndexer", "Starte rekursiven Scan in: ${rootDoc.name}")
            scanRecursive(rootDoc, "Archive", songs, 0)

            // Erst wenn der Scan KOMPLETT ist, die DB aktualisieren (in einer Transaktion)
            if (songs.isNotEmpty()) {
                _status.value = IndexingStatus.Indexing(songs.size, "Speichere in Datenbank...")
                olgaSongDao.replaceAll(songs)
            }

            _status.value = IndexingStatus.Completed
        } catch (e: Exception) {
            Log.e("OlgaIndexer", "Fehler beim Indexieren", e)
            _status.value = IndexingStatus.Error(e.localizedMessage ?: "Unbekannter Fehler")
        }
    }

    fun resetStatus() {
        _status.value = IndexingStatus.Idle
    }

    private suspend fun scanRecursive(dir: DocumentFile, category: String, songs: MutableList<OlgaSong>, depth: Int) {
        if (depth > 8) return // Etwas tiefer für alle Fälle

        val files = dir.listFiles()
        var newCategory = category
        if (depth == 0 && dir.name != null) newCategory = dir.name!!

        files.forEach { file ->
            if (file.isDirectory) {
                _status.value = IndexingStatus.Indexing(songs.size, file.name ?: newCategory)
                scanRecursive(file, newCategory, songs, depth + 1)
            } else {
                val name = file.name ?: return@forEach
                if (name.endsWith(".tab", ignoreCase = true)) return@forEach
                
                val extension = name.substringAfterLast(".", "").lowercase()
                if (extension == "pro" || extension == "crd" || extension == "chopro") {
                    val artist = dir.name?.replace("_", " ")?.trim() ?: "Unknown"
                    val title = name.substringBeforeLast(".").replace("_", " ").trim()
                    
                    // Erzeuge eine stabile ID aus dem Pfad
                    val stableId = file.uri.toString().hashCode().toString()

                    songs.add(
                        OlgaSong(
                            id = stableId,
                            title = title,
                            artist = artist,
                            filepath = file.uri.toString(),
                            format = extension,
                            category = newCategory
                        )
                    )
                }
            }
        }
    }

    // Removed parseFilename as we use parent folder now
}
