package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.platformOpenUrl
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

object GenealogyData {
  val trunk: List<GeneNode> = listOf(
    GeneNode("Adam", listOf("Genesis 5:1\u20135", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Seth", listOf("Genesis 5:3\u20138", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Enosh", listOf("Genesis 5:6\u201311", "Luke 3:38", "1 Chronicles 1:1")),
    GeneNode("Kenan", listOf("Genesis 5:9\u201314", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Mahalalel", listOf("Genesis 5:12\u201317", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Jared", listOf("Genesis 5:15\u201320", "Luke 3:37", "1 Chronicles 1:2")),
    GeneNode("Enoch", listOf("Genesis 5:21\u201324", "Luke 3:37", "1 Chronicles 1:3")),
    GeneNode("Methuselah", listOf("Genesis 5:25\u201327", "Luke 3:37", "1 Chronicles 1:3")),
    GeneNode("Lamech", listOf("Genesis 5:28\u201331", "Luke 3:36", "1 Chronicles 1:3")),
    GeneNode("Noah", listOf("Genesis 5:32", "Genesis 6:9\u201310", "Luke 3:36", "1 Chronicles 1:4")),
    GeneNode("Shem", listOf("Genesis 10:21\u201332", "Genesis 11:10\u201311", "Luke 3:36", "1 Chronicles 1:4")),
    GeneNode("Arphaxad", listOf("Genesis 11:12\u201313", "Luke 3:36", "1 Chronicles 1:17")),
    GeneNode("Shelah", listOf("Genesis 11:14\u201315", "Luke 3:35", "1 Chronicles 1:18")),
    GeneNode("Eber", listOf("Genesis 10:24\u201325", "Genesis 11:16\u201317", "Luke 3:35", "1 Chronicles 1:18\u201319")),
    GeneNode("Peleg", listOf("Genesis 10:25", "Genesis 11:18\u201319", "Luke 3:35", "1 Chronicles 1:19")),
    GeneNode("Reu", listOf("Genesis 11:20\u201321", "Luke 3:35", "1 Chronicles 1:25")),
    GeneNode("Serug", listOf("Genesis 11:22\u201323", "Luke 3:35", "1 Chronicles 1:26")),
    GeneNode("Nahor", listOf("Genesis 11:22\u201325", "Luke 3:34", "1 Chronicles 1:26")),
    GeneNode("Terah", listOf("Genesis 11:24\u201332", "Luke 3:34", "1 Chronicles 1:26\u201327")),
    GeneNode("Abraham", listOf("Genesis 11:26\u201332", "Genesis 12:1\u20133", "Luke 3:34", "1 Chronicles 1:27")),
    GeneNode("Isaac", listOf("Genesis 21:1\u20137", "Genesis 25:19\u201326", "Luke 3:34", "1 Chronicles 1:34")),
    GeneNode("Jacob", listOf("Genesis 25:24\u201326", "Genesis 35:9\u201312", "Luke 3:34", "1 Chronicles 1:34")),
    GeneNode("Judah", listOf("Genesis 29:35", "Genesis 49:8\u201310", "Luke 3:33", "1 Chronicles 2:3")),
    GeneNode("Perez", listOf("Genesis 38:27\u201330", "Ruth 4:18", "Luke 3:33", "1 Chronicles 2:4\u20135")),
    GeneNode("Hezron", listOf("Ruth 4:18", "Luke 3:33", "1 Chronicles 2:5,9")),
    GeneNode("Ram (Aram)", listOf("Ruth 4:19", "Matthew 1:3\u20134", "Luke 3:33", "1 Chronicles 2:9\u201310")),
    GeneNode("Amminadab", listOf("Ruth 4:19\u201320", "Matthew 1:4", "Luke 3:33", "1 Chronicles 2:10")),
    GeneNode("Nahshon", listOf("Ruth 4:20", "Matthew 1:4", "Luke 3:32", "1 Chronicles 2:10\u201312")),
    GeneNode("Salmon", listOf("Ruth 4:20\u201321", "Matthew 1:4\u20135", "1 Chronicles 2:11\u201312")),
    GeneNode("Boaz", listOf("Ruth 4:21", "Matthew 1:5", "1 Chronicles 2:11\u201312")),
    GeneNode("Obed", listOf("Ruth 4:21", "Matthew 1:5", "1 Chronicles 2:12")),
    GeneNode("Jesse", listOf("Ruth 4:22", "Matthew 1:5\u20136", "1 Chronicles 2:12\u201313")),
    GeneNode("David", listOf("Ruth 4:22", "1 Samuel 16", "Matthew 1:6", "Luke 3:31", "1 Chronicles 2:13\u201315"))
  )

  val matthew: List<GeneNode> = listOf(
    GeneNode("David", listOf("Matthew 1:6", "1 Chronicles 3:1\u20139")),
    GeneNode("Solomon", listOf("Matthew 1:6\u20137", "1 Chronicles 3:10")),
    GeneNode("Rehoboam", listOf("Matthew 1:7", "1 Chronicles 3:10")),
    GeneNode("Abijah", listOf("Matthew 1:7", "1 Chronicles 3:10")),
    GeneNode("Asa", listOf("Matthew 1:7\u20138", "1 Chronicles 3:10")),
    GeneNode("Jehoshaphat", listOf("Matthew 1:8", "1 Chronicles 3:10")),
    GeneNode("Joram (Jehoram)", listOf("Matthew 1:8", "1 Chronicles 3:11")),
    GeneNode("Uzziah (Azariah)", listOf("Matthew 1:8\u20139", "1 Chronicles 3:12")),
    GeneNode("Jotham", listOf("Matthew 1:9", "1 Chronicles 3:12")),
    GeneNode("Ahaz", listOf("Matthew 1:9", "1 Chronicles 3:13")),
    GeneNode("Hezekiah", listOf("Matthew 1:9\u201310", "1 Chronicles 3:13")),
    GeneNode("Manasseh", listOf("Matthew 1:10", "1 Chronicles 3:13")),
    GeneNode("Amon", listOf("Matthew 1:10", "1 Chronicles 3:14")),
    GeneNode("Josiah", listOf("Matthew 1:10\u201311", "1 Chronicles 3:14\u201315")),
    GeneNode("Jeconiah (Jehoiachin)", listOf("Matthew 1:11\u201312", "1 Chronicles 3:16")),
    GeneNode("Shealtiel", listOf("Matthew 1:12", "1 Chronicles 3:17")),
    GeneNode("Zerubbabel", listOf("Matthew 1:12\u201313", "1 Chronicles 3:19")),
    GeneNode("Abiud", listOf("Matthew 1:13")),
    GeneNode("Eliakim", listOf("Matthew 1:13")),
    GeneNode("Azor", listOf("Matthew 1:13\u201314")),
    GeneNode("Zadok", listOf("Matthew 1:14")),
    GeneNode("Achim", listOf("Matthew 1:14")),
    GeneNode("Eliud", listOf("Matthew 1:14\u201315")),
    GeneNode("Eleazar", listOf("Matthew 1:15")),
    GeneNode("Matthan", listOf("Matthew 1:15")),
    GeneNode("Jacob", listOf("Matthew 1:15\u201316")),
    GeneNode("Joseph (husband of Mary)", listOf("Matthew 1:16")),
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
    GeneNode("Levi", listOf("Luke 3:24, 29")),
    GeneNode("Matthat", listOf("Luke 3:24, 29")),
    GeneNode("Jorim", listOf("Luke 3:29")),
    GeneNode("Eliezer", listOf("Luke 3:29")),
    GeneNode("Joshua", listOf("Luke 3:29")),
    GeneNode("Er", listOf("Luke 3:28")),
    GeneNode("Elmadam", listOf("Luke 3:28")),
    GeneNode("Cosam", listOf("Luke 3:28")),
    GeneNode("Addi", listOf("Luke 3:28")),
    GeneNode("Melchi", listOf("Luke 3:24, 28")),
    GeneNode("Neri", listOf("Luke 3:27")),
    GeneNode("Shealtiel", listOf("Luke 3:27")),
    GeneNode("Zerubbabel", listOf("Luke 3:27")),
    GeneNode("Rhesa", listOf("Luke 3:27")),
    GeneNode("Joanan", listOf("Luke 3:27")),
    GeneNode("Joda", listOf("Luke 3:26")),
    GeneNode("Josech", listOf("Luke 3:26")),
    GeneNode("Semein (Semei)", listOf("Luke 3:26")),
    GeneNode("Mattathias (1)", listOf("Luke 3:25")),
    GeneNode("Maath", listOf("Luke 3:26")),
    GeneNode("Naggai", listOf("Luke 3:25")),
    GeneNode("Esli", listOf("Luke 3:25")),
    GeneNode("Nahum", listOf("Luke 3:25")),
    GeneNode("Amos", listOf("Luke 3:25")),
    GeneNode("Mattathias (2)", listOf("Luke 3:25, 26")),
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

object GenealogyRows {
  private fun sameName(a: String, b: String): Boolean =
    a.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^a-z0-9]"), "") ==
            b.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^a-z0-9]"), "")

  private fun canonicalName(s: String) = s.replace(Regex("\\(.*?\\)"), "").trim()

  private fun mergeNodes(m: GeneNode, l: GeneNode): GeneNode {
    val name = canonicalName(m.name)
    val head = listOfNotNull(m.refs.firstOrNull(), l.refs.firstOrNull()).joinToString(" \u2022 ")
    val rest = (m.refs.drop(1) + l.refs.drop(1))
    return GeneNode(name, listOf(head) + rest)
  }

  private fun indexOfName(list: List<GeneNode>, start: Int, target: String): Int {
    for (i in start until list.size) if (sameName(list[i].name, target)) return i
    return -1
  }

  private fun addAlignedRows(
    rows: MutableList<GeneRow>,
    leftSeg: List<GeneNode>,
    rightSeg: List<GeneNode>
  ) {
    val max = maxOf(leftSeg.size, rightSeg.size)
    for (k in 0 until max) {
      rows += GeneRow(
        left = leftSeg.getOrNull(k),
        right = rightSeg.getOrNull(k)
      )
    }
  }

  fun buildAnchored(
    trunk: List<GeneNode>,
    matthewFull: List<GeneNode>,
    lukeFull: List<GeneNode>
  ): List<GeneRow> {
    val rows = mutableListOf<GeneRow>()
    trunk.forEach { rows += GeneRow(center = it) }

    val matthew = matthewFull.drop(1)
    val luke = lukeFull.drop(1)

    var mi = 0; var li = 0
    val mZ = indexOfName(matthew, mi, "Zerubbabel")
    val lZ = indexOfName(luke, li, "Zerubbabel")

    val mSeg1 = if (mZ >= 0) matthew.subList(mi, mZ) else matthew.subList(mi, matthew.size)
    val lSeg1 = if (lZ >= 0) luke.subList(li, lZ) else luke.subList(li, luke.size)
    addAlignedRows(rows, mSeg1, lSeg1)

    if (mZ >= 0 && lZ >= 0) {
      rows += GeneRow(center = mergeNodes(matthew[mZ], luke[lZ]))
      mi = mZ + 1; li = lZ + 1
    } else {
      addAlignedRows(rows, matthew.subList(mi, matthew.size), luke.subList(li, luke.size))
      return rows
    }

    val mJ = indexOfName(matthew, mi, "Jesus")
    val lJ = indexOfName(luke, li, "Jesus")

    val mSeg2 = if (mJ >= 0) matthew.subList(mi, mJ) else matthew.subList(mi, matthew.size)
    val lSeg2 = if (lJ >= 0) luke.subList(li, lJ) else luke.subList(li, luke.size)
    addAlignedRows(rows, mSeg2, lSeg2)

    if (mJ >= 0 && lJ >= 0) {
      rows += GeneRow(center = mergeNodes(matthew[mJ], luke[lJ]))
    }
    return rows
  }
}

private fun languageFolder(tag: String): String =
  LocaleUtils.effectiveAssetTag(tag)

private fun detectCollectionFromRef(ref: String): String =
  if (
    ref.startsWith("Matt", true) || ref.startsWith("Luke", true) ||
    ref.startsWith("Mark", true) || ref.startsWith("John", true)
  ) "new_testament" else "old_testament"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GenealogyScreen(prefs: PrefsState, onBack: () -> Unit) {
  val ctx = LocalPlatformContext.current

  val rows = remember {
    GenealogyRows.buildAnchored(
      GenealogyData.trunk,
      GenealogyData.matthew,
      GenealogyData.luke
    )
  }
  val trunkCount = GenealogyData.trunk.size
  val trunkRows = remember { rows.take(trunkCount) }
  val branchRows = remember { rows.drop(trunkCount) }

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

      // Trunk rows with single-vertical connectors
      items(trunkRows) { row ->
        ConnectorLine(ConnectorType.Vertical)
        row.center?.let { CenterRow(it, prefs) }
      }

      // Fork label — Y splits down from trunk into two tails above the labels
      item {
        ConnectorLine(ConnectorType.ForkDown)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
          Text(
            "Matthew",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            "Luke",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }
        Spacer(Modifier.height(4.dp))
      }

      // Branch rows — connector style depends on what's above each row.
      // The fork label acts like a fork row for connector purposes.
      itemsIndexed(branchRows) { idx, row ->
        val prev = if (idx == 0) null else branchRows[idx - 1]
        val prevIsFork = prev?.center == null // null = fork-label (above first branch row); otherwise check previous row
        val currIsFork = row.center == null
        val connector = when {
          currIsFork && prevIsFork -> ConnectorType.TwoVerticals   // fork → fork
          currIsFork && !prevIsFork -> ConnectorType.ForkDown      // center → fork (split)
          !currIsFork && prevIsFork -> ConnectorType.MergeUp       // fork → center (merge)
          else -> ConnectorType.Vertical                           // center → center
        }
        ConnectorLine(connector)
        if (row.center != null) CenterRow(row.center, prefs) else ForkRow(row, prefs)
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }
}

private enum class ConnectorType { Vertical, TwoVerticals, ForkDown, MergeUp }

/** Draws the connecting line(s) between genealogy rows. */
@Composable
private fun ConnectorLine(type: ConnectorType) {
  val lineColor = MaterialTheme.colorScheme.outlineVariant
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(16.dp)
  ) {
    val centerX = size.width / 2f
    val leftX = size.width * 0.25f
    val rightX = size.width * 0.75f
    val midY = size.height / 2f
    when (type) {
      ConnectorType.Vertical -> drawLine(
        lineColor,
        Offset(centerX, 0f),
        Offset(centerX, size.height),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
      )
      ConnectorType.TwoVerticals -> {
        drawLine(lineColor, Offset(leftX, 0f), Offset(leftX, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(rightX, 0f), Offset(rightX, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
      }
      ConnectorType.ForkDown -> {
        // Trunk center splits into two tails heading to 25% / 75%.
        drawLine(lineColor, Offset(centerX, 0f), Offset(centerX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(centerX, midY), Offset(leftX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(centerX, midY), Offset(rightX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(leftX, midY), Offset(leftX, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(rightX, midY), Offset(rightX, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
      }
      ConnectorType.MergeUp -> {
        // Two branch lines converge back into one trunk line heading down.
        drawLine(lineColor, Offset(leftX, 0f), Offset(leftX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(rightX, 0f), Offset(rightX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(leftX, midY), Offset(centerX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(rightX, midY), Offset(centerX, midY), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(lineColor, Offset(centerX, midY), Offset(centerX, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
      }
    }
  }
}

@Composable
private fun CenterRow(node: GeneNode, prefs: PrefsState) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    PersonCard(node, prefs)
  }
}

@Composable
private fun ForkRow(row: GeneRow, prefs: PrefsState) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top
  ) {
    Box(Modifier.weight(1f)) {
      if (row.left != null) PersonCard(row.left, prefs) else Spacer(Modifier.height(64.dp))
    }
    Box(Modifier.weight(1f)) {
      if (row.right != null) PersonCard(row.right, prefs) else Spacer(Modifier.height(64.dp))
    }
  }
}

@Composable
private fun PersonCard(node: GeneNode, prefs: PrefsState) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val internalNav = LocalInternalNavigate.current
  Card(
    Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .clickable {
        node.refs.firstOrNull()?.let { ref ->
          if (prefs.readerMode == "internal") {
            val col = detectCollectionFromRef(ref)
            val bookName = ScriptureRefs.canonBookOfRef(ref) ?: return@let
            val bookId = bookName.trim().lowercase().replace(' ', '_')
            val chap = ref.substringAfter(bookName).trim().split(":").firstOrNull()
              ?.split("-")?.firstOrNull()?.trim()?.filter { it.isDigit() } ?: "1"
            internalNav(col, bookId, "$bookId-$chap", null, null)
          } else {
            scope.launch(Dispatchers.Default) {
              val url = Linker.toLink(
                collection = detectCollectionFromRef(ref),
                ref = ref,
                translation = prefs.translation,
                preferBibleCom = prefs.readerMode == "biblecom"
              )
              if (url.isNotBlank()) {
                withContext(Dispatchers.Main) {
                  runCatching { platformOpenUrl(ctx, url) }
                }
              }
            }
          }
        }
      },
    shape = RoundedCornerShape(12.dp)
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
        node.refs.firstOrNull() ?: "\u2014",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
