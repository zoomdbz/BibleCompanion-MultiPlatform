package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Curated color themes for the app. Each preset defines a full Material 3 color scheme
 * for both light and dark modes. Colors were designed around a warm, reverent aesthetic
 * appropriate for a Bible companion app while following M3 contrast guidance
 * (WCAG 4.5:1 minimum for body text).
 *
 * To add a new preset:
 * 1. Add an entry to [ThemePreset].
 * 2. Provide a [ColorScheme] pair (light + dark) below.
 * 3. Wire it up in [colorSchemeFor].
 */
enum class ThemePreset(val key: String) {
  Parchment("parchment"),
  Sage("sage"),
  Indigo("indigo"),
  Ink("ink"),
  Dynamic("dynamic"),
  Custom("custom");

  companion object {
    fun fromKey(k: String?): ThemePreset =
      entries.firstOrNull { it.key.equals(k, ignoreCase = true) } ?: Parchment
  }
}

// ─── Parchment ── warm cream + burnt umber (default, traditional Bible feel)
private val ParchmentLight = lightColorScheme(
  primary = Color(0xFF7D4A3E),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDBCE),
  onPrimaryContainer = Color(0xFF2E1411),
  secondary = Color(0xFF76584A),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDBCA),
  onSecondaryContainer = Color(0xFF2B160C),
  tertiary = Color(0xFF6E5F31),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFF9E3AB),
  onTertiaryContainer = Color(0xFF231B00),
  background = Color(0xFFFBF4EE),
  onBackground = Color(0xFF241916),
  surface = Color(0xFFFBF4EE),
  onSurface = Color(0xFF241916),
  surfaceVariant = Color(0xFFF3DFD6),
  onSurfaceVariant = Color(0xFF524340),
  outline = Color(0xFF84746F),
  outlineVariant = Color(0xFFD6C4BD),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002)
)

private val ParchmentDark = darkColorScheme(
  primary = Color(0xFFF6B8A6),
  onPrimary = Color(0xFF4D2116),
  primaryContainer = Color(0xFF673227),
  onPrimaryContainer = Color(0xFFFFDAD0),
  secondary = Color(0xFFE8BEA9),
  onSecondary = Color(0xFF442A1D),
  secondaryContainer = Color(0xFF5D4031),
  onSecondaryContainer = Color(0xFFFFDAC6),
  tertiary = Color(0xFFDDC78F),
  onTertiary = Color(0xFF3C2F08),
  tertiaryContainer = Color(0xFF55471D),
  onTertiaryContainer = Color(0xFFF9E3AB),
  background = Color(0xFF1B120F),
  onBackground = Color(0xFFF2DED8),
  surface = Color(0xFF1B120F),
  onSurface = Color(0xFFF2DED8),
  surfaceVariant = Color(0xFF524340),
  onSurfaceVariant = Color(0xFFD6C4BD),
  outline = Color(0xFF9E8E88),
  outlineVariant = Color(0xFF524340),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

// ─── Sage ── muted green + warm stone (calm, devotional)
private val SageLight = lightColorScheme(
  primary = Color(0xFF4C6B4D),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFCEEBCC),
  onPrimaryContainer = Color(0xFF082110),
  secondary = Color(0xFF54634E),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFD7E8CE),
  onSecondaryContainer = Color(0xFF121F0F),
  tertiary = Color(0xFF38656A),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFBCEBF0),
  onTertiaryContainer = Color(0xFF002023),
  background = Color(0xFFF9FBF1),
  onBackground = Color(0xFF1A1C17),
  surface = Color(0xFFF9FBF1),
  onSurface = Color(0xFF1A1C17),
  surfaceVariant = Color(0xFFDFE4D7),
  onSurfaceVariant = Color(0xFF43483F),
  outline = Color(0xFF73796E),
  outlineVariant = Color(0xFFC3C8BB),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002)
)

private val SageDark = darkColorScheme(
  primary = Color(0xFFB4D2B1),
  onPrimary = Color(0xFF213824),
  primaryContainer = Color(0xFF384F39),
  onPrimaryContainer = Color(0xFFD0EFCC),
  secondary = Color(0xFFBACCB4),
  onSecondary = Color(0xFF253423),
  secondaryContainer = Color(0xFF3B4B38),
  onSecondaryContainer = Color(0xFFD7E8CE),
  tertiary = Color(0xFFA2CED3),
  onTertiary = Color(0xFF01363B),
  tertiaryContainer = Color(0xFF1E4D52),
  onTertiaryContainer = Color(0xFFBCEBF0),
  background = Color(0xFF111411),
  onBackground = Color(0xFFE1E4DA),
  surface = Color(0xFF111411),
  onSurface = Color(0xFFE1E4DA),
  surfaceVariant = Color(0xFF43483F),
  onSurfaceVariant = Color(0xFFC3C8BB),
  outline = Color(0xFF8D9387),
  outlineVariant = Color(0xFF43483F),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

// ─── Indigo ── modern slate blue (clean, contemporary)
private val IndigoLight = lightColorScheme(
  primary = Color(0xFF3A5BA0),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFD9E2FF),
  onPrimaryContainer = Color(0xFF001947),
  secondary = Color(0xFF575E71),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFDBE2F9),
  onSecondaryContainer = Color(0xFF141B2C),
  tertiary = Color(0xFF735471),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFED7F8),
  onTertiaryContainer = Color(0xFF2B122B),
  background = Color(0xFFFAF8FD),
  onBackground = Color(0xFF1B1B1F),
  surface = Color(0xFFFAF8FD),
  onSurface = Color(0xFF1B1B1F),
  surfaceVariant = Color(0xFFE1E2EC),
  onSurfaceVariant = Color(0xFF44464F),
  outline = Color(0xFF757780),
  outlineVariant = Color(0xFFC5C6D0),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002)
)

private val IndigoDark = darkColorScheme(
  primary = Color(0xFFB3C5FF),
  onPrimary = Color(0xFF002E6A),
  primaryContainer = Color(0xFF1D4391),
  onPrimaryContainer = Color(0xFFD9E2FF),
  secondary = Color(0xFFBFC6DC),
  onSecondary = Color(0xFF293041),
  secondaryContainer = Color(0xFF3F4758),
  onSecondaryContainer = Color(0xFFDBE2F9),
  tertiary = Color(0xFFE2BBDD),
  onTertiary = Color(0xFF422741),
  tertiaryContainer = Color(0xFF5B3D59),
  onTertiaryContainer = Color(0xFFFED7F8),
  background = Color(0xFF121318),
  onBackground = Color(0xFFE4E2E6),
  surface = Color(0xFF121318),
  onSurface = Color(0xFFE4E2E6),
  surfaceVariant = Color(0xFF44464F),
  onSurfaceVariant = Color(0xFFC5C6D0),
  outline = Color(0xFF8F9099),
  outlineVariant = Color(0xFF44464F),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

// ─── Ink ── near-black/white with red-letter accent (traditional, printed-book feel)
private val InkLight = lightColorScheme(
  primary = Color(0xFFAF3434),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDAD6),
  onPrimaryContainer = Color(0xFF400F0B),
  secondary = Color(0xFF775653),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDAD6),
  onSecondaryContainer = Color(0xFF2C1513),
  tertiary = Color(0xFF735A2F),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFFDEAE),
  onTertiaryContainer = Color(0xFF281800),
  background = Color(0xFFFFFBFF),
  onBackground = Color(0xFF201A19),
  surface = Color(0xFFFFFBFF),
  onSurface = Color(0xFF201A19),
  surfaceVariant = Color(0xFFF5DDD9),
  onSurfaceVariant = Color(0xFF534340),
  outline = Color(0xFF85736F),
  outlineVariant = Color(0xFFD8C2BE),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002)
)

private val InkDark = darkColorScheme(
  primary = Color(0xFFFFB4AA),
  onPrimary = Color(0xFF601418),
  primaryContainer = Color(0xFF7E2A25),
  onPrimaryContainer = Color(0xFFFFDAD6),
  secondary = Color(0xFFE7BDB9),
  onSecondary = Color(0xFF432B29),
  secondaryContainer = Color(0xFF5D403D),
  onSecondaryContainer = Color(0xFFFFDAD6),
  tertiary = Color(0xFFE3C18E),
  onTertiary = Color(0xFF412D06),
  tertiaryContainer = Color(0xFF5A431B),
  onTertiaryContainer = Color(0xFFFFDEAE),
  background = Color(0xFF0F0D0D),
  onBackground = Color(0xFFEDE0DE),
  surface = Color(0xFF0F0D0D),
  onSurface = Color(0xFFEDE0DE),
  surfaceVariant = Color(0xFF534340),
  onSurfaceVariant = Color(0xFFD8C2BE),
  outline = Color(0xFFA08D89),
  outlineVariant = Color(0xFF534340),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

/** Returns the color scheme for a given preset in light or dark mode. */
fun colorSchemeFor(preset: ThemePreset, dark: Boolean, customHue: Float = 210f): ColorScheme = when (preset) {
  ThemePreset.Parchment, ThemePreset.Dynamic -> if (dark) ParchmentDark else ParchmentLight
  ThemePreset.Sage -> if (dark) SageDark else SageLight
  ThemePreset.Indigo -> if (dark) IndigoDark else IndigoLight
  ThemePreset.Ink -> if (dark) InkDark else InkLight
  ThemePreset.Custom -> customColorScheme(customHue, dark)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
  val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
  val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
  val m = l - c / 2f
  val (r, g, b) = when {
    h < 60f  -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else     -> Triple(c, 0f, x)
  }
  return Color(r + m, g + m, b + m)
}

private fun customColorScheme(hue: Float, dark: Boolean): ColorScheme {
  val h = hue.coerceIn(0f, 360f)
  val h2 = (h + 30f) % 360f
  val h3 = (h + 60f) % 360f
  return if (dark) darkColorScheme(
    primary = hslToColor(h, 0.50f, 0.72f),
    onPrimary = hslToColor(h, 0.40f, 0.18f),
    primaryContainer = hslToColor(h, 0.40f, 0.30f),
    onPrimaryContainer = hslToColor(h, 0.55f, 0.90f),
    secondary = hslToColor(h2, 0.30f, 0.70f),
    onSecondary = hslToColor(h2, 0.20f, 0.18f),
    secondaryContainer = hslToColor(h2, 0.25f, 0.28f),
    onSecondaryContainer = hslToColor(h2, 0.35f, 0.88f),
    tertiary = hslToColor(h3, 0.35f, 0.72f),
    onTertiary = hslToColor(h3, 0.25f, 0.18f),
    tertiaryContainer = hslToColor(h3, 0.30f, 0.28f),
    onTertiaryContainer = hslToColor(h3, 0.40f, 0.88f),
    background = hslToColor(h, 0.08f, 0.08f),
    onBackground = hslToColor(h, 0.08f, 0.90f),
    surface = hslToColor(h, 0.08f, 0.08f),
    onSurface = hslToColor(h, 0.08f, 0.90f),
    surfaceVariant = hslToColor(h, 0.10f, 0.22f),
    onSurfaceVariant = hslToColor(h, 0.10f, 0.78f),
    outline = hslToColor(h, 0.10f, 0.55f),
    outlineVariant = hslToColor(h, 0.10f, 0.30f),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
  ) else lightColorScheme(
    primary = hslToColor(h, 0.55f, 0.38f),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = hslToColor(h, 0.60f, 0.88f),
    onPrimaryContainer = hslToColor(h, 0.50f, 0.12f),
    secondary = hslToColor(h2, 0.30f, 0.40f),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = hslToColor(h2, 0.35f, 0.88f),
    onSecondaryContainer = hslToColor(h2, 0.25f, 0.12f),
    tertiary = hslToColor(h3, 0.35f, 0.38f),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = hslToColor(h3, 0.40f, 0.88f),
    onTertiaryContainer = hslToColor(h3, 0.30f, 0.12f),
    background = hslToColor(h, 0.08f, 0.97f),
    onBackground = hslToColor(h, 0.08f, 0.10f),
    surface = hslToColor(h, 0.08f, 0.97f),
    onSurface = hslToColor(h, 0.08f, 0.10f),
    surfaceVariant = hslToColor(h, 0.12f, 0.90f),
    onSurfaceVariant = hslToColor(h, 0.10f, 0.30f),
    outline = hslToColor(h, 0.10f, 0.48f),
    outlineVariant = hslToColor(h, 0.12f, 0.78f),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
  )
}

fun customThemeSwatch(hue: Float, dark: Boolean): ThemeSwatch {
  return ThemeSwatch(
    primary = if (dark) hslToColor(hue, 0.50f, 0.72f) else hslToColor(hue, 0.55f, 0.38f),
    surface = if (dark) hslToColor(hue, 0.08f, 0.08f) else hslToColor(hue, 0.08f, 0.97f),
    secondary = if (dark) hslToColor((hue + 60f) % 360f, 0.35f, 0.72f) else hslToColor((hue + 60f) % 360f, 0.35f, 0.38f)
  )
}

/**
 * Small swatch colors for the Settings theme picker. Shown as a color chip so users
 * can see the accent at a glance before committing.
 */
data class ThemeSwatch(
  val primary: Color,
  val surface: Color,
  val secondary: Color
)

fun swatchFor(preset: ThemePreset, dark: Boolean): ThemeSwatch {
  val scheme = colorSchemeFor(preset, dark)
  return ThemeSwatch(
    primary = scheme.primary,
    surface = scheme.surface,
    secondary = scheme.tertiary
  )
}
