package de.klaviatur.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.klaviatur.data.db.*
import de.klaviatur.data.model.Epoch
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceList
import de.klaviatur.data.model.PieceSection
import de.klaviatur.data.model.PieceStatus
import de.klaviatur.data.model.PracticeSession
import de.klaviatur.data.model.ProgressLevel
import de.klaviatur.data.model.SheetFile
import de.klaviatur.data.model.SheetFileType
import de.klaviatur.data.model.TempoEntry
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PieceRepository @Inject constructor(
    private val dao: PieceDao,
    private val listDao: ListDao,
    @ApplicationContext private val context: Context
) {
    val allPieces: Flow<List<Piece>> = dao.getAllFlow()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun upsert(piece: Piece) = dao.upsert(piece)
    suspend fun delete(piece: Piece) {
        dao.delete(piece)
        try {
            val lists = listDao.getAll()
            lists.forEach { list ->
                if (list.pieceIds.contains(piece.id)) {
                    listDao.upsert(list.copy(pieceIds = list.pieceIds - piece.id))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAssetToFile(assetName: String): File? {
        return try {
            val outFile = File(context.filesDir, assetName)
            if (!outFile.exists()) {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun checkAndPrepopulateDefaultPieces() {
        if (dao.getCount() == 0) {
            val pathetiqueFile = copyAssetToFile("pathetique-1-a4.pdf")
            val pathetiqueSheets = if (pathetiqueFile != null) {
                listOf(
                    SheetFile(
                        uriString = Uri.fromFile(pathetiqueFile).toString(),
                        name = "pathetique-1-a4.pdf",
                        type = SheetFileType.PDF
                    )
                )
            } else emptyList()

            val moonlightFile = copyAssetToFile("Moonlightsonata2ndMov.pdf")
            val moonlightSheets = if (moonlightFile != null) {
                listOf(
                    SheetFile(
                        uriString = Uri.fromFile(moonlightFile).toString(),
                        name = "Moonlightsonata2ndMov.pdf",
                        type = SheetFileType.PDF
                    )
                )
            } else emptyList()

            val defaultPieces = listOf(
                Piece(
                    title = "Piano Sonata no. 8 in C minor, op. 13, \"Pathétique\"",
                    composer = "Ludwig van Beethoven",
                    epoch = Epoch.KLASSIK,
                    keySignature = "C-Moll",
                    totalBars = 311,
                    targetBpm = 0,
                    status = PieceStatus.LEARNING,
                    difficulty = 3,
                    sheetFiles = pathetiqueSheets,
                    sections = listOf(
                        PieceSection(name = "Takt 1–10", startBar = 1, endBar = 10, level = ProgressLevel.SAFE),
                        PieceSection(name = "Takt 11–34", startBar = 11, endBar = 34, level = ProgressLevel.WIP),
                        PieceSection(name = "Takt 35–88", startBar = 35, endBar = 88, level = ProgressLevel.GOOD),
                        PieceSection(name = "Takt 89–134", startBar = 89, endBar = 134, level = ProgressLevel.SAFE),
                        PieceSection(name = "Takt 135–191", startBar = 135, endBar = 191, level = ProgressLevel.WIP),
                        PieceSection(name = "Takt 192–266", startBar = 192, endBar = 266, level = ProgressLevel.SAFE),
                        PieceSection(name = "Takt 267–311", startBar = 267, endBar = 311, level = ProgressLevel.WIP)
                    )
                ),
                Piece(
                    title = "Piano Sonata no. 14 in C sharp minor, op. 27 no. 2, \"Moonlight\" – 2nd Movement",
                    composer = "Ludwig van Beethoven",
                    epoch = Epoch.KLASSIK,
                    keySignature = "C#-Moll",
                    totalBars = 0,
                    targetBpm = 0,
                    status = PieceStatus.NEW,
                    difficulty = 2,
                    sheetFiles = moonlightSheets,
                    sections = emptyList()
                )
            )
            defaultPieces.forEach { dao.upsert(it) }
        }
    }

    suspend fun addTempoEntry(piece: Piece, entry: TempoEntry) {
        dao.upsert(piece.copy(tempoLog = piece.tempoLog + entry))
    }
    suspend fun updateSections(piece: Piece, sections: List<PieceSection>) {
        dao.upsert(piece.copy(sections = sections))
    }
    suspend fun updateLastPracticed(piece: Piece, timestamp: Long) {
        dao.upsert(piece.copy(lastPracticedAt = timestamp))
    }
    suspend fun updateLastPracticed(pieceIds: List<Long>, timestamp: Long) {
        pieceIds.forEach { id ->
            dao.getById(id)?.let { piece ->
                if (piece.lastPracticedAt == null || timestamp > piece.lastPracticedAt) {
                    dao.upsert(piece.copy(lastPracticedAt = timestamp))
                }
            }
        }
    }
}

@Singleton
class SessionRepository @Inject constructor(private val dao: SessionDao) {
    val allSessions: Flow<List<PracticeSession>> = dao.getAllFlow()
    val totalMinutes: Flow<Int?> = dao.totalMinutesFlow()
    suspend fun upsert(session: PracticeSession) = dao.upsert(session)
    suspend fun delete(session: PracticeSession) = dao.delete(session)

    fun calculateStreak(sessions: List<PracticeSession>): Int {
        if (sessions.isEmpty()) return 0
        val cal = Calendar.getInstance()
        val today = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val dayMs = 86_400_000L
        val uniqueDays = sessions.map { (it.dateMillis / dayMs) * dayMs }.toSortedSet().toList().sortedDescending()
        if (uniqueDays.isEmpty() || uniqueDays.first() < today - dayMs) return 0
        var streak = 1
        for (i in 1 until uniqueDays.size) {
            if (uniqueDays[i - 1] - uniqueDays[i] == dayMs) streak++ else break
        }
        return streak
    }
}

@Singleton
class ListRepository @Inject constructor(private val dao: ListDao) {
    val allLists: Flow<List<PieceList>> = dao.getAllFlow()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun upsert(list: PieceList) = dao.upsert(list)
    suspend fun delete(list: PieceList) = dao.delete(list)
    suspend fun addPieceToList(list: PieceList, pieceId: Long) {
        if (!list.pieceIds.contains(pieceId))
            dao.upsert(list.copy(pieceIds = list.pieceIds + pieceId))
    }
    suspend fun removePieceFromList(list: PieceList, pieceId: Long) {
        dao.upsert(list.copy(pieceIds = list.pieceIds - pieceId))
    }
}

@Singleton
class OpenOpusRepository @Inject constructor(private val api: OpenOpusApi) {
    suspend fun search(query: String): List<OOSearchResult> {
        if (query.length < 2) return emptyList()
        val results = mutableListOf<OOSearchResult>()

        val translatedQuery = translateQuery(query)

        try {
            // 1. Omnisearch is the most powerful global search
            val omniResp = api.omnisearch(translatedQuery)
            omniResp.results?.forEach { res ->
                val work = res.work
                val comp = res.composer
                if (work != null && comp != null) {
                    results.add(OOSearchResult(work.title, work.subtitle, comp.completeName ?: comp.name ?: ""))
                }
            }

            // 2. Targeted Composer Search (especially for combined queries)
            if (results.size < 10) {
                val compResp = api.searchComposers(translatedQuery)
                compResp.composers?.take(2)?.forEach { comp ->
                    try {
                        val workQuery = extractWorkQuery(translatedQuery, comp.completeName ?: "", comp.name ?: "")
                        val works = if (workQuery.isNotBlank() && workQuery.lowercase() != translatedQuery.lowercase()) {
                            api.searchWorksForComposer(comp.id, workQuery).works
                        } else {
                            api.getWorksForComposer(comp.id).works
                        }
                        works?.take(10)?.forEach { work ->
                            results.add(OOSearchResult(work.title, work.subtitle, comp.completeName ?: comp.name ?: ""))
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Fallback to original query if translation was used and results are poor
            if (results.isEmpty() && translatedQuery != query) {
                val omniResp2 = api.omnisearch(query)
                omniResp2.results?.forEach { res ->
                    val work = res.work
                    val comp = res.composer
                    if (work != null && comp != null) {
                        results.add(OOSearchResult(work.title, work.subtitle, comp.completeName ?: comp.name ?: ""))
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results.distinctBy { "${it.composer}|${it.title}|${it.subtitle}" }.take(25)
    }

    private fun translateQuery(query: String): String {
        var q = query.lowercase()
        // Simple mapping for common German terms to English (which OpenOpus uses more)
        val mapping = mapOf(
            "mondscheinsonate" to "moonlight sonata",
            "sonate" to "sonata",
            "klavier" to "piano",
            "geige" to "violin",
            "walzer" to "waltz",
            "nocturne" to "nocturne",
            "etüde" to "etude",
            "präludium" to "prelude"
        )
        mapping.forEach { (de, en) ->
            if (q.contains(de)) q = q.replace(de, en)
        }
        return q
    }

    private fun extractWorkQuery(fullQuery: String, completeName: String, lastName: String): String {
        var q = fullQuery.lowercase()
        val cn = completeName.lowercase()
        val ln = lastName.lowercase()
        if (cn.isNotEmpty()) q = q.replace(cn, "")
        if (ln.isNotEmpty()) q = q.replace(ln, "")
        return q.trim()
    }
}
