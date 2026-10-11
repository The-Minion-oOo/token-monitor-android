package io.github.theminionooo.tokenmonitor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class WarningContrastTest {
    private val lightThemes = listOf(InterfaceTheme.Porcelain, InterfaceTheme.fromCode("TM1-2563EB-FFFFFF-101010-777777")!!)

    @Test fun lightThemesDrawWarningTextAtWcagAaOnEverySurface() {
        lightThemes.forEach { theme ->
            val palette = Palette.from(theme)
            val surfaces = mapOf(
                "shell" to palette.shell,
                "gradient bottom" to palette.gradientBottom,
                "gradient top" to palette.gradientTop,
                "recessed" to palette.recessed,
                "overlay on shell" to palette.overlay.compositeOver(palette.shell),
                "overlay on gradient bottom" to palette.overlay.compositeOver(palette.gradientBottom),
            )
            mapOf("orange" to palette.orangeText, "danger" to palette.dangerText, "yellow" to palette.yellowText).forEach { (name, color) ->
                surfaces.forEach { (surface, background) ->
                    val ratio = contrast(color, background)
                    assertTrue("${theme.code} $name on $surface is %.2f:1".format(ratio), ratio >= 4.5)
                }
            }
        }
    }

    @Test fun lightVariantsKeepTheirHueSoTheyStillReadAsTheSameWarning() {
        val dark = Palette.from(InterfaceTheme.Default)
        val light = Palette.from(InterfaceTheme.Porcelain)
        assertEquals(hue(dark.orange), hue(light.orangeText), 3.0)
        assertEquals(hue(dark.danger), hue(light.dangerText), 3.0)
        assertEquals(hue(dark.yellow), hue(light.yellowText), 3.0)
    }

    @Test fun fillsKeepTheOriginalWarningColorsAndDarkTextMatchesThem() {
        listOf(InterfaceTheme.Default, InterfaceTheme.Obsidian, InterfaceTheme.Porcelain).forEach { theme ->
            val palette = Palette.from(theme)
            assertEquals(Color(0xFFF4A073), palette.orange)
            assertEquals(Color(0xFFF47788), palette.danger)
            assertEquals(Color(0xFFF1D973), palette.yellow)
        }
        listOf(InterfaceTheme.Default, InterfaceTheme.Obsidian).forEach { theme ->
            val palette = Palette.from(theme)
            assertEquals(palette.orange, palette.orangeText)
            assertEquals(palette.danger, palette.dangerText)
            assertEquals(palette.yellow, palette.yellowText)
        }
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground.compositeOver(background))
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** WCAG 2.x relative luminance of an opaque sRGB color. */
    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double = channel.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun hue(color: Color): Double {
        val (r, g, b) = Triple(color.red.toDouble(), color.green.toDouble(), color.blue.toDouble())
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        val sector = when (max) {
            r -> ((g - b) / delta) % 6
            g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        }
        return (sector * 60 + 360) % 360
    }
}
