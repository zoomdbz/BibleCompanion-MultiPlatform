package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotesMarkdownTest {

  @Test
  fun stripsStandaloneAndInlineHtmlCommentsWithoutRemovingContent() {
    val source = """
      Intro.
      <!-- ECLIPSE_CATALOG_2024_2033_BEGIN -->
      ### Complete Eclipse Catalog, 2024-2033
      | 2024 | Total <!-- eclipse:2024-04-08:solar-total -->; Annular |
      <!-- ECLIPSE_CATALOG_2024_2033_END -->
      Outro.
    """.trimIndent()

    val prepared = stripMarkdownHtmlComments(source)

    assertFalse("<!--" in prepared)
    assertTrue("### Complete Eclipse Catalog, 2024-2033" in prepared)
    assertTrue("| 2024 | Total ; Annular |" in prepared)
    assertTrue("Intro." in prepared)
    assertTrue("Outro." in prepared)
  }

  @Test
  fun preservesCommentLiteralsInsideInlineAndFencedCode() {
    val source = """
      Keep `<!-- inline literal -->` here.
      ```html
      <!-- fenced literal -->
      ```
          <!-- indented literal -->
      Remove <!-- audit marker --> this.
    """.trimIndent()

    val prepared = stripMarkdownHtmlComments(source)

    assertTrue("`<!-- inline literal -->`" in prepared)
    assertTrue("<!-- fenced literal -->" in prepared)
    assertTrue("<!-- indented literal -->" in prepared)
    assertFalse("audit marker" in prepared)
    assertTrue("Remove  this." in prepared)
  }

  @Test
  fun preservesLineBreaksAcrossMultilineCommentsAndFollowingHeadings() {
    val source = "before\n<!-- audit\nmetadata -->\n## Heading\nafter"

    assertEquals("before\n\n\n## Heading\nafter", stripMarkdownHtmlComments(source))
  }

  @Test
  fun plainTextExportAlsoOmitsHtmlComments() {
    val plain = markdownToPlainText("Text <!-- hidden --> [NASA](https://example.test).")

    assertEquals("Text  NASA (https://example.test).", plain)
  }
}
