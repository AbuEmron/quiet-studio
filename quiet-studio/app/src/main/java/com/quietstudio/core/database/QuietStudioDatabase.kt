package com.quietstudio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ProjectVersionEntity::class,
        FolderEntity::class,
        TemplateEntity::class,
        VisualPackEntity::class,
        MusicPrefEntity::class,
        PlaylistEntity::class,
        ImportedTrackEntity::class,
        ExportJobEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class QuietStudioDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun folderDao(): FolderDao
    abstract fun templateDao(): TemplateDao
    abstract fun visualPackDao(): VisualPackDao
    abstract fun musicDao(): MusicDao
    abstract fun exportDao(): ExportDao
}
