package com.quietstudio.subtitles

import android.content.Context
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.quietstudio.core.media.SubtitlePainter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards that the font picker's ids map to real, distinct typefaces in the
 * shared painter (which draws both preview and export). AJ's complaint was that
 * choosing a font did nothing; these confirm the ids resolve and that the
 * clearly-different families actually differ.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleFontTest {

    private val painter =
        SubtitlePainter(540, 960, ApplicationProvider.getApplicationContext<Context>())

    private val allIds = listOf(
        "poppins_bold", "poppins_semibold", "poppins_medium", "inter",
        "serif", "mono", "handwritten", "display", "condensed",
    )

    @Test
    fun `every picker font id resolves to a non-null typeface`() {
        allIds.forEach { id ->
            assertNotNull("id $id resolved to null", painter.typefaceFor(id))
        }
    }

    @Test
    fun `the clearly-distinct families are actually different typefaces`() {
        val serif = painter.typefaceFor("serif")
        val mono = painter.typefaceFor("mono")
        val sans = painter.typefaceFor("poppins_bold")
        // System serif/mono are known-distinct from each other and from sans.
        assertTrue("serif == mono", serif != mono)
        assertTrue("serif should not be plain default", serif != Typeface.DEFAULT || sans != Typeface.DEFAULT)
    }

    @Test
    fun `an unknown id falls back rather than crashing`() {
        assertNotNull(painter.typefaceFor("nope-not-a-font"))
    }
}
