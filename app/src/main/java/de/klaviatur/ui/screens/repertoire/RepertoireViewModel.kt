package de.klaviatur.ui.screens.repertoire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.klaviatur.data.model.*
import de.klaviatur.data.repository.OpenOpusRepository
import de.klaviatur.data.repository.PieceRepository
import de.klaviatur.data.repository.SessionRepository
import de.klaviatur.data.db.OOSearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Random
import javax.inject.Inject

data class RepertoireFilter(
    val query: String = "",
    val status: PieceStatus? = null,
    val epoch: Epoch? = null,
    val difficulty: Int? = null,
    val selectedTags: List<String> = emptyList()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class RepertoireViewModel @Inject constructor(
    private val repo: PieceRepository,
    private val ooRepo: OpenOpusRepository,
    private val sessionRepo: SessionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(RepertoireFilter())
    val filter: StateFlow<RepertoireFilter> = _filter.asStateFlow()

    val pieces: StateFlow<List<Piece>> = repo.allPieces
        .combine(_filter) { pieces, f -> applyFilter(pieces, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPieces: StateFlow<List<Piece>> = repo.allPieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsPracticePieces: StateFlow<List<Piece>> = repo.allPieces
        .map { pieces ->
            val threshold = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 Tage
            pieces.filter { 
                (it.lastPracticedAt != null && it.lastPracticedAt < threshold) ||
                (it.lastPracticedAt == null && it.createdAt < threshold)
            }.sortedBy { it.lastPracticedAt ?: it.createdAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ooResults = MutableStateFlow<List<OOSearchResult>>(emptyList())
    val ooResults: StateFlow<List<OOSearchResult>> = _ooResults.asStateFlow()

    private val _ooLoading = MutableStateFlow(false)
    val ooLoading: StateFlow<Boolean> = _ooLoading.asStateFlow()

    fun setFilter(f: RepertoireFilter) { _filter.value = f }

    fun upsertPiece(piece: Piece) = viewModelScope.launch { repo.upsert(piece) }
    fun deletePiece(piece: Piece) = viewModelScope.launch { repo.delete(piece) }

    fun addTempoEntry(piece: Piece, entry: TempoEntry) = viewModelScope.launch {
        repo.addTempoEntry(piece, entry)
    }

    fun updateSections(piece: Piece, sections: List<PieceSection>) = viewModelScope.launch {
        repo.updateSections(piece, sections)
    }

    fun autoSplitSections(piece: Piece, barsPerSection: Int) = viewModelScope.launch {
        if (piece.totalBars <= 0 || barsPerSection <= 0) return@launch
        val newSections = mutableListOf<PieceSection>()
        var currentBar = 1
        while (currentBar <= piece.totalBars) {
            val endBar = (currentBar + barsPerSection - 1).coerceAtMost(piece.totalBars)
            newSections.add(
                PieceSection(
                    name = "Abschnitt ${newSections.size + 1}",
                    startBar = currentBar,
                    endBar = endBar,
                    level = ProgressLevel.NOT_LEARNED
                )
            )
            currentBar = endBar + 1
        }
        repo.updateSections(piece, newSections)
    }

    fun pickShufflePiece(): Long? {
        val filtered = pieces.value
        if (filtered.isEmpty()) return null
        
        // Sort by lastPracticedAt (null first, then oldest)
        val sorted = filtered.sortedWith(compareBy<Piece> { it.lastPracticedAt ?: 0L })
        
        // Take the bottom 10% (at least 1, at most all if very few)
        val count = (sorted.size * 0.1).toInt().coerceAtLeast(1).coerceAtMost(sorted.size)
        val candidates = sorted.take(count)
        
        return candidates[Random().nextInt(candidates.size)].id
    }

    fun savePracticeSession(piece: Piece, durationMinutes: Int) = viewModelScope.launch {
        if (durationMinutes <= 0) return@launch
        val now = System.currentTimeMillis()
        sessionRepo.upsert(
            PracticeSession(
                dateMillis = now,
                durationMinutes = durationMinutes,
                pieceIds = listOf(piece.id.toString())
            )
        )
        repo.updateLastPracticed(piece, now)
    }

    fun deleteSection(piece: Piece, section: PieceSection) = viewModelScope.launch {
        repo.updateSections(piece, piece.sections - section)
    }

    fun upsertSection(piece: Piece, section: PieceSection, index: Int = -1) = viewModelScope.launch {
        val newSections = piece.sections.toMutableList()
        if (index >= 0 && index < newSections.size) {
            newSections[index] = section
        } else {
            newSections.add(section)
        }
        repo.updateSections(piece, newSections)
    }

    fun searchOpenOpus(query: String) {
        if (query.length < 2) { _ooResults.value = emptyList(); return }
        viewModelScope.launch {
            _ooLoading.value = true
            _ooResults.value = ooRepo.search(query)
            _ooLoading.value = false
        }
    }

    fun clearOoResults() { _ooResults.value = emptyList() }

    private fun applyFilter(pieces: List<Piece>, f: RepertoireFilter): List<Piece> {
        val q = f.query.lowercase()
        return pieces.filter { p ->
            (q.isBlank() || p.title.lowercase().contains(q) || p.composer.lowercase().contains(q)) &&
            (f.status == null || p.status == f.status) &&
            (f.epoch == null || p.epoch == f.epoch) &&
            (f.difficulty == null || p.difficulty == f.difficulty) &&
            (f.selectedTags.isEmpty() || p.tags.containsAll(f.selectedTags))
        }
    }
}
