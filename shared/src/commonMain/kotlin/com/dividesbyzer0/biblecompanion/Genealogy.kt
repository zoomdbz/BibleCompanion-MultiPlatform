package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.platformOpenExternalReader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

object GenealogyData {
  val trunk: List<GeneNode> = listOf(
    GeneNode("Adam", listOf("Genesis 5:1–5", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Seth", listOf("Genesis 5:3–8", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Enosh", listOf("Genesis 5:6–11", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Kenan", listOf("Genesis 5:9–14", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Mahalalel", listOf("Genesis 5:12–17", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Jared", listOf("Genesis 5:15–20", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Enoch", listOf("Genesis 5:21–24", "Luke 3:37", "1 Chronicles 1:3")),
    GeneNode("Methuselah", listOf("Genesis 5:25–27", "Luke 3:37", "1 Chronicles 1:3")),
    GeneNode("Lamech", listOf("Genesis 5:28–31", "Luke 3:36", "1 Chronicles 1:3")),
    GeneNode("Noah", listOf("Genesis 5:32", "Genesis 6:9–10", "Luke 3:36", "1 Chronicles 1:4")),
    GeneNode("Shem", listOf("Genesis 10:21–32", "Genesis 11:10–11", "Luke 3:36", "1 Chronicles 1:4")),
    GeneNode("Arphaxad", listOf("Genesis 11:12–13", "Luke 3:36", "1 Chronicles 1:17")),
    GeneNode("Shelah", listOf("Genesis 11:14–15", "Luke 3:35", "1 Chronicles 1:18")),
    GeneNode("Eber", listOf("Genesis 10:24–25", "Genesis 11:16–17", "Luke 3:35", "1 Chronicles 1:18–19")),
    GeneNode("Peleg", listOf("Genesis 10:25", "Genesis 11:18–19", "Luke 3:35", "1 Chronicles 1:19")),
    GeneNode("Reu", listOf("Genesis 11:20–21", "Luke 3:35", "1 Chronicles 1:25")),
    GeneNode("Serug", listOf("Genesis 11:22–23", "Luke 3:35", "1 Chronicles 1:26")),
    GeneNode("Nahor", listOf("Genesis 11:22–25", "Luke 3:34", "1 Chronicles 1:26")),
    GeneNode("Terah", listOf("Genesis 11:24–32", "Luke 3:34", "1 Chronicles 1:26–27")),
    GeneNode("Abraham", listOf("Genesis 11:26–32", "Genesis 12:1–3", "Luke 3:34", "1 Chronicles 1:27")),
    GeneNode("Isaac", listOf("Genesis 21:1–7", "Genesis 25:19–26", "Luke 3:34", "1 Chronicles 1:34")),
    GeneNode("Jacob", listOf("Genesis 25:24–26", "Genesis 35:9–12", "Luke 3:34", "1 Chronicles 1:34")),
    GeneNode("Judah", listOf("Genesis 29:35", "Genesis 49:8–10", "Luke 3:33", "1 Chronicles 2:3"), wife = "Tamar", wifeRef = "Matthew 1:3"),
    GeneNode("Perez", listOf("Genesis 38:27–30", "Ruth 4:18", "Luke 3:33", "1 Chronicles 2:4–5")),
    GeneNode("Hezron", listOf("Ruth 4:18", "Luke 3:33", "1 Chronicles 2:5,9")),
    GeneNode("Ram (Aram)", listOf("Ruth 4:19", "Matthew 1:3–4", "Luke 3:33", "1 Chronicles 2:9–10")),
    GeneNode("Amminadab", listOf("Ruth 4:19–20", "Matthew 1:4", "Luke 3:33", "1 Chronicles 2:10")),
    GeneNode("Nahshon", listOf("Ruth 4:20", "Matthew 1:4", "Luke 3:32", "1 Chronicles 2:10–12")),
    GeneNode("Salmon", listOf("Ruth 4:20–21", "Matthew 1:4–5", "1 Chronicles 2:11–12"), wife = "Rahab", wifeRef = "Matthew 1:5"),
    GeneNode("Boaz", listOf("Ruth 4:21", "Matthew 1:5", "1 Chronicles 2:11–12"), wife = "Ruth", wifeRef = "Matthew 1:5"),
    GeneNode("Obed", listOf("Ruth 4:21", "Matthew 1:5", "1 Chronicles 2:12")),
    GeneNode("Jesse", listOf("Ruth 4:22", "Matthew 1:5–6", "1 Chronicles 2:12–13")),
    GeneNode("David", listOf("Ruth 4:22", "1 Samuel 16", "Matthew 1:6", "Luke 3:31", "1 Chronicles 2:13–15"), wife = "Bathsheba", wifeRef = "Matthew 1:6")
  )

  val matthew: List<GeneNode> = listOf(
    GeneNode("David", listOf("Matthew 1:6", "1 Chronicles 3:1–9")),
    GeneNode("Solomon", listOf("Matthew 1:6–7", "1 Chronicles 3:10")),
    GeneNode("Rehoboam", listOf("Matthew 1:7", "1 Chronicles 3:10")),
    GeneNode("Abijah (Abijam)", listOf("Matthew 1:7", "1 Chronicles 3:10")),
    GeneNode("Asa", listOf("Matthew 1:7–8", "1 Chronicles 3:10")),
    GeneNode("Jehoshaphat", listOf("Matthew 1:8", "1 Chronicles 3:10")),
    GeneNode("Joram (Jehoram)", listOf("Matthew 1:8", "1 Chronicles 3:11")),
    GeneNode("Uzziah (Azariah)", listOf("Matthew 1:8–9", "1 Chronicles 3:12")),
    GeneNode("Jotham", listOf("Matthew 1:9", "1 Chronicles 3:12")),
    GeneNode("Ahaz", listOf("Matthew 1:9", "1 Chronicles 3:13")),
    GeneNode("Hezekiah", listOf("Matthew 1:9–10", "1 Chronicles 3:13")),
    GeneNode("Manasseh", listOf("Matthew 1:10", "1 Chronicles 3:13")),
    GeneNode("Amon", listOf("Matthew 1:10", "1 Chronicles 3:14")),
    GeneNode("Josiah", listOf("Matthew 1:10–11", "1 Chronicles 3:14–15")),
    GeneNode("Jeconiah (Jehoiachin)", listOf("Matthew 1:11–12", "1 Chronicles 3:16")),
    GeneNode("Shealtiel", listOf("Matthew 1:12", "1 Chronicles 3:17")),
    GeneNode("Zerubbabel", listOf("Matthew 1:12–13", "1 Chronicles 3:19")),
    GeneNode("Abiud", listOf("Matthew 1:13")),
    GeneNode("Eliakim", listOf("Matthew 1:13")),
    GeneNode("Azor", listOf("Matthew 1:13–14")),
    GeneNode("Zadok", listOf("Matthew 1:14")),
    GeneNode("Achim", listOf("Matthew 1:14")),
    GeneNode("Eliud", listOf("Matthew 1:14–15")),
    GeneNode("Eleazar", listOf("Matthew 1:15")),
    GeneNode("Matthan", listOf("Matthew 1:15")),
    GeneNode("Jacob", listOf("Matthew 1:15–16")),
    GeneNode("Joseph", listOf("Matthew 1:16"), wife = "Mary", wifeRef = "Matthew 1:16"),
    GeneNode("Jesus", listOf("Matthew 1:16"))
  )

  val luke: List<GeneNode> = listOf(
    GeneNode("David", listOf("Luke 3:31")),
    GeneNode("Nathan", listOf("Luke 3:31")),
    GeneNode("Mattatha (Mattathah)", listOf("Luke 3:31")),
    GeneNode("Menna", listOf("Luke 3:31")),
    GeneNode("Melea", listOf("Luke 3:31")),
    GeneNode("Eliakim", listOf("Luke 3:30")),
    GeneNode("Jonam", listOf("Luke 3:30")),
    GeneNode("Joseph", listOf("Luke 3:30")),
    GeneNode("Judah", listOf("Luke 3:30")),
    GeneNode("Simeon", listOf("Luke 3:30")),
    GeneNode("Levi", listOf("Luke 3:29")),
    GeneNode("Matthat", listOf("Luke 3:29")),
    GeneNode("Jorim", listOf("Luke 3:29")),
    GeneNode("Eliezer", listOf("Luke 3:29")),
    GeneNode("Joshua", listOf("Luke 3:29")),
    GeneNode("Er", listOf("Luke 3:28")),
    GeneNode("Elmadam", listOf("Luke 3:28")),
    GeneNode("Cosam", listOf("Luke 3:28")),
    GeneNode("Addi", listOf("Luke 3:28")),
    GeneNode("Melchi", listOf("Luke 3:28")),
    GeneNode("Neri", listOf("Luke 3:27")),
    GeneNode("Shealtiel", listOf("Luke 3:27")),
    GeneNode("Zerubbabel", listOf("Luke 3:27")),
    GeneNode("Rhesa", listOf("Luke 3:27")),
    GeneNode("Joanan", listOf("Luke 3:27")),
    GeneNode("Joda", listOf("Luke 3:26")),
    GeneNode("Josech", listOf("Luke 3:26")),
    GeneNode("Semein (Semei)", listOf("Luke 3:26")),
    GeneNode("Mattathias (1)", listOf("Luke 3:26")),
    GeneNode("Maath", listOf("Luke 3:26")),
    GeneNode("Naggai", listOf("Luke 3:25")),
    GeneNode("Esli", listOf("Luke 3:25")),
    GeneNode("Nahum", listOf("Luke 3:25")),
    GeneNode("Amos", listOf("Luke 3:25")),
    GeneNode("Mattathias (2)", listOf("Luke 3:25")),
    GeneNode("Joseph", listOf("Luke 3:24")),
    GeneNode("Jannai (Janna)", listOf("Luke 3:24")),
    GeneNode("Melchi", listOf("Luke 3:24")),
    GeneNode("Levi", listOf("Luke 3:24")),
    GeneNode("Matthat", listOf("Luke 3:24")),
    GeneNode("Heli", listOf("Luke 3:23")),
    GeneNode("Joseph", listOf("Luke 3:23")),
    GeneNode("Jesus", listOf("Luke 3:23"))
  )
}

/**
 * Splits the two post-David genealogies into independent-length segments that
 * meet at the shared nodes. Matthew and Luke have different generation counts
 * (roughly 26 vs 41 names David to Jesus), so they are never forced into the
 * same rows; each column runs its true length and the rails converge at
 * Zerubbabel and again at Jesus.
 */
object GenealogyRows {
  private fun sameName(a: String, b: String): Boolean =
    a.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^a-z0-9]"), "") ==
            b.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^a-z0-9]"), "")

  private fun canonicalName(s: String) = s.replace(Regex("\\(.*?\\)"), "").trim()

  private fun indexOfName(list: List<GeneNode>, start: Int, target: String): Int {
    for (i in start until list.size) if (sameName(list[i].name, target)) return i
    return -1
  }

  fun segments(
    trunk: List<GeneNode>,
    matthewFull: List<GeneNode>,
    lukeFull: List<GeneNode>
  ): GeneScreenModel {
    val matthew = matthewFull.drop(1)
    val luke = lukeFull.drop(1)

    val mZ = indexOfName(matthew, 0, "Zerubbabel")
    val lZ = indexOfName(luke, 0, "Zerubbabel")
    val mJ = indexOfName(matthew, if (mZ >= 0) mZ + 1 else 0, "Jesus")
    val lJ = indexOfName(luke, if (lZ >= 0) lZ + 1 else 0, "Jesus")

    val seg1Mt = matthew.subList(0, if (mZ >= 0) mZ else matthew.size)
    val seg1Lk = luke.subList(0, if (lZ >= 0) lZ else luke.size)
    val converge1 = if (mZ >= 0 && lZ >= 0) {
      ConvergeNode(canonicalName(matthew[mZ].name), matthew[mZ].refs.first(), luke[lZ].refs.first())
    } else null

    val seg2Mt = if (mZ >= 0 && mJ >= 0) matthew.subList(mZ + 1, mJ) else emptyList()
    val seg2Lk = if (lZ >= 0 && lJ >= 0) luke.subList(lZ + 1, lJ) else emptyList()
    val converge2 = if (mJ >= 0 && lJ >= 0) {
      ConvergeNode(canonicalName(matthew[mJ].name), matthew[mJ].refs.first(), luke[lJ].refs.first())
    } else null

    return GeneScreenModel(trunk, seg1Mt, seg1Lk, converge1, seg2Mt, seg2Lk, converge2)
  }
}

data class GeneScreenModel(
  val trunk: List<GeneNode>,
  val seg1Mt: List<GeneNode>,
  val seg1Lk: List<GeneNode>,
  val converge1: ConvergeNode?,
  val seg2Mt: List<GeneNode>,
  val seg2Lk: List<GeneNode>,
  val converge2: ConvergeNode?
)

private fun languageFolder(tag: String): String =
  LocaleUtils.effectiveAssetTag(tag)

private fun detectCollectionFromRef(ref: String): String =
  if (
    ref.startsWith("Matt", true) || ref.startsWith("Luke", true) ||
    ref.startsWith("Mark", true) || ref.startsWith("John", true)
  ) "new_testament" else "old_testament"

/**
 * Opens a reference. Internal reader now routes through the app's own
 * [ScriptureRefs.parseInternalRefTail], so the tap lands on the exact verse
 * rather than the top of the chapter. External modes keep their existing
 * verse-aware links.
 */
private fun navigateToRef(
  ref: String?,
  prefs: PrefsState,
  ctx: PlatformContext,
  scope: CoroutineScope,
  internalNav: (String, String, String?, Int?, Int?) -> Unit
) {
  val r = ref?.trim().orEmpty()
  if (r.isEmpty()) return
  val col = detectCollectionFromRef(r)
  if (prefs.readerMode == "internal") {
    val bookName = ScriptureRefs.canonBookOfRef(r) ?: return
    val bookId = bookName.trim().lowercase().replace(' ', '_')
    val tail = r.substringAfter(bookName).trim()
    val target = ScriptureRefs.parseInternalRefTail(bookId, tail)
    internalNav(col, bookId, "$bookId-${target.chapter}", target.verse, target.verseEnd)
  } else {
    scope.launch(Dispatchers.Default) {
      val url = Linker.toLink(
        collection = col,
        ref = r,
        translation = prefs.translation,
        preferBibleCom = prefs.readerMode == "biblecom",
        appLanguage = prefs.appLanguage
      )
      if (url.isNotBlank()) {
        withContext(Dispatchers.Main) {
          runCatching { platformOpenExternalReader(ctx, url) }
        }
      }
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GenealogyScreen(prefs: PrefsState, onBack: () -> Unit) {
  val ctx = LocalPlatformContext.current

  val model = remember {
    GenealogyRows.segments(
      GenealogyData.trunk,
      GenealogyData.matthew,
      GenealogyData.luke
    )
  }

  var notes by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(prefs.appLanguage) {
    val folder = languageFolder(prefs.appLanguage)
    val candidates = listOf(
      "notes/$folder/genealogy_notes.md",
      "notes/en/genealogy_notes.md"
    )
    notes = candidates.firstNotNullOfOrNull { path ->
      readAssetText(ctx, path)
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.genealogy)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.back)
            )
          }
        }
      )
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.padding(pad),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
      notes?.let { text ->
        if (text.isNotBlank()) {
          item {
            RenderNotesMarkdown(body = text, prefs = prefs)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
          }
        }
      }

      // Trunk: single centered rail from Adam to David.
      itemsIndexed(model.trunk) { i, node ->
        if (i > 0) TrunkConnector()
        CenterCard(node, prefs)
      }

      // Fork into the two gospel columns.
      item {
        ForkConnector()
        Spacer(Modifier.height(4.dp))
        ForkHeader()
        Spacer(Modifier.height(6.dp))
      }
      item { ForkColumns(model.seg1Mt, model.seg1Lk, prefs) }

      // First convergence: Zerubbabel.
      model.converge1?.let { c ->
        item {
          MergeConnector()
          Spacer(Modifier.height(6.dp))
          ConvergeCard(c, prefs)
        }
      }

      // Split again toward Jesus.
      if (model.seg2Mt.isNotEmpty() || model.seg2Lk.isNotEmpty()) {
        item {
          Spacer(Modifier.height(6.dp))
          ForkConnector()
          Spacer(Modifier.height(6.dp))
        }
        item { ForkColumns(model.seg2Mt, model.seg2Lk, prefs) }
      }

      // Final convergence: Jesus.
      model.converge2?.let { c ->
        item {
          MergeConnector()
          Spacer(Modifier.height(6.dp))
          ConvergeCard(c, prefs)
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }
}

/** Solid vertical link between trunk cards. */
@Composable
private fun TrunkConnector() {
  val c = MaterialTheme.colorScheme.outlineVariant
  Canvas(Modifier.fillMaxWidth().height(14.dp)) {
    val x = size.width / 2f
    drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
  }
}

/** Trunk line splits into the two colored gospel rails. */
@Composable
private fun ForkConnector() {
  val stem = MaterialTheme.colorScheme.outlineVariant
  val mt = MaterialTheme.colorScheme.primary
  val lk = MaterialTheme.colorScheme.tertiary
  Canvas(Modifier.fillMaxWidth().height(22.dp)) {
    val cx = size.width / 2f
    val lX = size.width * 0.25f
    val rX = size.width * 0.75f
    val mid = size.height / 2f
    drawLine(stem, Offset(cx, 0f), Offset(cx, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(mt, Offset(cx, mid), Offset(lX, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(mt, Offset(lX, mid), Offset(lX, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(lk, Offset(cx, mid), Offset(rX, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(lk, Offset(rX, mid), Offset(rX, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
  }
}

/** Two gospel rails converge back into a single line feeding the shared node. */
@Composable
private fun MergeConnector() {
  val stem = MaterialTheme.colorScheme.outlineVariant
  val mt = MaterialTheme.colorScheme.primary
  val lk = MaterialTheme.colorScheme.tertiary
  Canvas(Modifier.fillMaxWidth().height(22.dp)) {
    val cx = size.width / 2f
    val lX = size.width * 0.25f
    val rX = size.width * 0.75f
    val mid = size.height / 2f
    drawLine(mt, Offset(lX, 0f), Offset(lX, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(lk, Offset(rX, 0f), Offset(rX, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(mt, Offset(lX, mid), Offset(cx, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(lk, Offset(rX, mid), Offset(cx, mid), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(stem, Offset(cx, mid), Offset(cx, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
  }
}

@Composable
private fun ForkHeader() {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        ScriptureRefs.localizeRef("Matthew"),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        stringResource(Res.string.genealogy_line_royal),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        ScriptureRefs.localizeRef("Luke"),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold
      )
      Text(
        stringResource(Res.string.genealogy_line_priestly),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary
      )
    }
  }
}

@Composable
private fun CenterCard(node: GeneNode, prefs: PrefsState) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    PersonCard(node, prefs, accent = null, modifier = Modifier.widthIn(max = 260.dp))
  }
}

/** One gospel column: its own true length, with a colored rail behind the cards. */
@Composable
private fun ForkColumns(mt: List<GeneNode>, lk: List<GeneNode>, prefs: PrefsState) {
  val mtColor = MaterialTheme.colorScheme.primary
  val lkColor = MaterialTheme.colorScheme.tertiary
  Row(
    Modifier.fillMaxWidth().height(IntrinsicSize.Max),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    GospelColumn(mt, mtColor, prefs, Modifier.weight(1f).fillMaxHeight())
    GospelColumn(lk, lkColor, prefs, Modifier.weight(1f).fillMaxHeight())
  }
}

@Composable
private fun GospelColumn(nodes: List<GeneNode>, accent: Color, prefs: PrefsState, modifier: Modifier) {
  Box(modifier) {
    val rail = accent.copy(alpha = 0.5f)
    // Full-height rail: the shorter column simply draws its line down to the
    // merge point instead of padding with empty placeholder cards.
    Canvas(Modifier.matchParentSize()) {
      val x = size.width / 2f
      drawLine(rail, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      nodes.forEach { PersonCard(it, prefs, accent = accent) }
    }
  }
}

@Composable
private fun PersonCard(
  node: GeneNode,
  prefs: PrefsState,
  accent: Color? = null,
  modifier: Modifier = Modifier
) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val internalNav = LocalInternalNavigate.current
  val shape = RoundedCornerShape(12.dp)
  val wifeColor = MaterialTheme.colorScheme.secondary
  Card(
    modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .then(if (accent != null) Modifier.border(1.5.dp, accent.copy(alpha = 0.55f), shape) else Modifier)
      .clickable {
        navigateToRef(node.navRef ?: node.refs.firstOrNull(), prefs, ctx, scope, internalNav)
      },
    shape = shape
  ) {
    Column(
      Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        node.name,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
      node.wife?.let { w ->
        Text(
          "& $w",
          style = MaterialTheme.typography.labelMedium,
          color = wifeColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().then(
            if (node.wifeRef != null) {
              Modifier.clickable {
                navigateToRef(node.wifeRef, prefs, ctx, scope, internalNav)
              }
            } else Modifier
          )
        )
      }
      Text(
        node.refs.firstOrNull()?.let { ScriptureRefs.localizeRef(it) } ?: "—",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

/** Shared node where both gospels meet; each gospel reference is its own tap target. */
@Composable
private fun ConvergeCard(node: ConvergeNode, prefs: PrefsState) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val internalNav = LocalInternalNavigate.current
  val shape = RoundedCornerShape(12.dp)
  val accent = MaterialTheme.colorScheme.secondary
  val mtColor = MaterialTheme.colorScheme.primary
  val lkColor = MaterialTheme.colorScheme.tertiary
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Card(
      Modifier
        .fillMaxWidth()
        .border(1.5.dp, accent, shape)
        .clickable {
          navigateToRef(node.matthewRef, prefs, ctx, scope, internalNav)
        },
      shape = shape
    ) {
      Column(
        Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          node.name,
          style = MaterialTheme.typography.titleSmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
        Text(
          ScriptureRefs.localizeRef(node.matthewRef),
          style = MaterialTheme.typography.labelSmall,
          color = mtColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().clickable {
            navigateToRef(node.matthewRef, prefs, ctx, scope, internalNav)
          }
        )
        Text(
          ScriptureRefs.localizeRef(node.lukeRef),
          style = MaterialTheme.typography.labelSmall,
          color = lkColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().clickable {
            navigateToRef(node.lukeRef, prefs, ctx, scope, internalNav)
          }
        )
      }
    }
  }
}
