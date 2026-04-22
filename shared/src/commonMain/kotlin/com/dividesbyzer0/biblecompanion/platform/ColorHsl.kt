package com.dividesbyzer0.biblecompanion.platform

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure-Kotlin HSL utilities replacing Android's ColorUtils. */
object ColorHsl {

    fun colorToHSL(argb: Int, hsl: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn

        val l = (mx + mn) / 2f

        val s: Float
        val h: Float

        if (d == 0f) {
            h = 0f; s = 0f
        } else {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
                g -> ((b - r) / d + 2f) * 60f
                else -> ((r - g) / d + 4f) * 60f
            }
        }
        hsl[0] = h
        hsl[1] = s
        hsl[2] = l
    }

    fun hslToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1].coerceIn(0f, 1f)
        val l = hsl[2].coerceIn(0f, 1f)

        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
