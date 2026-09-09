package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionMotionTest {
    @Test
    fun systemModeFollowsAndroidAnimationState() {
        assertTrue(interactionMotionEnabled(ReduceMotionMode.System, systemAnimationsEnabled = true))
        assertFalse(interactionMotionEnabled(ReduceMotionMode.System, systemAnimationsEnabled = false))
    }

    @Test
    fun explicitModesOverrideAndroidAnimationState() {
        assertFalse(interactionMotionEnabled(ReduceMotionMode.On, systemAnimationsEnabled = true))
        assertTrue(interactionMotionEnabled(ReduceMotionMode.Off, systemAnimationsEnabled = false))
    }
}
