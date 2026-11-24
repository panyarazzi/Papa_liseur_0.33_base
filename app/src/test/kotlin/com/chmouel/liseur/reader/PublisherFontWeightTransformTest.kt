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
    fun `apply script receives requested weight and reports layout changes`() {
        val script = PublisherFontWeightTransform.script(1.75)

        assertTrue(script.contains("var target = 700;"))
        assertTrue(script.contains("var previousTarget = state.target;"))
        assertTrue(script.contains("var(--papa-reader-font-weight, 400)"))
        assertTrue(script.contains("return changed;"))
        assertFalse(script.contains("__PAPA_FONT_WEIGHT__"))
    }

    @Test
    fun `only normal declarations in ordinary style rules are transformed`() {
        val script = PublisherFontWeightTransform.script(1.75)

        assertTrue(script.contains("rule.type === 1 && rule.style"))
        assertTrue(script.contains("value.trim().toLowerCase() !== \"normal\""))
        assertTrue(script.contains("style.getPropertyPriority(\"font-weight\")"))
        assertFalse(script.contains("rule.type === 5 && rule.style"))
    }

    @Test
    fun `embedded font face descriptors remain untouched`() {
        val script = PublisherFontWeightTransform.script(1.75)

        assertTrue(script.contains("@font-face descriptors"))
        assertTrue(script.contains("explicit 400/700 mapping"))
    }

    @Test
    fun `pending stylesheets and inline declarations are detected before mutation`() {
        val script = PublisherFontWeightTransform.needsApplyScript(1.75)

        assertTrue(script.contains("state.target !== target"))
        assertTrue(script.contains("rule.type === 1 && rule.style && hasNormal(rule.style)"))
        assertTrue(script.contains("document.querySelectorAll(\"[style]\")"))
        assertFalse(script.contains("style.setProperty("))
    }
}
