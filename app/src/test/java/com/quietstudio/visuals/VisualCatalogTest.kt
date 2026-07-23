package com.quietstudio.visuals

import com.quietstudio.core.media.scenes.SceneCatalog
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.feature.visuals.buildCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the catalog → Visuals grid binding: the grid renders whatever
 * buildCatalog() returns, so this proves the full scene set reaches the UI and
 * a future truncation (a stray take()/limit/subset) fails CI instead of
 * silently hiding scenes from AJ.
 */
class VisualCatalogTest {

    private val scenery = buildCatalog().filter { it.visual.kind == BackgroundKind.SCENERY.name }

    @Test
    fun `every catalog scene reaches the grid — all forty`() {
        assertEquals(SceneCatalog.ALL.size, scenery.size)
        assertEquals(40, scenery.size)
    }

    @Test
    fun `grid scene ids and names line up with the source catalog`() {
        assertEquals(
            SceneCatalog.ALL.map { it.name }.toSet(),
            scenery.map { it.name }.toSet(),
        )
        // Each grid item carries the scene's own id, so selecting it stores the
        // right scenery theme.
        val themes = scenery.map { it.visual.sceneryTheme }.toSet()
        assertEquals(SceneCatalog.ALL.map { it.id }.toSet(), themes)
    }

    @Test
    fun `the default All filter shows the whole set, groups partition it`() {
        val groups = scenery.groupBy { it.category }
        assertEquals(4, groups.size)
        groups.values.forEach { assertEquals(10, it.size) }
        assertTrue(scenery.size == groups.values.sumOf { it.size })
    }
}
