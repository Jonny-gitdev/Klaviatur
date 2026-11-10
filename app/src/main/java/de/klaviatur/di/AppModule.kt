package de.klaviatur.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.klaviatur.data.db.AppDatabase
import de.klaviatur.data.db.ChordDatabase
import de.klaviatur.data.db.OpenOpusApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/** chords.db v2 -> v3: neue Tabelle für die Instrument-Zuordnung (bestehende Songs bleiben erhalten). */
private val CHORD_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chord_song_instruments` " +
                "(`songIdentifier` TEXT NOT NULL, `instrument` TEXT NOT NULL, PRIMARY KEY(`songIdentifier`))"
        )
    }
}

/** klaviatur.db v5 -> v6: Instrument pro Stück (bestehende Stücke bleiben erhalten, Instrument = leer). */
private val APP_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pieces ADD COLUMN instrument TEXT")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "klaviatur.db")
            .addMigrations(APP_MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun providePieceDao(db: AppDatabase) = db.pieceDao()
    @Provides @Singleton fun provideSessionDao(db: AppDatabase) = db.sessionDao()
    @Provides @Singleton fun provideListDao(db: AppDatabase) = db.listDao()

    @Provides @Singleton
    fun provideChordDatabase(@ApplicationContext ctx: Context): ChordDatabase =
        Room.databaseBuilder(ctx, ChordDatabase::class.java, "chords.db")
            .addMigrations(CHORD_MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideOlgaSongDao(db: ChordDatabase) = db.olgaSongDao()
    @Provides @Singleton fun provideUserSongDao(db: ChordDatabase) = db.userSongDao()
    @Provides @Singleton fun provideChordFavoriteDao(db: ChordDatabase) = db.favoriteDao()
    @Provides @Singleton fun provideChordPlaylistDao(db: ChordDatabase) = db.playlistDao()
    @Provides @Singleton fun provideChordInstrumentDao(db: ChordDatabase) = db.instrumentDao()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideOpenOpusApi(client: OkHttpClient): OpenOpusApi =
        Retrofit.Builder()
            .baseUrl("https://api.openopus.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenOpusApi::class.java)
}
