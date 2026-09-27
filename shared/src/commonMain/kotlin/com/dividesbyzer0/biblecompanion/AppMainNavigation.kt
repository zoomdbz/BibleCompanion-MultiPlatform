package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class AppMainNavigationItem(
  val route: String,
  val label: StringResource,
  val icon: ImageVector
)

@Composable
internal fun AppMainNavigation(
  selectedRoute: String,
  onSelect: (String) -> Unit,
  rail: Boolean
) {
  val items = listOf(
    AppMainNavigationItem("home", Res.string.ui_nav_home, Icons.Filled.Home),
    AppMainNavigationItem("tab_read", Res.string.ui_nav_read, Icons.AutoMirrored.Filled.MenuBook),
    AppMainNavigationItem("tab_study", Res.string.ui_nav_study, Icons.Filled.School),
    AppMainNavigationItem("tab_calendar", Res.string.ui_nav_calendar, Icons.Filled.CalendarMonth)
  )
  val noInsets = WindowInsets(0, 0, 0, 0)

  if (rail) {
    NavigationRail(windowInsets = noInsets) {
      items.forEach { item ->
        NavigationRailItem(
          selected = selectedRoute == item.route,
          onClick = { if (selectedRoute != item.route) onSelect(item.route) },
          icon = { Icon(item.icon, contentDescription = null) },
          label = { Text(stringResource(item.label)) }
        )
      }
    }
  } else {
    NavigationBar(windowInsets = noInsets) {
      items.forEach { item ->
        NavigationBarItem(
          selected = selectedRoute == item.route,
          onClick = { if (selectedRoute != item.route) onSelect(item.route) },
          icon = { Icon(item.icon, contentDescription = null) },
          label = { Text(stringResource(item.label)) }
        )
      }
    }
  }
}
