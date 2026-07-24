package com.quietstudio.enhance

import com.quietstudio.core.media.enhance.EnhanceGrade
import com.quietstudio.core.model.EnhanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhanceGradeTest {

    @Test
    fun `looks are distinct and all lift saturation and contrast`() {
        listOf("CINEMATIC", "WARM", "NATURAL").forEach { look ->
            val l = EnhanceGrade.baseLook(look)
            assertTrue("$look contrast", l.contrast > 0f)
            assertTrue("$look saturation", l.saturation > 0f)
            assertTrue("$look exposure ~1", l.exposure in 1f..1.1f)
        }
        // Warm is warmer than natural.
        assertTrue(EnhanceGrade.baseLook("WARM").warmth > EnhanceGrade.baseLook("NATURAL").warmth)
    }

    @Test
    fun `a dark frame is brightened, a bright frame is pulled down`() {
        val (darkExp, _) = EnhanceGrade.autoLevels(0.1f, 0.1f, 0.1f)
        val (brightExp, _) = EnhanceGrade.autoLevels(0.9f, 0.9f, 0.9f)
        assertTrue("dark should brighten (>1): $darkExp", darkExp > 1f)
        assertTrue("bright should darken (<1): $brightExp", brightExp < 1f)
    }

    @Test
    fun `auto exposure is clamped so it never blows out or crushes`() {
        val (veryDark, _) = EnhanceGrade.autoLevels(0.001f, 0.001f, 0.001f)
        val (veryBright, _) = EnhanceGrade.autoLevels(1f, 1f, 1f)
        assertTrue(veryDark <= 1.4f)
        assertTrue(veryBright >= 0.75f)
    }

    @Test
    fun `a cool cast warms and a warm cast cools`() {
        val (_, coolShot) = EnhanceGrade.autoLevels(0.3f, 0.4f, 0.7f) // blue-heavy
        val (_, warmShot) = EnhanceGrade.autoLevels(0.7f, 0.4f, 0.3f) // red-heavy
        assertTrue("cool shot should get positive warmth: $coolShot", coolShot > 0f)
        assertTrue("warm shot should get negative warmth: $warmShot", warmShot < 0f)
    }

    @Test
    fun `channel scales apply warmth as red-up blue-down`() {
        val l = EnhanceGrade.Levels(exposure = 1f, contrast = 0f, saturation = 0f, warmth = 0.2f)
        val s = EnhanceGrade.channelScales(l)
        assertTrue("red boosted", s[0] > s[2])
        assertTrue("blue reduced below 1", s[2] < 1f)
    }

    @Test
    fun `the preview colour matrix is a well-formed 4x5`() {
        val m = EnhanceGrade.colorMatrix(EnhanceGrade.baseLook("CINEMATIC"))
        assertEquals(20, m.size)
        // last row is the alpha passthrough
        assertEquals(1f, m[18], 0.0001f)
    }

    @Test
    fun `neutral settings barely move a mid-grey`() {
        // Natural look, no auto correction: mid-grey stays close to mid-grey.
        val l = EnhanceGrade.levels(EnhanceSettings(enabled = true, look = "NATURAL"))
        val m = EnhanceGrade.colorMatrix(l)
        // red output for input (0.5,0.5,0.5,1): r*128 + g*128 + b*128 + offset, in 0..255
        val out = m[0] * 128 + m[1] * 128 + m[2] * 128 + m[4]
        assertTrue("mid-grey stays mid-ish: $out", out in 96f..168f)
    }

    @Test
    fun `letterbox bars only appear when the frame is taller than 2_39_1`() {
        // A 9:16 portrait frame is taller than 2.39:1 → bars.
        assertTrue(EnhanceGrade.letterboxBarFraction(1080, 1920) > 0f)
        // A 2.39:1 frame → no bars.
        assertEquals(0f, EnhanceGrade.letterboxBarFraction(2390, 1000), 0.001f)
    }
}
