package com.dividesbyzer0.biblecompanion

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotesStateWriteTest {
  @Test
  fun writeRemainsAChildOfTheScreenScope() {
    val parent = Job()
    val gate = CompletableDeferred<Unit>()
    val write = CoroutineScope(parent + Dispatchers.Unconfined).launchNoteStateWrite { gate.await() }

    assertTrue(parent.children.any { it === write })
    gate.complete(Unit)
    assertTrue(write.isCompleted)
    parent.complete()
  }

  @Test
  fun leavingTheScreenWaitsForTheProtectedPreferenceWrite() {
    val parent = Job()
    val gate = CompletableDeferred<Unit>()
    var saved = false
    val write = CoroutineScope(parent + Dispatchers.Unconfined).launchNoteStateWrite {
      gate.await()
      saved = true
    }

    parent.cancel()
    assertFalse(saved)
    assertFalse(parent.isCompleted)
    gate.complete(Unit)

    assertTrue(saved)
    assertTrue(write.isCompleted)
    assertTrue(parent.isCompleted)
  }

  @Test
  fun disposalCanStartTheFinalWriteEvenAfterScopeCancellation() {
    val parent = Job().apply { cancel() }
    var saved = false
    val write = CoroutineScope(parent + Dispatchers.Unconfined).launchNoteStateWrite { saved = true }

    assertTrue(saved)
    assertTrue(write.isCompleted)
  }

  @Test
  fun aWriteFailureStillReachesTheParentAndItsExceptionHandler() {
    val parent = Job()
    val gate = CompletableDeferred<Unit>()
    val failure = IllegalStateException("Preference write failed")
    var observed: Throwable? = null
    val handler = CoroutineExceptionHandler { _, error -> observed = error }
    val write = CoroutineScope(parent + Dispatchers.Unconfined + handler).launchNoteStateWrite {
      gate.await()
      throw failure
    }

    gate.complete(Unit)

    assertEquals(failure, observed)
    assertTrue(parent.isCancelled)
    assertTrue(write.isCompleted)
  }
}
