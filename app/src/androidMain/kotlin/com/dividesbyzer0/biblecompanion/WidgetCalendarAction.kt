package com.dividesbyzer0.biblecompanion

import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity

internal fun calendarWidgetOpenAction(): Action = actionStartActivity(
  Intent("com.dividesbyzer0.biblecompanion.FEAST_CALENDAR").apply {
    setClassName("com.dividesbyzer0.biblecompanion", MainActivity::class.java.name)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }
)
