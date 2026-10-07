package com.dividesbyzer0.biblecompanion.platform

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dividesbyzer0.biblecompanion.comfortableDynamicColorScheme

@Composable
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
actual fun platformSupportsDynamicColor(): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
  val context = LocalContext.current
  val wallpaperScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  return comfortableDynamicColorScheme(wallpaperScheme, dark)
}
