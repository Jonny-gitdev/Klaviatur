package de.klaviatur.di

import android.content.Context
import androidx.room.Room
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "klaviatur.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun providePieceDao(db: AppDatabase) = db.pieceDao()
    @Provides @Singleton fun provideSessionDao(db: AppDatabase) = db.sessionDao()
    @Provides @Singleton fun provideListDao(db: AppDatabase) = db.listDao()

    @Provides @Singleton
    fun provideChordDatabase(@ApplicationContext ctx: Context): ChordDatabase =
        Room.databaseBuilder(ctx, ChordDatabase::class.java, "chords.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideOlgaSongDao(db: ChordDatabase) = db.olgaSongDao()
    @Provides @Singleton fun provideUserSongDao(db: ChordDatabase) = db.userSongDao()
    @Provides @Singleton fun provideChordFavoriteDao(db: ChordDatabase) = db.favoriteDao()
    @Provides @Singleton fun provideChordPlaylistDao(db: ChordDatabase) = db.playlistDao()

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
