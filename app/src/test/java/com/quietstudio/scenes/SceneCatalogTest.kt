package com.quietstudio.scenes

import com.quietstudio.core.media.scenes.SceneCatalog
import com.quietstudio.core.media.scenes.SceneGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the authored scene library: the full 40 across four groups, unique
 * stable ids, and every scene resolvable by id (the value stored in projects).
 */
class SceneCatalogTest {

    @Test
    fun `catalog has all forty scenes`() {
        assertEquals(40, SceneCatalog.ALL.size)
    }

    @Test
    fun `ids are unique and stable-looking`() {
        val ids = SceneCatalog.ALL.map { it.id }
        assertEquals("duplicate scene id", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue("id '$id' should be kebab-case", id.matches(Regex("[a-z0-9-]+")))
        }
    }

    @Test
    fun `every scene resolves by id and has layers`() {
        SceneCatalog.ALL.forEach { spec ->
            assertNotNull(SceneCatalog.byId(spec.id))
            assertTrue("${spec.id} has no layers", spec.layers.isNotEmpty())
            assertTrue("${spec.name} is blank", spec.name.isNotBlank())
        }
    }

    @Test
    fun `each group is represented as specified`() {
        val byGroup = SceneCatalog.ALL.groupBy { it.group }
        assertEquals(10, byGroup[SceneGroup.PASTORAL]?.size)
        assertEquals(10, byGroup[SceneGroup.ELECTRIC]?.size)
        assertEquals(10, byGroup[SceneGroup.COSMIC]?.size)
        assertEquals(10, byGroup[SceneGroup.NIGHTLIFE]?.size)
    }
}
