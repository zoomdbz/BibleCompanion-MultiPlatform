package com.dividesbyzer0.biblecompanion.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// iOS has no Material You equivalent. Report unsupported so the picker hides the option.

@Composable
actual fun platformSupportsDynamicColor(): Boolean = false

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null
