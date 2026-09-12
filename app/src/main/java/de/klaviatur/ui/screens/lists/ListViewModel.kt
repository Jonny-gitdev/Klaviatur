package de.klaviatur.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceList
import de.klaviatur.data.repository.ListRepository
import de.klaviatur.data.repository.PieceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val listRepo: ListRepository,
    private val pieceRepo: PieceRepository
) : ViewModel() {

    val lists: StateFlow<List<PieceList>> = listRepo.allLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPieces: StateFlow<List<Piece>> = pieceRepo.allPieces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun upsertList(list: PieceList) = viewModelScope.launch { listRepo.upsert(list) }
    fun deleteList(list: PieceList) = viewModelScope.launch { listRepo.delete(list) }

    fun addPieceToList(listId: Long, pieceId: Long) = viewModelScope.launch {
        listRepo.getById(listId)?.let { listRepo.addPieceToList(it, pieceId) }
    }

    fun removePieceFromList(listId: Long, pieceId: Long) = viewModelScope.launch {
        listRepo.getById(listId)?.let { listRepo.removePieceFromList(it, pieceId) }
    }

    fun piecesForList(list: PieceList): List<Piece> =
        allPieces.value.filter { list.pieceIds.contains(it.id) }
}
