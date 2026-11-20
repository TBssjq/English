package com.ssjq.english.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassQualityTest {

    @Test
    fun 关闭特效后装饰层与色散同时关闭() {
        GlassQuality.configure(false)
        GlassQuality.setEffectsEnabled(true)
        assertTrue(GlassQuality.decorationsEnabled)
        assertTrue(GlassQuality.chromaticAberrationEnabled)
        assertEquals(1f, GlassQuality.blurRadiusPxFactor, 0f)

        GlassQuality.setEffectsEnabled(false)
        assertFalse(GlassQuality.decorationsEnabled)
        assertFalse(GlassQuality.chromaticAberrationEnabled)
        assertEquals(0.5f, GlassQuality.blurRadiusPxFactor, 0f)
    }

    @Test
    fun 低端设备档位同样关闭高开销效果() {
        GlassQuality.configure(true)
        GlassQuality.setEffectsEnabled(true)
        assertFalse(GlassQuality.decorationsEnabled)
        assertFalse(GlassQuality.chromaticAberrationEnabled)
        assertFalse(GlassQuality.lensEnabled)
        assertEquals(0.5f, GlassQuality.blurRadiusPxFactor, 0f)
        GlassQuality.configure(false)
        GlassQuality.setEffectsEnabled(true)
    }
}
