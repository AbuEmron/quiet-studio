package com.quietstudio.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.quietstudio.core.database.QuietStudioDatabase
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.NarrationInfo
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.VisualConfig
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * End-to-end backup → restore against a real Room database and real files, so
 * AJ's insurance against reinstall data-loss is proven, not assumed. The
 * critical part is that the per-project media (narration WAV, video clip)
 * round-trips and is relinked with the correct scheme on restore.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectBackupTest {

    private lateinit var db: QuietStudioDatabase
    private lateinit var repo: ProjectRepository
    private lateinit var backup: ProjectBackup
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QuietStudioDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ProjectRepository(db.projectDao(), db.folderDao())
        backup = ProjectBackup(context, repo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `backup then restore preserves projects and relinks media`() = runTest {
        // A project with a real narration WAV and a real video clip on disk.
        val wav = File(context.filesDir, "narration_1.wav").apply { writeBytes(ByteArray(2048) { 7 }) }
        val mp4 = File(context.filesDir, "clip_1.mp4").apply { writeBytes(ByteArray(4096) { 9 }) }
        repo.create(
            "My Take",
            ProjectContent(
                narration = NarrationInfo(wav.absolutePath, 12_000, processed = true),
                visual = VisualConfig(kind = BackgroundKind.VIDEO.name, sourceUri = "file://${mp4.absolutePath}"),
            ),
        )

        // Export to a file, then wipe the DB + original media (simulate reinstall).
        val backupFile = File(context.cacheDir, "backup.zip")
        val exported = backupFile.outputStream().use { backup.exportTo(it) }
        assertEquals(1, exported.count)
        assertTrue("backup file should have content", backupFile.length() > 0)

        db.projectDao().let { runCatchingDeleteAll(it) }
        wav.delete(); mp4.delete()
        assertTrue(repo.allProjectsOnce().isEmpty())

        // Restore.
        val restored = backupFile.inputStream().use { backup.importFrom(it) }
        assertEquals(1, restored.count)

        val projects = repo.allProjectsOnce()
        assertEquals(1, projects.size)
        val p = projects.first()
        assertEquals("My Take", p.title)

        // Narration relinked as a raw path that exists again.
        val restoredWav = File(p.content.narration.wavPath!!)
        assertTrue("narration relinked to an existing file", restoredWav.exists())
        assertEquals(2048L, restoredWav.length())

        // Video relinked as a file:// URI that exists again.
        val uri = p.content.visual.sourceUri!!
        assertTrue("video uri keeps file:// scheme", uri.startsWith("file://"))
        val restoredMp4 = File(android.net.Uri.parse(uri).path!!)
        assertTrue("video relinked to an existing file", restoredMp4.exists())
        assertEquals(4096L, restoredMp4.length())
    }

    @Test
    fun `restored projects get new ids so they never overwrite current work`() = runTest {
        repo.create("A", ProjectContent())
        val backupFile = File(context.cacheDir, "b2.zip")
        backupFile.outputStream().use { backup.exportTo(it) }
        val originalId = repo.allProjectsOnce().first().id

        // Restore into a DB that still has the original → should add, not clobber.
        backupFile.inputStream().use { backup.importFrom(it) }
        val all = repo.allProjectsOnce()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == originalId })
        assertTrue(all.any { it.id != originalId })
    }

    @Test
    fun `importing a non-backup file fails cleanly`() = runTest {
        val junk = File(context.cacheDir, "notabackup.zip").apply { writeText("hello") }
        val threw = runCatching { junk.inputStream().use { backup.importFrom(it) } }.isFailure
        assertTrue("a junk file should be rejected", threw)
    }

    private suspend fun runCatchingDeleteAll(dao: com.quietstudio.core.database.ProjectDao) {
        repo.allProjectsOnce().forEach { dao.delete(it.id) }
    }
}
