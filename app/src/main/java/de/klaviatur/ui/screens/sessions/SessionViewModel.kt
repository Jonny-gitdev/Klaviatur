package de.klaviatur.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PracticeSession
import de.klaviatur.data.repository.PieceRepository
import de.klaviatur.data.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val pieceRepo: PieceRepository
) : ViewModel() {

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds = _elapsedSeconds.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive = _isSessionActive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _currentPiece = MutableStateFlow<Piece?>(null)
    val currentPiece = _currentPiece.asStateFlow()

    private val _practicedPieces = MutableStateFlow<Set<Piece>>(emptySet())
    val practicedPieces = _practicedPieces.asStateFlow()

    private val _currentBars = MutableStateFlow("")
    val currentBars = _currentBars.asStateFlow()

    private val _selectedSections = MutableStateFlow<Set<Int>>(emptySet())
    val selectedSections = _selectedSections.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_isSessionActive.value && !_isPaused.value) {
                    _elapsedSeconds.value += 1
                }
            }
        }
    }

    val sessions: StateFlow<List<PracticeSession>> = sessionRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPieces: StateFlow<List<Piece>> = pieceRepo.allPieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMinutes: StateFlow<Int> = sessionRepo.totalMinutes
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val streak: StateFlow<Int> = sessionRepo.allSessions
        .map { sessionRepo.calculateStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startSession(piece: Piece? = null) {
        _currentPiece.value = piece
        piece?.let { _practicedPieces.value = setOf(it) }
        _isSessionActive.value = true
        _isPaused.value = false
        _elapsedSeconds.value = 0
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun stopSession() {
        _isSessionActive.value = false
        val durationSeconds = _elapsedSeconds.value
        val durationMinutes = (durationSeconds / 60).toInt()
        val finalPieces = _practicedPieces.value.toList()
        
        if (durationSeconds > 0) {
             viewModelScope.launch {
                val session = PracticeSession(
                    durationMinutes = if (durationMinutes == 0 && durationSeconds > 0) 1 else durationMinutes,
                    pieceIds = finalPieces.map { it.id.toString() },
                    notes = "Übung an: " + finalPieces.joinToString { it.title }
                )
                sessionRepo.upsert(session)
                pieceRepo.updateLastPracticed(finalPieces.map { it.id }, System.currentTimeMillis())
            }
        }
        _isPaused.value = false
        _elapsedSeconds.value = 0
        _currentPiece.value = null
        _practicedPieces.value = emptySet()
        _currentBars.value = ""
    }

    fun updateCurrentPiece(piece: Piece?) {
        _currentPiece.value = piece
        _selectedSections.value = emptySet()
        _currentBars.value = ""
        piece?.let {
            _practicedPieces.value = _practicedPieces.value + it
        }
    }

    fun updateCurrentBars(bars: String) {
        _currentBars.value = bars
    }

    fun toggleSection(index: Int) {
        _selectedSections.value = if (_selectedSections.value.contains(index)) {
            _selectedSections.value - index
        } else {
            _selectedSections.value + index
        }
        
        // Update currentBars string based on selected sections
        val piece = _currentPiece.value
        if (piece != null && _selectedSections.value.isNotEmpty()) {
            val selected = _selectedSections.value.mapNotNull { piece.sections.getOrNull(it) }
                .sortedBy { it.startBar }
            _currentBars.value = selected.joinToString(", ") { "${it.startBar}-${it.endBar}" }
        }
    }

    fun upsert(session: PracticeSession) = viewModelScope.launch {
        sessionRepo.upsert(session)
        pieceRepo.updateLastPracticed(session.pieceIds.mapNotNull { it.toLongOrNull() }, session.dateMillis)
    }
    fun delete(session: PracticeSession) = viewModelScope.launch { sessionRepo.delete(session) }
}
