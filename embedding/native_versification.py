"""Canonical-to-native chapter/verse mapping for embedding metadata.

These Russian verse rules mirror the Scripture migration's old_to_new mapping.
The Kotlin chapter-only mapping lives in NativeVersification.kt.
Verse-specific rows must use this mapping instead
of a chapter-only lookup because Psalms 116 and 147 split in the NRT.
"""

from __future__ import annotations

DELTA_ONE = {
    3, 4, 5, 6, 7, 8, 9, 11, 17, 18, 19, 20, 21, 29, 30, 33, 35, 37,
    38, 39, 40, 41, 43, 44, 45, 46, 47, 48, 52, 54, 55, 56, 57, 58, 60,
    61, 62, 63, 64, 66, 67, 68, 69, 74, 75, 76, 79, 80, 82, 83, 84, 87,
    88, 91, 101, 107, 139,
}
DELTA_TWO = {50, 51, 53, 59}
DELTA = {number: 1 for number in DELTA_ONE} | {number: 2 for number in DELTA_TWO}


def russian_psalm_verse(old_chapter: int, old_verse: int) -> tuple[int, int]:
    """Map an old English-numbered Psalm verse to NRT chapter and verse."""
    if not 1 <= old_chapter <= 150 or old_verse < 1:
        raise ValueError((old_chapter, old_verse))
    if old_chapter <= 8:
        return old_chapter, old_verse + DELTA.get(old_chapter, 0)
    if old_chapter == 9:
        return 9, old_verse + DELTA[9]
    if old_chapter == 10:
        return 9, old_verse + DELTA[9] + 20
    if old_chapter <= 113:
        new_chapter = old_chapter - 1
        return new_chapter, old_verse + DELTA.get(new_chapter, 0)
    if old_chapter == 114:
        return 113, old_verse
    if old_chapter == 115:
        return 113, old_verse + 8
    if old_chapter == 116:
        return (114, old_verse) if old_verse <= 9 else (115, old_verse - 9)
    if old_chapter <= 146:
        new_chapter = old_chapter - 1
        return new_chapter, old_verse + DELTA.get(new_chapter, 0)
    if old_chapter == 147:
        return (146, old_verse) if old_verse <= 11 else (147, old_verse - 11)
    return old_chapter, old_verse


def russian_psalm_chapters(old_chapter: int) -> tuple[int, ...]:
    """Map canonical chapter-wide content; two chapters need two targets."""
    if not 1 <= old_chapter <= 150:
        raise ValueError(old_chapter)
    if old_chapter in (116, 147):
        return (114, 115) if old_chapter == 116 else (146, 147)
    return (russian_psalm_verse(old_chapter, 1)[0],)


def native_anchor_ids(story_id: str, lang: str) -> tuple[str, ...]:
    if lang == "ru" and story_id.startswith("psalms-"):
        chapter = int(story_id.rsplit("-", 1)[1])
        return tuple(f"psalms-{n}" for n in russian_psalm_chapters(chapter))
    if lang in ("de", "fr") and story_id == "malachi-4":
        return ("malachi-3",)
    return (story_id,)
