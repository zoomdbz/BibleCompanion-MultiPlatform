"""Regression checks for reviewed 5.0.0 translation omissions and ambiguity.

These approved phrases guard specific defects; they do not substitute for a
human-language review or imply that word counts prove translation accuracy.
"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "shared/assets/notes"

# Every conclusion must retain all of the English distinctions: two penumbral
# eclipses, intervening eclipses, the American-path/Israel date difference,
# four consecutive Purim observances, two blood moons, and the solar storm.
CONCLUSION_FACTS = {
    "ja": (
        "2回は半影月食", "その間にほかの日食や月食", "米州の経路",
        "ロシュ・ハシャナー前日", "イスラエル", "すでにラッパの祭り",
        "4年連続", "2回は血の月", "太陽嵐",
    ),
    "ko": (
        "두 번은 반영월식", "그 사이에 다른 일식과 월식", "아메리카 대륙의 경로",
        "로시 하샤나 전날", "이스라엘", "이미 나팔절",
        "4년 연속", "두 번은 핏빛 달", "태양 폭풍",
    ),
    "zh-Hans": (
        "两次是半影月食", "期间还穿插着其他日月食", "美洲路径",
        "罗什哈沙那前夕", "以色列", "已经进入吹角节",
        "连续四年", "两次是血月", "太阳风暴",
    ),
    "zh-Hant": (
        "兩次是半影月食", "期間還穿插著其他日月食", "美洲路徑",
        "羅什·哈沙納前夕", "以色列", "已經進入吹角節",
        "連續四年", "兩次是血月", "太陽風暴",
    ),
}


def note(locale, filename="astronomical_signs.md"):
    return (NOTES / locale / filename).read_text(encoding="utf-8-sig")


class LocalizationReleaseFixes(unittest.TestCase):
    def test_eastern_conclusions_retain_every_reviewed_english_fact(self):
        for locale, phrases in CONCLUSION_FACTS.items():
            conclusion = note(locale).rsplit("\n### ", 1)[1]
            for phrase in phrases:
                with self.subTest(locale=locale, fact=phrase):
                    self.assertIn(phrase, conclusion)

    def test_chinese_opening_references_have_one_parenthetical_pair(self):
        for locale, correct, broken in (
            ("zh-Hans", "（参见使徒行传2:20；启示录6:12）", "（参（"),
            ("zh-Hant", "（參見使徒行傳2:20；啟示錄6:12）", "（參（"),
        ):
            with self.subTest(locale=locale):
                text = note(locale)
                self.assertEqual(1, text.count(correct))
                self.assertNotIn(broken, text)

    def test_arabic_and_russian_approved_grammar_replaces_the_defects(self):
        fixes = {
            "ar": (
                ("ويحدهما خسوف شبه ظلي في شوشان بوريم 5784 وبوريم قطان 5787",
                 "ويحدهما خسوفان شبه ظليان، أحدهما في شوشان بوريم 5784 والآخر في بوريم قطان 5787"),
                ("أول أربعة خسوفات قمرية", "أول خسوف من أربعة خسوفات قمرية"),
                ("فاثنان منها شبه ظليين", "فاثنان منها شبه ظليان"),
            ),
            "ru": (
                ("Ирода из Матфея 2", "Ирод из Матфея 2"),
                ("пара осенней кровавой луны", "в паре с осенней кровавой луной"),
                ("Соединения Юпитера и Венеры, это класс событий",
                 "Соединения Юпитера и Венеры относятся к событиям"),
                ("Эти совпадения, документированный факт",
                 "Эти совпадения документально подтверждены"),
            ),
        }
        for locale, pairs in fixes.items():
            text = note(locale)
            for old, new in pairs:
                with self.subTest(locale=locale, correction=new):
                    self.assertNotIn(old, text)
                    self.assertEqual(1, text.count(new))

    def test_rapture_introductions_mean_gathering_believers_not_book_collection(self):
        for locale, correct, ambiguous in (
            ("de", "dieselbe in der Schrift beschriebene Sammlung der Gläubigen", "dieselbe biblische Sammlung"),
            ("it", "lo stesso raduno dei credenti descritto nella Scrittura", "stessa raccolta biblica"),
        ):
            with self.subTest(locale=locale):
                text = note(locale, "revelation_timeline.md")
                self.assertEqual(1, text.count(correct))
                self.assertNotIn(ambiguous, text)


if __name__ == "__main__":
    unittest.main()
