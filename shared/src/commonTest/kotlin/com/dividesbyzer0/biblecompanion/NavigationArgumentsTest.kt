package com.dividesbyzer0.biblecompanion

import androidx.savedstate.savedState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationArgumentsTest {
  @Test
  fun nullableStringArgumentsPreserveBundleGetStringSemantics() {
    val arguments = savedState {
      putString("col", "old_testament")
      putString("bookId", "genesis")
      putString("sourceLang", "en")
      putString("sourceEdition", "bsb")
      putString("empty", "")
      putNull("optional")
      putInt("wrongType", 1)
    }

    assertEquals("old_testament", arguments.stringArgument("col"))
    assertEquals("genesis", arguments.stringArgument("bookId"))
    assertEquals("en", arguments.stringArgument("sourceLang"))
    assertEquals("bsb", arguments.stringArgument("sourceEdition"))
    assertEquals("", arguments.stringArgument("empty"))
    assertNull(arguments.stringArgument("missing"))
    assertNull(arguments.stringArgument("optional"))
    assertNull(arguments.stringArgument("wrongType"))
    assertNull((null as androidx.savedstate.SavedState?).stringArgument("col"))
  }
}
