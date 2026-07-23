package com.quietstudio.di

import android.content.Context
import androidx.room.Room
import com.quietstudio.BuildConfig
import com.quietstudio.core.database.QuietStudioDatabase
import com.quietstudio.core.media.Media3ExportEngine
import com.quietstudio.core.media.MediaEngine
import com.quietstudio.transcription.TranscriptionEngine
import com.quietstudio.transcription.whisper.ManualTranscriptionEngine
import com.quietstudio.transcription.whisper.WhisperTranscriptionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuietStudioDatabase =
        Room.databaseBuilder(context, QuietStudioDatabase::class.java, "quietstudio.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun projectDao(db: QuietStudioDatabase) = db.projectDao()
    @Provides fun folderDao(db: QuietStudioDatabase) = db.folderDao()
    @Provides fun templateDao(db: QuietStudioDatabase) = db.templateDao()
    @Provides fun visualPackDao(db: QuietStudioDatabase) = db.visualPackDao()
    @Provides fun musicDao(db: QuietStudioDatabase) = db.musicDao()
    @Provides fun exportDao(db: QuietStudioDatabase) = db.exportDao()

    @Provides
    @Singleton
    fun provideMediaEngine(engine: Media3ExportEngine): MediaEngine = engine

    @Provides
    @Singleton
    fun provideTranscriptionEngine(
        whisper: WhisperTranscriptionEngine,
        manual: ManualTranscriptionEngine,
    ): TranscriptionEngine =
        if (BuildConfig.WHISPER_ENABLED) whisper else manual
}
