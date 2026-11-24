package com.chmouel.liseur.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublisherFontWeightTransformTest {

    @Test
    fun `weight multiplier maps to a CSS font weight`() {
        assertEquals(400, PublisherFontWeightTransform.targetWeight(null))
        assertEquals(300, PublisherFontWeightTransform.targetWeight(0.75))
        assertEquals(400, PublisherFontWeightTransform.targetWeight(1.0))
        assertEquals(700, PublisherFontWeightTransform.targetWeight(1.75))
        assertEquals(1000, PublisherFontWeightTransform.targetWeight(2.5))
    }

    @Test
    fun `invalid multipliers fall back to normal weight`() {
        assertEquals(400, PublisherFontWeightTransform.targetWeight(Double.NaN))
        assertEquals(400, PublisherFontWeightTransform.targetWeight(Double.POSITIVE_INFINITY))
        assertEquals(400, PublisherFontWeightTransform.targetWeight(-1.0))
    }

    @Test
    fun `enabled script applies the requested weight to normal declarations`() {
        val script = PublisherFontWeightTransform.script(1.75, enabled = true)

        assertTrue(script.contains("var target = 700;"))
        assertTrue(script.contains("var enabled = true;"))
        assertTrue(script.contains("value.trim().toLowerCase()"))
        assertTrue(script.contains("normalized === \"normal\""))
        assertTrue(script.contains("var(--papa-reader-font-weight, 400)"))
        assertTrue(script.contains("style.getPropertyPriority(\"font-weight\")"))
        assertTrue(script.contains("return changed;"))
        assertFalse(script.contains("__PAPA_FONT_WEIGHT__"))
        assertFalse(script.contains("__PAPA_FONT_WEIGHT_ENABLED__"))
    }

    @Test
    fun `disabled script restores declarations owned by the transform`() {
        val script = PublisherFontWeightTransform.script(1.0, enabled = false)

        assertTrue(script.contains("var enabled = false;"))
        assertTrue(script.contains("normalized === \"var(--papa-reader-font-weight, 400)\""))
        assertTrue(script.contains("style.setProperty(\"font-weight\", \"normal\", priority)"))
    }

    @Test
    fun `embedded font face descriptors are excluded from rewriting`() {
        val script = PublisherFontWeightTransform.script(1.75, enabled = true)

        assertTrue(script.contains("rule.type === 1 && rule.style"))
        assertTrue(script.contains("@font-face descriptors"))
        assertFalse(script.contains("rule.type === 5 && rule.style"))
    }

    @Test
    fun `inline styles and later stylesheet changes are handled`() {
        val script = PublisherFontWeightTransform.script(1.75, enabled = true)

        assertTrue(script.contains("document.querySelectorAll(\"[style]\")"))
        assertTrue(script.contains("new MutationObserver"))
        assertTrue(script.contains("document.addEventListener(\"load\", state.schedule, true)"))
        assertTrue(script.contains("attributeFilter: [\"style\", \"href\", \"rel\"]"))
    }
}
