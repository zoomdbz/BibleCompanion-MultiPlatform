package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DivineNameRenderingTest {

  private data class PhraseCase(
    val lang: String,
    val source: String,
    val yahweh: String
  )

  private val angelOfTheLordCases = listOf(
    PhraseCase("en", "the angel of the Lord spoke", "the angel of [DN]Yahweh[/DN] spoke"),
    PhraseCase("es", "un ángel del Señor habló", "un ángel de [DN]Yahvé[/DN] habló"),
    PhraseCase("fr", "L’ange du Seigneur parla", "L’ange de [DN]Yahvé[/DN] parla"),
    PhraseCase("de", "der Engel des HERRN sprach", "der Engel [DN]Jahwe[/DN]s sprach"),
    PhraseCase("it", "L'angelo del Signore parlò", "L'angelo di [DN]Yahweh[/DN] parlò"),
    PhraseCase("pt", "o anjo do Senhor falou", "o anjo de [DN]Javé[/DN] falou"),
    PhraseCase("ru", "Ангел Господень сказал", "Ангел [DN]Яхве[/DN] сказал"),
    PhraseCase("ar", "ملاك الرَّبِّ تكلّم", "ملاك [DN]يهوه[/DN] تكلّم"),
    PhraseCase("hi", "प्रभु के एक दूत ने कहा", "[DN]याहवे[/DN] के एक दूत ने कहा"),
    PhraseCase("ja", "主の使いが語った", "[DN]ヤハウェ[/DN]の使いが語った"),
    PhraseCase("ko", "주님의 천사가 말했다", "[DN]야훼[/DN]의 천사가 말했다"),
    PhraseCase("zh-Hans", "上主的天使说", "[DN]雅威[/DN]的天使说"),
    PhraseCase("zh-Hant", "上主的天使說", "[DN]雅威[/DN]的天使說")
  )

  @Test
  fun angelOfTheLordUsesLocalizedNameInEveryLanguage() {
    for (case in angelOfTheLordCases) {
      assertEquals(
        case.yahweh,
        applyDivineName(case.source, "yahweh", case.lang, true, "deuterocanonical"),
        case.lang
      )
    }
  }

  @Test
  fun traditionalModeColorsPhraseWithoutChangingItsWords() {
    for (case in angelOfTheLordCases) {
      val rendered = applyDivineName(
        case.source,
        "traditional",
        case.lang,
        true,
        "deuterocanonical"
      )
      assertEquals(case.source, stripScriptureInlineTags(rendered), case.lang)
      assertEquals(1, Regex("\\[DN]", RegexOption.IGNORE_CASE).findAll(rendered).count(), case.lang)
    }
  }

  @Test
  fun yahwehModeUsesTheSameLocalizedFormsAdvertisedBySettings() {
    val expected = mapOf(
      "en" to "Yahweh",
      "es" to "Yahvé",
      "fr" to "Yahvé",
      "de" to "Jahwe",
      "it" to "Yahweh",
      "pt" to "Javé",
      "ru" to "Яхве",
      "ar" to "يهوه",
      "hi" to "याहवे",
      "ja" to "ヤハウェ",
      "ko" to "야훼",
      "zh-Hans" to "雅威",
      "zh-Hant" to "雅威"
    )
    for ((lang, name) in expected) {
      assertEquals(
        "[DN]$name[/DN]",
        applyDivineName("YHWH", "yahweh", lang, true, "old_testament"),
        lang
      )
    }
  }

  @Test
  fun consonantalModesHonorLocalizedYhwhAndUniversalYhvhLabels() {
    assertEquals("[DN]ЙХВХ[/DN]", applyDivineName("YHWH", "yhwh", "ru", true))
    assertEquals("[DN]يهوه[/DN]", applyDivineName("YHWH", "yhwh", "ar", true))
    assertEquals("[DN]YHWH[/DN]", applyDivineName("Yahweh", "yhwh", "de", true))
    assertEquals("[DN]YHVH[/DN]", applyDivineName("Yahweh", "yhvh", "ar", true))
  }

  @Test
  fun newTestamentLordTitlesAreNotRewrittenAsTheTetragrammaton() {
    val english = "the angel of the Lord Jesus spoke; an UNKNOWN GOD"
    val italian = "L'angelo del Signore Gesù parlò"
    val russian = "Ангел Господень явился"
    assertEquals(english, applyDivineName(english, "yahweh", "en", true, "new_testament"))
    assertEquals(italian, applyDivineName(italian, "yahweh", "it", true, "new_testament"))
    assertEquals(russian, applyDivineName(russian, "yahweh", "ru", true, "new_testament"))
  }

  @Test
  fun cjkFalsePositivesStayUntouched() {
    val japanese = "持ち主と救い主"
    val korean = "보여 주는 사람과 거주 지역"
    assertEquals(japanese, applyDivineName(japanese, "yahweh", "ja", true, "old_testament"))
    assertEquals(korean, applyDivineName(korean, "yahweh", "ko", true, "old_testament"))
  }

  @Test
  fun kjvDivineNameFormsAreCovered() {
    val source = "The LORD is JEHOVAH; praise JAH; Lord GOD reigns; GOD the Lord speaks."
    val expected = "[DN]Yahweh[/DN] is [DN]Yahweh[/DN]; praise [DN]Yahweh[/DN]; " +
      "Lord [DN]Yahweh[/DN] reigns; [DN]Yahweh[/DN] the Lord speaks."
    assertEquals(expected, applyDivineName(source, "yahweh", "en", true, "old_testament"))
  }

  @Test
  fun traditionalColoringPreservesKjvJehovahAndJahWording() {
    val source = "The LORD is JEHOVAH; praise ye the LORD, and praise JAH."
    val rendered = applyDivineName(source, "traditional", "en", true, "old_testament")

    assertEquals(source, stripScriptureInlineTags(rendered))
    assertTrue(rendered.contains("[DN]JEHOVAH[/DN]"))
    assertTrue(rendered.contains("[DN]JAH[/DN]"))
  }

  @Test
  fun colorTaggingIsIdempotentWithoutNormalizingScripture() {
    assertEquals(
      "ＹＨＷＨ",
      applyDivineName("ＹＨＷＨ", "yhwh", "en", true, "old_testament")
    )

    val first = applyDivineName(
      "[J]the angel of the Lord[/J] and [DN]YHWH[/DN]",
      "yahweh",
      "en",
      true,
      "deuterocanonical"
    )
    assertEquals(
      "[J]the angel of [DN]Yahweh[/DN][/J] and [DN]Yahweh[/DN]",
      first
    )
    assertEquals(
      first,
      applyDivineName(first, "yahweh", "en", true, "deuterocanonical")
    )
    assertFalse(first.contains("[DN][DN]"))
  }

  @Test
  fun sourceMarkedDivineNamesKeepPublisherTextInTraditionalMode() {
    val source = "der Engel des [DN]Herrn[/DN] sprach"
    val colored = applyDivineName(source, "traditional", "de", true, "old_testament")
    val uncolored = applyDivineName(source, "traditional", "de", false, "old_testament")

    assertEquals(source, colored)
    assertEquals("der Engel des Herrn sprach", stripScriptureInlineTags(colored))
    assertEquals("der Engel des Herrn sprach", uncolored)
  }

  @Test
  fun sourceMarkedDivineNamesStillHonorReplacementModeAndGrammar() {
    val german = applyDivineName(
      "der Engel des [DN]Herrn[/DN] sprach",
      "yahweh",
      "de",
      true,
      "old_testament"
    )
    assertEquals("der Engel [DN]Jahwe[/DN]s sprach", german)
    assertEquals(german, applyDivineName(german, "yahweh", "de", true, "old_testament"))

    assertEquals(
      "el ángel de Yahvé habló",
      applyDivineName(
        "el ángel del [DN]Señor[/DN] habló",
        "yahweh",
        "es",
        false,
        "old_testament"
      )
    )

    val russian = applyDivineName(
      "Ангел [DN]Господень[/DN] сказал",
      "yahweh",
      "ru",
      true,
      "old_testament"
    )
    assertEquals("Ангел [DN]Яхве[/DN] сказал", russian)
    assertEquals(russian, applyDivineName(russian, "yahweh", "ru", true, "old_testament"))

    assertEquals(
      "The [DN]Yahweh[/DN] spoke [ADD]new words[/ADD]",
      applyDivineName(
        "The [DN]LORD spoke[/DN] [ADD]new words[/ADD]",
        "yahweh",
        "en",
        true,
        "old_testament"
      )
    )
    assertEquals(
      "[DN]Yahweh[/DN] spoke",
      applyDivineName("[DN]The LORD[/DN] spoke", "yahweh", "en", true, "old_testament")
    )
    assertEquals(
      "[DN]Yahweh[/DN] spoke",
      applyDivineName("The LORD spoke", "yahweh", "en", true, "old_testament")
    )
    assertEquals(
      "[DN]Yahweh[/DN] spoke",
      applyDivineName("[DN]LORD GOD[/DN] spoke", "yahweh", "en", true, "old_testament")
    )
    assertEquals(
      "[DN]Yahweh[/DN] spoke",
      applyDivineName("[DN]GOD the LORD[/DN] spoke", "yahweh", "en", true, "old_testament")
    )
  }

  @Test
  fun publisherMarkedGottesAndDieuStayNarrowAndKeepTheirGrammar() {
    val germanSource =
      "vor dem Angesicht [DN]Gottes[/DN], des Herrn!"
    assertEquals(
      germanSource,
      applyDivineName(germanSource, "traditional", "de", true, "old_testament")
    )
    assertEquals(
      "vor dem Angesicht Gottes, des Herrn!",
      applyDivineName(germanSource, "traditional", "de", false, "old_testament")
    )
    val germanReplacement = applyDivineName(
      germanSource,
      "yahweh",
      "de",
      true,
      "old_testament"
    )
    assertEquals("vor dem Angesicht [DN]Jahwe[/DN]s, des Herrn!", germanReplacement)
    assertEquals(
      germanReplacement,
      applyDivineName(germanReplacement, "yahweh", "de", true, "old_testament")
    )
    assertEquals(
      "vor dem Angesicht Jahwes, des Herrn!",
      applyDivineName(germanSource, "yahweh", "de", false, "old_testament")
    )
    assertEquals(
      "vor dem Angesicht Gottes, des Herrn!",
      applyDivineName(
        "vor dem Angesicht Gottes, des Herrn!",
        "yahweh",
        "de",
        true,
        "old_testament"
      )
    )

    val frenchSource = "Seigneur [DN]Dieu[/DN], que me donneras-tu ?"
    assertEquals(
      frenchSource,
      applyDivineName(frenchSource, "traditional", "fr", true, "old_testament")
    )
    assertEquals(
      "Seigneur Dieu, que me donneras-tu ?",
      applyDivineName(frenchSource, "traditional", "fr", false, "old_testament")
    )
    val frenchReplacement = applyDivineName(
      frenchSource,
      "yahweh",
      "fr",
      true,
      "old_testament"
    )
    assertEquals("Seigneur [DN]Yahvé[/DN], que me donneras-tu ?", frenchReplacement)
    assertEquals(
      frenchReplacement,
      applyDivineName(frenchReplacement, "yahweh", "fr", true, "old_testament")
    )
    assertEquals(
      "Seigneur Dieu, que me donneras-tu ?",
      applyDivineName(
        "Seigneur Dieu, que me donneras-tu ?",
        "yahweh",
        "fr",
        true,
        "old_testament"
      )
    )
  }

  @Test
  fun publisherMarkedGermanGottUsesOnlyItsSourceBoundary() {
    val source = "Und [DN]Gott[/DN] sprach zu ihnen."
    assertEquals(
      source,
      applyDivineName(source, "traditional", "de", true, "old_testament")
    )
    assertEquals(
      "Und Gott sprach zu ihnen.",
      applyDivineName(source, "traditional", "de", false, "old_testament")
    )

    for ((mode, name) in listOf("yahweh" to "Jahwe", "yhwh" to "YHWH", "yhvh" to "YHVH")) {
      val replacement = applyDivineName(source, mode, "de", true, "old_testament")
      assertEquals("Und [DN]$name[/DN] sprach zu ihnen.", replacement, mode)
      assertEquals(
        replacement,
        applyDivineName(replacement, mode, "de", true, "old_testament"),
        mode
      )
    }
    assertEquals(
      "Und Gott sprach zu ihnen.",
      applyDivineName(
        "Und Gott sprach zu ihnen.",
        "yahweh",
        "de",
        true,
        "old_testament"
      )
    )
  }

  @Test
  fun reviewedArabicPublisherFormsRequireTheirExactDnBoundary() {
    val reviewedForms = listOf(
      "اللهِ", "اللهُ", "اللهَ", "الْمَوْلَى", "لّٰهِ", "لْمَوْلَى"
    )
    for (form in reviewedForms) {
      val marked = "قبل [DN]$form[/DN] بعد"
      assertEquals(
        marked,
        applyDivineName(marked, "traditional", "ar", true, "old_testament"),
        form
      )
      assertEquals(
        "قبل $form بعد",
        applyDivineName(marked, "traditional", "ar", false, "old_testament"),
        form
      )
      val replacement = "قبل [DN]يهوه[/DN] بعد"
      assertEquals(
        replacement,
        applyDivineName(marked, "yahweh", "ar", true, "old_testament"),
        form
      )
      assertEquals(
        replacement,
        applyDivineName(replacement, "yahweh", "ar", true, "old_testament"),
        form
      )
      assertEquals(
        "قبل $form بعد",
        applyDivineName("قبل $form بعد", "yahweh", "ar", true, "old_testament"),
        "untagged $form"
      )
    }
  }

  @Test
  fun nviExodusSelfIdentificationKeepsPublisherWordsAndHonorsColor() {
    val source = "[DN]Yo soy el que soy[/DN] ... [DN]Yo soy[/DN] me ha enviado"
    val plain = "Yo soy el que soy ... Yo soy me ha enviado"
    for (mode in listOf("traditional", "yahweh", "yhwh", "yhvh")) {
      assertEquals(source, applyDivineName(source, mode, "es", true, "old_testament"), mode)
      assertEquals(plain, applyDivineName(source, mode, "es", false, "old_testament"), mode)
    }
    assertEquals(
      plain,
      applyDivineName(plain, "yahweh", "es", true, "old_testament")
    )
    assertEquals(
      "[DN]Yo soy el que soy[/DN] y [DN]Yahvé[/DN]",
      applyDivineName(
        "[DN]Yo soy el que soy[/DN] y [DN]Señor[/DN]",
        "yahweh",
        "es",
        true,
        "old_testament"
      )
    )
  }

  @Test
  fun addAndSemanticTagsAreRecognizedAndStrippedWithoutRemovingContent() {
    val tagged = "[J]I [ADD]am[/ADD] [DN]YHWH[/DN][/J]"
    assertEquals("I am YHWH", stripScriptureInlineTags(tagged))
    assertEquals(5, scriptureInlineTagEnd("[ADD]word", 0, opening = true, tag = "ADD"))
    assertEquals(8, scriptureInlineTagEnd("[/ add ]word", 0, opening = false, tag = "ADD"))
    assertEquals(-1, scriptureInlineTagEnd("[ADDRESS]", 0, opening = true, tag = "ADD"))
    assertTrue(stripScriptureInlineTags("[OTHER]word[/OTHER]").contains("[OTHER]"))
  }
}
