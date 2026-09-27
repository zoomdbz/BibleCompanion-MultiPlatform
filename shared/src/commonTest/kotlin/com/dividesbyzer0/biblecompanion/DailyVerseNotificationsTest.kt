package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyVerseNotificationsTest {
  @Test fun remindersDefaultToOffWithNineAmTime() {
    assertFalse(PrefsState().dailyVerseNotifications)
    assertEquals(540, PrefsState().dailyVerseNotificationMinuteOfDay)
  }

  @Test fun notificationTextRemovesDisplayMarkupWithoutRemovingWords() {
    assertEquals("Jesus said, Peace. Added words.", notificationPlainText(
      "Jesus said, [J]Peace.[/J] [ADD]Added words.[/ADD]", "traditional", "en", "new_testament"))
  }

  @Test fun notificationTextHonorsDivineNamePreference() {
    assertEquals("the angel of Yahweh spoke", notificationPlainText(
      "the angel of the Lord spoke", "yahweh", "en", "deuterocanonical"))
  }

  @Test fun completeCombinedVerseUnitsCanOpenWithoutSplittingThem() {
    assertTrue(notificationAnchorExists(listOf("Text (3:4-6)."), 3, 4, 6))
    assertTrue(notificationAnchorExists(listOf("Text (3:4-6)."), 3, 5, 5))
    assertFalse(notificationAnchorExists(listOf("Text (3:4-6)."), 3, 4, 7))
  }

  @Test fun embeddedCrossReferencesCannotSupplyANotificationDestination() {
    val bullet = "Text with John 1:1 in a note (7:15)."
    assertTrue(notificationAnchorExists(listOf(bullet), 7, 15, 15))
    assertFalse(notificationAnchorExists(listOf(bullet), 1, 1, 1))
    assertFalse(notificationAnchorExists(listOf(bullet), 7, 16, 15))
  }

  @Test fun missingMiddleVerseFailsClosed() {
    assertFalse(notificationAnchorExists(listOf("One (1:1).", "Three (1:3)."), 1, 1, 3))
  }

  @Test fun permissionFeedbackDoesNotDependOnAnActivityCallback() {
    DailyVerseNotificationBridge.permissionRequestFinished(false)
    assertTrue(DailyVerseNotificationBridge.permissionRequestDenied.value)
    assertEquals(false, DailyVerseNotificationBridge.permissionAllowed.value)
    DailyVerseNotificationBridge.clearPermissionRequestFeedback()
    assertFalse(DailyVerseNotificationBridge.permissionRequestDenied.value)
    DailyVerseNotificationBridge.permissionRequestFinished(true)
    assertEquals(true, DailyVerseNotificationBridge.permissionAllowed.value)
    assertFalse(DailyVerseNotificationBridge.permissionRequestDenied.value)
  }
}
