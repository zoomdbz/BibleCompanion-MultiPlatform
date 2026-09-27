package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Serializable
internal data class NotesScreenState(
  val expandedHeaders: Set<String> = emptySet(),
  val expandedPaths: Set<String> = emptySet(),
  val firstVisibleItem: Int = 0,
  val firstVisibleOffset: Int = 0
)

internal fun notesScreenKey(language: String, assetFileName: String) = "$language/$assetFileName"

internal fun notesHeaderPath(vararg headers: String): String =
  JsonArray(headers.map { JsonPrimitive(it) }).toString()

/** Bridges the short interval before an asynchronous preference write emits.
 * Reopening a route or rotating must see the last UI state, not an older prefs
 * snapshot. Process restarts still restore from native persisted preferences.
 */
internal object LiveNotesScreenStates {
  private val latest = MutableStateFlow<Map<String, NotesScreenState>>(emptyMap())

  fun read(raw: String, language: String, assetFileName: String): NotesScreenState =
    latest.value[notesScreenKey(language, assetFileName)]
      ?: readNotesScreenState(raw, language, assetFileName)

  fun record(language: String, assetFileName: String, state: NotesScreenState) {
    latest.update { it + (notesScreenKey(language, assetFileName) to state) }
  }
}

internal fun readNotesScreenState(raw: String, language: String, assetFileName: String): NotesScreenState {
  val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return NotesScreenState()
  val entry = root[notesScreenKey(language, assetFileName)]
  if (entry != null) return runCatching { Json.decodeFromString<NotesScreenState>(entry.toString()) }
    .getOrDefault(NotesScreenState())
  // Older versions stored only H2 names under the bare filename. Preserve those
  // names on first visit; only matching headers render as expanded.
  val legacy = root[assetFileName] as? JsonArray ?: return NotesScreenState()
  val headers = legacy.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.toSet()
  return NotesScreenState(expandedHeaders = headers)
}

internal fun mergeNotesScreenState(raw: String, language: String, assetFileName: String, stateJson: String): String {
  val state = runCatching { Json.decodeFromString<NotesScreenState>(stateJson) }.getOrNull() ?: return raw
  val root = runCatching { Json.parseToJsonElement(raw).jsonObject.toMutableMap() }.getOrDefault(mutableMapOf())
  // Keep explicit empty states, otherwise closing everything resurrects legacy
  // expansion on the next visit. Merge inside the native preference transaction.
  root[notesScreenKey(language, assetFileName)] = Json.parseToJsonElement(Json.encodeToString(state.copy(
    firstVisibleItem = state.firstVisibleItem.coerceAtLeast(0),
    firstVisibleOffset = state.firstVisibleOffset.coerceAtLeast(0)
  )))
  return JsonObject(root).toString()
}
