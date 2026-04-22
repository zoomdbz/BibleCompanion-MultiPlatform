package com.dividesbyzer0.biblecompanion.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** True if the platform supports Material You dynamic color (Android 12+ only). */
@Composable
expect fun platformSupportsDynamicColor(): Boolean

/**
 * Returns a dynamic color scheme derived from the system wallpaper/accent.
 * Returns null if not supported on this platform or OS version.
 */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
