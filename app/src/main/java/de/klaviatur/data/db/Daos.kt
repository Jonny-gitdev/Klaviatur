package de.klaviatur.data.db

import androidx.room.*
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceList
import de.klaviatur.data.model.PracticeSession
import kotlinx.coroutines.flow.Flow

@Dao
interface PieceDao {
    @Query("SELECT * FROM pieces ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Piece>>

    @Query("SELECT * FROM pieces WHERE id = :id")
    suspend fun getById(id: Long): Piece?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(piece: Piece): Long

    @Delete
    suspend fun delete(piece: Piece)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY dateMillis DESC")
    fun getAllFlow(): Flow<List<PracticeSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PracticeSession): Long

    @Delete
    suspend fun delete(session: PracticeSession)

    @Query("SELECT SUM(durationMinutes) FROM practice_sessions")
    fun totalMinutesFlow(): Flow<Int?>
}

@Dao
interface ListDao {
    @Query("SELECT * FROM piece_lists ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<PieceList>>

    @Query("SELECT * FROM piece_lists WHERE id = :id")
    suspend fun getById(id: Long): PieceList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: PieceList): Long

    @Delete
    suspend fun delete(list: PieceList)
}
