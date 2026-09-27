package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

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
  background = Color(0xFFF8F3EA),
  onBackground = Color(0xFF221A14),
  surface = Color(0xFFF8F3EA),
  onSurface = Color(0xFF221A14),
  surfaceBright = Color(0xFFFFF9F2),
  surfaceDim = Color(0xFFD8CCB8),
  surfaceContainerLowest = Color(0xFFFFF9F2),
  surfaceContainerLow = Color(0xFFF3ECE0),
  surfaceContainer = Color(0xFFEEE5D7),
  surfaceContainerHigh = Color(0xFFE7DCCB),
  surfaceContainerHighest = Color(0xFFE0D3BF),
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
  background = Color(0xFF211A16),
  onBackground = Color(0xFFEFE4D6),
  surface = Color(0xFF211A16),
  onSurface = Color(0xFFEFE4D6),
  surfaceBright = Color(0xFF51443A),
  surfaceDim = Color(0xFF1C1612),
  surfaceContainerLowest = Color(0xFF17120F),
  surfaceContainerLow = Color(0xFF29211B),
  surfaceContainer = Color(0xFF30271F),
  surfaceContainerHigh = Color(0xFF3A3028),
  surfaceContainerHighest = Color(0xFF45392F),
  surfaceVariant = Color(0xFF524340),
  onSurfaceVariant = Color(0xFFD6C4BD),
  outline = Color(0xFF9E8E88),
  outlineVariant = Color(0xFF79675E),
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
  background = Color(0xFFF6F8F6),
  onBackground = Color(0xFF1A1C17),
  surface = Color(0xFFF6F8F6),
  onSurface = Color(0xFF1A1C17),
  surfaceBright = Color(0xFFFCFEFC),
  surfaceDim = Color(0xFFD0DAD0),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFEEF2EE),
  surfaceContainer = Color(0xFFE5EBE5),
  surfaceContainerHigh = Color(0xFFDCE5DC),
  surfaceContainerHighest = Color(0xFFD3DED4),
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
  background = Color(0xFF1B231C),
  onBackground = Color(0xFFE1E4DA),
  surface = Color(0xFF1B231C),
  onSurface = Color(0xFFE1E4DA),
  surfaceBright = Color(0xFF495B4B),
  surfaceDim = Color(0xFF171E18),
  surfaceContainerLowest = Color(0xFF121812),
  surfaceContainerLow = Color(0xFF232D24),
  surfaceContainer = Color(0xFF2A362B),
  surfaceContainerHigh = Color(0xFF334135),
  surfaceContainerHighest = Color(0xFF3D4D3F),
  surfaceVariant = Color(0xFF43483F),
  onSurfaceVariant = Color(0xFFC3C8BB),
  outline = Color(0xFF8D9387),
  outlineVariant = Color(0xFF647562),
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
  background = Color(0xFFF6F7F9),
  onBackground = Color(0xFF1B1B1F),
  surface = Color(0xFFF6F7F9),
  onSurface = Color(0xFF1B1B1F),
  surfaceBright = Color(0xFFFDFBFF),
  surfaceDim = Color(0xFFCFD3DB),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFEDEFF2),
  surfaceContainer = Color(0xFFE4E7EC),
  surfaceContainerHigh = Color(0xFFDCDFE5),
  surfaceContainerHighest = Color(0xFFD3D7DF),
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
  background = Color(0xFF1B1E27),
  onBackground = Color(0xFFE4E2E6),
  surface = Color(0xFF1B1E27),
  onSurface = Color(0xFFE4E2E6),
  surfaceBright = Color(0xFF4B5666),
  surfaceDim = Color(0xFF171921),
  surfaceContainerLowest = Color(0xFF12141A),
  surfaceContainerLow = Color(0xFF232631),
  surfaceContainer = Color(0xFF2A2E3A),
  surfaceContainerHigh = Color(0xFF343946),
  surfaceContainerHighest = Color(0xFF3E4654),
  surfaceVariant = Color(0xFF44464F),
  onSurfaceVariant = Color(0xFFC5C6D0),
  outline = Color(0xFF8F9099),
  outlineVariant = Color(0xFF667083),
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
  background = Color(0xFFF8F7F7),
  onBackground = Color(0xFF201A19),
  surface = Color(0xFFF8F7F7),
  onSurface = Color(0xFF201A19),
  surfaceBright = Color(0xFFFFFBFB),
  surfaceDim = Color(0xFFD7D2D2),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF0EFEF),
  surfaceContainer = Color(0xFFE9E7E7),
  surfaceContainerHigh = Color(0xFFE2DFDF),
  surfaceContainerHighest = Color(0xFFDBD7D7),
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
  background = Color(0xFF211E1E),
  onBackground = Color(0xFFEDE0DE),
  surface = Color(0xFF211E1E),
  onSurface = Color(0xFFEDE0DE),
  surfaceBright = Color(0xFF524A4A),
  surfaceDim = Color(0xFF1B1818),
  surfaceContainerLowest = Color(0xFF151313),
  surfaceContainerLow = Color(0xFF292525),
  surfaceContainer = Color(0xFF302C2C),
  surfaceContainerHigh = Color(0xFF3A3434),
  surfaceContainerHighest = Color(0xFF463F3F),
  surfaceVariant = Color(0xFF534340),
  onSurfaceVariant = Color(0xFFD8C2BE),
  outline = Color(0xFFA08D89),
  outlineVariant = Color(0xFF796A66),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

/** Returns the color scheme for a given preset in light or dark mode. */
fun colorSchemeFor(
  preset: ThemePreset,
  dark: Boolean,
  customHue: Float = 210f,
  customSaturation: Float = 1f,
  customLightness: Float = .5f
): ColorScheme = when (preset) {
  ThemePreset.Parchment, ThemePreset.Dynamic -> if (dark) ParchmentDark else ParchmentLight
  ThemePreset.Sage -> if (dark) SageDark else SageLight
  ThemePreset.Indigo -> if (dark) IndigoDark else IndigoLight
  ThemePreset.Ink -> if (dark) InkDark else InkLight
  ThemePreset.Custom -> customColorScheme(customHue, customSaturation, customLightness, dark)
}

private fun normalizedHue(hue: Float): Float {
  val finiteHue = if (hue.isFinite()) hue else 0f
  return ((finiteHue % 360f) + 360f) % 360f
}

private fun unitInterval(value: Float, fallback: Float): Float =
  if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

/** Exact sRGB HSL seed used by the custom-theme picker and primary container. */
internal fun customThemeSeedColor(hue: Float, saturation: Float, lightness: Float): Color {
  val h = normalizedHue(hue)
  val s = unitInterval(saturation, 1f)
  val l = unitInterval(lightness, .5f)
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

private fun contrastRatio(first: Color, second: Color): Float {
  val lighter = maxOf(first.luminance(), second.luminance())
  val darker = minOf(first.luminance(), second.luminance())
  return (lighter + .05f) / (darker + .05f)
}

private fun contrastOn(background: Color): Color =
  if (contrastRatio(background, Color.Black) >= contrastRatio(background, Color.White)) Color.Black
  else Color.White

/**
 * Keep the selected hue and saturation, changing only HSL lightness when the
 * accent would vanish against the active surface. This role serves text and
 * icons; the primary container retains the exact picker color separately.
 */
private fun textSafeAccent(hue: Float, saturation: Float, lightness: Float, surface: Color, dark: Boolean): Color {
  val requested = customThemeSeedColor(hue, saturation, lightness)
  fun usable(color: Color): Boolean =
    contrastRatio(color, surface) >= 4.5f && contrastRatio(color, contrastOn(color)) >= 4.5f
  if (usable(requested)) return requested

  val requestedLightness = unitInterval(lightness, .5f)
  var low = if (dark) requestedLightness else 0f
  var high = if (dark) 1f else requestedLightness
  repeat(24) {
    val candidateLightness = (low + high) / 2f
    val candidate = customThemeSeedColor(hue, saturation, candidateLightness)
    if (usable(candidate)) {
      if (dark) high = candidateLightness else low = candidateLightness
    } else {
      if (dark) low = candidateLightness else high = candidateLightness
    }
  }
  return customThemeSeedColor(hue, saturation, if (dark) high else low)
}

private fun customColorScheme(hue: Float, saturation: Float, lightness: Float, dark: Boolean): ColorScheme {
  val h = normalizedHue(hue)
  val s = unitInterval(saturation, 1f)
  val l = unitInterval(lightness, .5f)
  val h2 = normalizedHue(h + 38f)
  val h3 = normalizedHue(h + 78f)
  val neutralSaturation = s * .08f
  val background = customThemeSeedColor(h, neutralSaturation, if (dark) .12f else .98f)
  val surface = background
  val primaryContainer = customThemeSeedColor(h, s, l)
  val secondaryContainer = customThemeSeedColor(h2, s, if (dark) .40f else .62f)
  val tertiaryContainer = customThemeSeedColor(h3, s, if (dark) .40f else .62f)
  val primary = textSafeAccent(h, s, l, surface, dark)
  val secondary = textSafeAccent(h2, s, l, surface, dark)
  val tertiary = textSafeAccent(h3, s, l, surface, dark)
  val outline = textSafeAccent(h, s * .55f, l, surface, dark)
  val outlineVariant = textSafeAccent(h, s * .32f, l, surface, dark)

  return if (dark) darkColorScheme(
    primary = primary,
    onPrimary = contrastOn(primary),
    primaryContainer = primaryContainer,
    onPrimaryContainer = contrastOn(primaryContainer),
    secondary = secondary,
    onSecondary = contrastOn(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = contrastOn(secondaryContainer),
    tertiary = tertiary,
    onTertiary = contrastOn(tertiary),
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = contrastOn(tertiaryContainer),
    background = background,
    onBackground = contrastOn(background),
    surface = surface,
    onSurface = contrastOn(surface),
    surfaceBright = customThemeSeedColor(h, neutralSaturation, .32f),
    surfaceDim = customThemeSeedColor(h, neutralSaturation, .09f),
    surfaceContainerLowest = customThemeSeedColor(h, neutralSaturation, .06f),
    surfaceContainerLow = customThemeSeedColor(h, neutralSaturation, .16f),
    surfaceContainer = customThemeSeedColor(h, neutralSaturation, .19f),
    surfaceContainerHigh = customThemeSeedColor(h, neutralSaturation, .23f),
    surfaceContainerHighest = customThemeSeedColor(h, neutralSaturation, .27f),
    surfaceVariant = customThemeSeedColor(h, s * .20f, .29f),
    onSurfaceVariant = contrastOn(customThemeSeedColor(h, s * .20f, .29f)),
    outline = outline,
    outlineVariant = outlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
  ) else lightColorScheme(
    primary = primary,
    onPrimary = contrastOn(primary),
    primaryContainer = primaryContainer,
    onPrimaryContainer = contrastOn(primaryContainer),
    secondary = secondary,
    onSecondary = contrastOn(secondary),
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = contrastOn(secondaryContainer),
    tertiary = tertiary,
    onTertiary = contrastOn(tertiary),
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = contrastOn(tertiaryContainer),
    background = background,
    onBackground = contrastOn(background),
    surface = surface,
    onSurface = contrastOn(surface),
    surfaceBright = customThemeSeedColor(h, neutralSaturation, .99f),
    surfaceDim = customThemeSeedColor(h, neutralSaturation, .85f),
    surfaceContainerLowest = customThemeSeedColor(h, neutralSaturation, 1f),
    surfaceContainerLow = customThemeSeedColor(h, neutralSaturation, .95f),
    surfaceContainer = customThemeSeedColor(h, neutralSaturation, .92f),
    surfaceContainerHigh = customThemeSeedColor(h, neutralSaturation, .89f),
    surfaceContainerHighest = customThemeSeedColor(h, neutralSaturation, .86f),
    surfaceVariant = customThemeSeedColor(h, s * .20f, .90f),
    onSurfaceVariant = contrastOn(customThemeSeedColor(h, s * .20f, .90f)),
    outline = outline,
    outlineVariant = outlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
  )
}

fun customThemeSwatch(
  hue: Float,
  dark: Boolean,
  saturation: Float = 1f,
  lightness: Float = .5f
): ThemeSwatch {
  return ThemeSwatch(
    primary = customThemeSeedColor(hue, saturation, lightness),
    surface = customThemeSeedColor(hue, saturation * .08f, if (dark) .12f else .98f),
    secondary = customThemeSeedColor(hue + 78f, saturation, lightness)
  )
}

/**
 * Keeps Android's wallpaper-derived accent roles intact while making its dark neutral
 * surfaces less black and easier to distinguish. Material role pairs remain untouched.
 */
internal fun comfortableDynamicColorScheme(scheme: ColorScheme, dark: Boolean): ColorScheme {
  if (!dark) return scheme

  fun surfaceAt(fraction: Float): Color = lerp(scheme.surface, scheme.onSurface, fraction)
  return scheme.copy(
    background = surfaceAt(0.05f),
    surface = surfaceAt(0.05f),
    surfaceDim = surfaceAt(0.025f),
    surfaceContainerLowest = scheme.surface,
    surfaceContainerLow = surfaceAt(0.09f),
    surfaceContainer = surfaceAt(0.12f),
    surfaceContainerHigh = surfaceAt(0.16f),
    surfaceContainerHighest = surfaceAt(0.21f),
    surfaceBright = surfaceAt(0.27f),
    outlineVariant = surfaceAt(0.48f)
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
