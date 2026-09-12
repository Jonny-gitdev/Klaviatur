package de.klaviatur.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.klaviatur.data.model.Converters
import de.klaviatur.data.model.Piece
import de.klaviatur.data.model.PieceList
import de.klaviatur.data.model.PracticeSession

@Database(
    entities = [Piece::class, PracticeSession::class, PieceList::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pieceDao(): PieceDao
    abstract fun sessionDao(): SessionDao
    abstract fun listDao(): ListDao
}
