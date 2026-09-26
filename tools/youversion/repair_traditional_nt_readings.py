#!/usr/bin/env python3
"""Repair the 16 traditional NT verse addresses without inventing Scripture.

This is intentionally narrow. It restores exact imported source text for
editions that publish the readings in their body, and moves omitted readings
to one version-labelled manuscript variant for editions that do not.
"""

from __future__ import annotations

import json
import difflib
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BOOK_ROOT = ROOT / "shared/assets/books/new_testament"

REFS = {
    "matthew": ((17, 21), (18, 11), (23, 14)),
    "mark": ((7, 16), (9, 44), (9, 46), (11, 26), (15, 28)),
    "luke": ((17, 36), (23, 17)),
    "john": ((5, 4),),
    "acts": ((8, 37), (15, 34), (24, 7), (28, 29)),
    "romans": ((16, 24),),
}

BOOK_LABEL = {
    "matthew": "Matthew",
    "mark": "Mark",
    "luke": "Luke",
    "john": "John",
    "acts": "Acts",
    "romans": "Romans",
}

BODY_SOURCES = {
    "ar": "2fbb46d6",
    "de": "7d47636b",
    "hi": "2fbb46d6",
    "it": "7d47636b",
    "pt": "7d47636b",
    "ru": "7d47636b",
    "zh-Hans": "7d47636b",
}

SPANISH_RVR1960 = {
    ("matthew", 17, 21): "Pero este género no sale sino con oración y ayuno.",
    ("matthew", 18, 11): "Porque el Hijo del Hombre ha venido para salvar lo que se había perdido.",
    ("matthew", 23, 14): "¡Ay de vosotros, escribas y fariseos, hipócritas! porque devoráis las casas de las viudas, y como pretexto hacéis largas oraciones; por esto recibiréis mayor condenación.",
    ("mark", 7, 16): "Si alguno tiene oídos para oír, oiga.",
    ("mark", 9, 44): "donde el gusano de ellos no muere, y el fuego nunca se apaga.",
    ("mark", 9, 46): "donde el gusano de ellos no muere, y el fuego nunca se apaga.",
    ("mark", 11, 26): "Porque si vosotros no perdonáis, tampoco vuestro Padre que está en los cielos os perdonará vuestras ofensas.",
    ("mark", 15, 28): "Y se cumplió la Escritura que dice: Y fue contado con los inicuos.",
    ("luke", 17, 36): "Dos estarán en el campo; el uno será tomado, y el otro dejado.",
    ("luke", 23, 17): "Y tenía necesidad de soltarles uno en cada fiesta.",
    ("john", 5, 4): "Porque un ángel descendía de tiempo en tiempo al estanque, y agitaba el agua; y el que primero descendía al estanque después del movimiento del agua, quedaba sano de cualquier enfermedad que tuviese.",
    ("acts", 8, 37): "Felipe dijo: Si crees de todo corazón, bien puedes. Y respondiendo, dijo: Creo que Jesucristo es el Hijo de Dios.",
    ("acts", 15, 34): "Mas a Silas le pareció bien el quedarse allí.",
    ("acts", 24, 7): "Pero interviniendo el tribuno Lisias, con gran violencia le quitó de nuestras manos,",
    ("acts", 28, 29): "Y cuando hubo dicho esto, los judíos se fueron, teniendo gran discusión entre sí.",
    ("romans", 16, 24): "La gracia de nuestro Señor Jesucristo sea con todos vosotros. Amén.",
}

FRENCH_LSG = {
    ("matthew", 17, 21): "Mais cette sorte de démon ne sort que par la prière et par le jeûne.",
    ("matthew", 18, 11): "Car le Fils de l'homme est venu sauver ce qui était perdu.",
    ("matthew", 23, 14): "Malheur à vous, scribes et pharisiens hypocrites! parce que vous dévorez les maisons des veuves, et que vous faites pour l'apparence de longues prières; à cause de cela, vous serez jugés plus sévèrement.",
    ("mark", 7, 16): "Si quelqu'un a des oreilles pour entendre, qu'il entende.",
    # LSG uses 44 and 46 for the continuation of the preceding warnings.
    ("mark", 9, 44): "que d'avoir les deux mains et d'aller dans la géhenne, dans le feu qui ne s'éteint point.",
    ("mark", 9, 46): "que d'avoir les deux pieds et d'être jeté dans la géhenne, dans le feu qui ne s'éteint point.",
    ("mark", 11, 26): "Mais si vous ne pardonnez pas, votre Père qui est dans les cieux ne vous pardonnera pas non plus vos offenses.",
    ("mark", 15, 28): "Ainsi fut accompli ce que dit l'Écriture: Il a été mis au nombre des malfaiteurs.",
    ("luke", 17, 36): "De deux hommes qui seront dans un champ, l'un sera pris et l'autre laissé.",
    ("luke", 23, 17): "A chaque fête, il était obligé de leur relâcher un prisonnier.",
    ("john", 5, 4): "car un ange descendait de temps en temps dans la piscine, et agitait l'eau; et celui qui y descendait le premier après que l'eau avait été agitée était guéri, quelle que fût sa maladie.",
    ("acts", 8, 37): "Philippe dit: Si tu crois de tout ton coeur, cela est possible. L'eunuque répondit: Je crois que Jésus Christ est le Fils de Dieu.",
    ("acts", 15, 34): "Toutefois Silas trouva bon de rester.",
    ("acts", 24, 7): "mais le tribun Lysias étant survenu, l'a arraché de nos mains avec une grande violence,",
    ("acts", 28, 29): "Lorsqu'il eut dit cela, les Juifs s'en allèrent, discutant vivement entre eux.",
    ("romans", 16, 24): "Que la grâce de notre Seigneur Jésus Christ soit avec vous tous! Amen!",
}

TRAILING_REF = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)\.$")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def load_git_json(commit: str, relative: Path) -> dict:
    raw = subprocess.check_output(
        ["git", "show", f"{commit}:{relative.as_posix()}"], cwd=ROOT
    )
    return json.loads(raw.decode("utf-8"))


def story(data: dict, book: str, chapter: int) -> dict:
    target = f"{book}-{chapter}"
    matches = [item for item in data["stories"] if item.get("id") == target]
    if len(matches) != 1:
        raise RuntimeError(f"expected one story {target}, got {len(matches)}")
    return matches[0]


def coverage(bullet: str) -> tuple[int, int, int] | None:
    match = TRAILING_REF.search(bullet)
    if match is None:
        return None
    chapter, start, end = map(int, (match.group(1), match.group(2), match.group(3) or match.group(2)))
    return chapter, start, end


def bullet_index(chapter_story: dict, chapter: int, verse: int) -> int | None:
    hits = []
    for index, bullet in enumerate(chapter_story.get("summaryBullets", [])):
        span = coverage(bullet)
        if span is not None and span[0] == chapter and span[1] <= verse <= span[2]:
            hits.append(index)
    if len(hits) > 1:
        raise RuntimeError(f"overlapping coverage at {chapter}:{verse}")
    return hits[0] if hits else None


def preserve_outer_tags(current: str, source: str) -> str:
    current_text = TRAILING_REF.sub("", current).strip()
    source_match = TRAILING_REF.search(source)
    if source_match is None:
        raise RuntimeError(f"source bullet lacks trailing reference: {source!r}")
    source_text = source[: source_match.start()].rstrip()
    suffix = source[source_match.start():]
    if current_text.startswith("[J]") and current_text.endswith("[/J]"):
        source_text = re.sub(r"^\[J\]|\[/J\]$", "", source_text)
        source_text = f"[J]{source_text}[/J]"
    return f"{source_text} {suffix}"


def variant_matches(value: dict, chapter: int, verse: int) -> bool:
    ref = str(value.get("ref", ""))
    for match in re.finditer(r"(?<!\d)(\d+):(\d+)(?:-(\d+))?(?!\d)", ref):
        candidate_chapter = int(match.group(1))
        start = int(match.group(2))
        end = int(match.group(3) or match.group(2))
        if candidate_chapter == chapter and start <= verse <= end:
            return True
    return False


def clear_variants(chapter_story: dict, chapter: int, verse: int) -> None:
    values = chapter_story.get("manuscriptVariants", [])
    kept = [value for value in values if not variant_matches(value, chapter, verse)]
    if kept:
        chapter_story["manuscriptVariants"] = kept
    else:
        chapter_story.pop("manuscriptVariants", None)


def add_variant(chapter_story: dict, ref: str, text: str) -> None:
    chapter_story.setdefault("manuscriptVariants", []).append({"ref": ref, "text": text})


def restore_body_language(language: str, commit: str) -> set[Path]:
    touched: set[Path] = set()
    for book, refs in REFS.items():
        relative = Path(f"shared/assets/books/new_testament/{language}/{book}.json")
        path = ROOT / relative
        data = load_json(path)
        source = load_git_json(commit, relative)
        changed = False
        for chapter, verse in refs:
            current_story = story(data, book, chapter)
            clear_variants(current_story, chapter, verse)
            if language == "zh-Hans" and book == "mark" and chapter == 9 and verse in (44, 46):
                changed = True
                continue
            source_story = story(source, book, chapter)
            current_index = bullet_index(current_story, chapter, verse)
            source_index = bullet_index(source_story, chapter, verse)
            if current_index is None or source_index is None:
                raise RuntimeError(f"{language} {book} {chapter}:{verse}: missing body source")
            current_story["summaryBullets"][current_index] = preserve_outer_tags(
                current_story["summaryBullets"][current_index],
                source_story["summaryBullets"][source_index],
            )
            changed = True
        if language == "zh-Hans" and book == "mark":
            mark9 = story(data, "mark", 9)
            bullets = mark9["summaryBullets"]
            indexes = [bullet_index(mark9, 9, verse) for verse in (43, 44, 45, 46)]
            if indexes[0] == indexes[1] and indexes[2] == indexes[3] and indexes[0] != indexes[2]:
                changed = True
            elif any(index is None for index in indexes) or len(set(indexes)) != 4:
                raise RuntimeError(f"zh-Hans mark 9 expected four standalone 43-46 bullets, got {indexes}")
            else:
                insertion = min(indexes)
                for index in sorted(indexes, reverse=True):
                    del bullets[index]
                bullets[insertion:insertion] = [
                    "[J]如果你一只手使你犯罪，就砍掉它！残废着进入永生，胜过双手健全却落入地狱不灭的火中。[/J] (9:43-44).",
                    "[J]如果你一只脚使你犯罪，就砍掉它！瘸着腿进入永生，胜过双脚健全却被扔进地狱。[/J] (9:45-46).",
                ]
                changed = True
        if changed:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            touched.add(path)
    return touched


def move_to_variants(language: str, source_name: str, texts: dict[tuple[str, int, int], str] | None) -> set[Path]:
    touched: set[Path] = set()
    factual = {
        "ko": "선택된 RNKSV 본문에는 이 절이 없으며, 일부 사본과 번역본에는 이 절이 포함되어 있습니다.",
        "zh-Hant": "目前選用的《和合本修訂版》正文沒有這一節；部分抄本和譯本收錄此節。",
    }
    for book, refs in REFS.items():
        path = BOOK_ROOT / language / f"{book}.json"
        data = load_json(path)
        for chapter, verse in refs:
            chapter_story = story(data, book, chapter)
            index = bullet_index(chapter_story, chapter, verse)
            if index is not None:
                span = coverage(chapter_story["summaryBullets"][index])
                if span != (chapter, verse, verse):
                    raise RuntimeError(
                        f"{language} {book} {chapter}:{verse}: refusing to remove bridged source line {span}"
                    )
                del chapter_story["summaryBullets"][index]
            clear_variants(chapter_story, chapter, verse)
            label = f"{source_name} {BOOK_LABEL[book]} {chapter}:{verse}" if source_name else f"{BOOK_LABEL[book]} {chapter}:{verse}"
            text = texts[(book, chapter, verse)] if texts is not None else factual[language]
            add_variant(chapter_story, label, text)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        touched.add(path)
    return touched


def repair_japanese() -> set[Path]:
    touched: set[Path] = set()
    notes = {
        ("mark", 11, 26): "〔もしゆるさないならば、天にいますあなたがたの父も、あなたがたのあやまちを、ゆるしてくださらないであろう〕」。",
        ("acts", 15, 34): "〔しかし、シラスだけは、引きつづきとどまることにした。〕",
    }
    for book, refs in REFS.items():
        path = BOOK_ROOT / "ja" / f"{book}.json"
        data = load_json(path)
        for chapter, verse in refs:
            chapter_story = story(data, book, chapter)
            if bullet_index(chapter_story, chapter, verse) is None:
                raise RuntimeError(f"ja {book} {chapter}:{verse}: repaired JCB body coverage missing")
            clear_variants(chapter_story, chapter, verse)
            key = (book, chapter, verse)
            if key in notes:
                add_variant(chapter_story, f"JA1955 {BOOK_LABEL[book]} {chapter}:{verse}", notes[key])
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        touched.add(path)
    return touched


def render_file(language: str, book: str) -> str:
    """Render one transformed file to stdout-friendly text without writing it."""
    path = BOOK_ROOT / language / f"{book}.json"
    data = load_json(path)
    if language in BODY_SOURCES or language in {"ru", "zh-Hans"}:
        commit = {"ar": "2fbb46d6", "hi": "2fbb46d6"}.get(language, "7d47636b")
        relative = path.relative_to(ROOT)
        source = load_git_json(commit, relative)
        for chapter, verse in REFS[book]:
            current_story = story(data, book, chapter)
            clear_variants(current_story, chapter, verse)
            if language == "zh-Hans" and book == "mark" and chapter == 9 and verse in (44, 46):
                continue
            source_story = story(source, book, chapter)
            current_index = bullet_index(current_story, chapter, verse)
            source_index = bullet_index(source_story, chapter, verse)
            if current_index is None or source_index is None:
                raise RuntimeError(f"{language} {book} {chapter}:{verse}: missing body source")
            current_story["summaryBullets"][current_index] = preserve_outer_tags(
                current_story["summaryBullets"][current_index],
                source_story["summaryBullets"][source_index],
            )
        if language == "zh-Hans" and book == "mark":
            mark9 = story(data, "mark", 9)
            bullets = mark9["summaryBullets"]
            indexes = [bullet_index(mark9, 9, verse) for verse in (43, 44, 45, 46)]
            if indexes[0] == indexes[1] and indexes[2] == indexes[3] and indexes[0] != indexes[2]:
                pass
            elif any(index is None for index in indexes) or len(set(indexes)) != 4:
                raise RuntimeError(f"zh-Hans mark 9 expected four standalone 43-46 bullets, got {indexes}")
            else:
                insertion = min(indexes)
                for index in sorted(indexes, reverse=True):
                    del bullets[index]
                bullets[insertion:insertion] = [
                    "[J]如果你一只手使你犯罪，就砍掉它！残废着进入永生，胜过双手健全却落入地狱不灭的火中。[/J] (9:43-44).",
                    "[J]如果你一只脚使你犯罪，就砍掉它！瘸着腿进入永生，胜过双脚健全却被扔进地狱。[/J] (9:45-46).",
                ]
    elif language in {"es", "fr"}:
        texts = SPANISH_RVR1960 if language == "es" else FRENCH_LSG
        source_name = "RVR1960" if language == "es" else "LSG"
        for chapter, verse in REFS[book]:
            chapter_story = story(data, book, chapter)
            index = bullet_index(chapter_story, chapter, verse)
            if index is not None:
                span = coverage(chapter_story["summaryBullets"][index])
                if span != (chapter, verse, verse):
                    raise RuntimeError(f"{language} {book} {chapter}:{verse}: bridged source line {span}")
                del chapter_story["summaryBullets"][index]
            clear_variants(chapter_story, chapter, verse)
            add_variant(
                chapter_story,
                f"{source_name} {BOOK_LABEL[book]} {chapter}:{verse}",
                texts[(book, chapter, verse)],
            )
    elif language == "ja":
        notes = {
            ("mark", 11, 26): "〔もしゆるさないならば、天にいますあなたがたの父も、あなたがたのあやまちを、ゆるしてくださらないであろう〕」。",
            ("acts", 15, 34): "〔しかし、シラスだけは、引きつづきとどまることにした。〕",
        }
        for chapter, verse in REFS[book]:
            chapter_story = story(data, book, chapter)
            if bullet_index(chapter_story, chapter, verse) is None:
                raise RuntimeError(f"ja {book} {chapter}:{verse}: repaired JCB body coverage missing")
            clear_variants(chapter_story, chapter, verse)
            key = (book, chapter, verse)
            if key in notes:
                add_variant(chapter_story, f"JA1955 {BOOK_LABEL[book]} {chapter}:{verse}", notes[key])
    elif language in {"ko", "zh-Hant"}:
        publisher_notes = {
            "ko": {
                ("matthew", 17, 21): "그러나 이런 종류는 기도와 금식을 하지 않고는 나가지 않는다",
                ("matthew", 18, 11): "인자는 잃은 사람을 구원하러 왔다",
                ("matthew", 23, 14): "이 위선자인 율법학자들과 바리새 파 사람들아! 너희에게 화가 있다! 너희는 과부의 집을 삼키고 남에게 보이려고 길게 기도한다. 그러므로 너희는 무서운 심판을 받을 것이다",
                ("mark", 7, 16): "들을 귀가 있는 사람들은 들어라",
                ("mark", 9, 44): "지옥에서는 ‘그들을 파먹는 구더기들도 죽지 않고, 불도 꺼지지 않는다.’",
                ("mark", 9, 46): "지옥에서는 ‘그들을 파먹는 구더기들도 죽지 않고, 불도 꺼지지 않는다.’",
                ("mark", 11, 26): "만일 너희가 용서해 주지 않으면 하늘에 계신 너희의 아버지께서도 너희의 잘못을 용서해 주지 않으실 것이다",
                ("mark", 15, 28): "그리하여 ‘그는 범법자들 가운데 한 사람으로 여김을 받았다’고 한 성경 말씀이 이루어졌다",
                ("luke", 17, 36): "또 두 사람이 밭에 있을 터이나 하나는 데려가고 하나는 버려 둘 것이다",
                ("luke", 23, 17): "명절이 되어 빌라도는 죄수 한 사람을 그들에게 놓아주어야 했다",
                ("acts", 8, 37): "빌립이 말하였다. ‘그대가 마음을 다하여 믿으면, 세례를 받을 수 있습니다.’ 그 때에 내시가 대답하였다. ‘나는 예수 그리스도가 하나님의 아들이심을 믿습니다.’",
                ("acts", 15, 34): "그러나 실라는 그들과 함께 머무르려고 하였다",
                ("acts", 24, 7): "그래서 우리의 율법대로 재판하려고 했지만, 7. 천부장 루시아가 와서 그를 우리 손에서 강제로 빼앗아 갔습니다. 8. 그리고는 그를 고발하는 사람들에게 총독님께 가라고 명령하였습니다",
                ("acts", 28, 29): "그가 이 말을 마쳤을 때에, 유대 사람들은 서로 많은 토론을 하면서 돌아갔다",
                ("romans", 16, 24): "우리 주 예수 그리스도의 은혜가 여러분 모두와 함께 있기를 빕니다. 아멘",
            },
            "zh-Hant": {
                ("matthew", 17, 21): "至於這一類的鬼，若不禱告禁食，他就不出來。",
                ("matthew", 18, 11): "人子來，為要拯救失喪的人。",
                ("matthew", 23, 14): "你們這假冒為善的文士和法利賽人有禍了！因為你們侵吞寡婦的家產，假意作很長的禱告，所以要受更重的懲罰。",
                ("mark", 7, 16): "有耳可聽的，就應當聽！",
                ("mark", 9, 44): "在那裏，蟲是不死的，火是不滅的。",
                ("mark", 9, 46): "在那裏，蟲是不死的，火是不滅的。",
                ("mark", 11, 26): "你們若不饒恕人，你們在天上的父也不饒恕你們的過犯。",
                ("mark", 15, 28): "這就應驗了經上的話說：他被列在罪犯之中。",
                ("luke", 17, 36): "兩個人在田裏，一個被接去，一個被撇下。",
                ("luke", 23, 17): "每逢這節期，總督必須釋放一個囚犯給他們。",
                ("john", 5, 4): "等候水動，4 因為有天使按時下池子攪動那水，水動之後，先下水的人無論患甚麼病都能得痊癒。",
                ("acts", 8, 37): "腓利說：『你若是一心相信，就可以。』他回答：『我信耶穌基督是上帝的兒子。』",
                ("acts", 15, 34): "惟有西拉決定仍住在那裏。",
                ("acts", 24, 7): "要按我們的律法審問，7 可是，呂西亞千夫長前來，甚是強橫，從我們手中把他奪去，下令告他的人到你這裏來。",
                ("acts", 28, 29): "保羅說了這些話，猶太人議論紛紛地走了。",
                ("romans", 16, 24): "願我們的主耶穌基督賜恩典給你們各位！阿們！",
            },
        }
        for chapter, verse in REFS[book]:
            chapter_story = story(data, book, chapter)
            key = (book, chapter, verse)
            if language == "ko" and key == ("mark", 7, 16):
                index = bullet_index(chapter_story, chapter, verse)
                if index is None:
                    raise RuntimeError("ko mark 7:16 body line missing")
                chapter_story["summaryBullets"][index] = "[J]사람에게서 나오는 것이 그 사람을 더럽힌다.[/J] (7:16)."
                clear_variants(chapter_story, chapter, verse)
                add_variant(chapter_story, "RNKSV Mark 7:16", publisher_notes[language][key])
                continue
            if language == "ko" and key == ("john", 5, 4):
                indexes = [bullet_index(chapter_story, 5, candidate) for candidate in (3, 4)]
                desired = "이 주랑 안에는 많은 환자들, 곧 눈먼 사람들과 다리 저는 사람들과 중풍병자들이 누워 있었다. [[그들은 물이 움직이기를 기다리고 있었다. 주님의 천사가 때때로 못에 내려와 물을 휘저어 놓는데 물이 움직인 뒤에 맨 먼저 들어가는 사람은 무슨 병에 걸렸든지 나았기 때문이다.]] (5:3-4)."
                if indexes[0] == indexes[1] and indexes[0] is not None:
                    chapter_story["summaryBullets"][indexes[0]] = desired
                elif any(index is None for index in indexes) or len(set(indexes)) != 2:
                    raise RuntimeError(f"ko john 5 expected standalone 3 and 4, got {indexes}")
                else:
                    insertion = min(indexes)
                    for index in sorted(indexes, reverse=True):
                        del chapter_story["summaryBullets"][index]
                    chapter_story["summaryBullets"].insert(insertion, desired)
                clear_variants(chapter_story, chapter, verse)
                continue
            index = bullet_index(chapter_story, chapter, verse)
            if index is not None:
                span = coverage(chapter_story["summaryBullets"][index])
                if span != (chapter, verse, verse):
                    raise RuntimeError(f"{language} {book} {chapter}:{verse}: bridged source line {span}")
                del chapter_story["summaryBullets"][index]
            clear_variants(chapter_story, chapter, verse)
            source_name = "RNKSV" if language == "ko" else "RCUV"
            native_ref = f"{chapter}:{verse}"
            if key == ("acts", 24, 7):
                native_ref = "24:6-8"
            elif language == "zh-Hant" and key == ("john", 5, 4):
                native_ref = "5:3-4"
            add_variant(
                chapter_story,
                f"{source_name} {BOOK_LABEL[book]} {native_ref}",
                publisher_notes[language][key],
            )
    else:
        raise RuntimeError(f"unsupported patch language {language}")
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def emit_patch(language: str, book: str) -> None:
    path = BOOK_ROOT / language / f"{book}.json"
    old = path.read_text(encoding="utf-8")
    new = render_file(language, book)
    relative = path.relative_to(ROOT).as_posix()
    diff = list(difflib.unified_diff(old.splitlines(), new.splitlines(), lineterm=""))
    print("*** Begin Patch")
    print(f"*** Update File: {relative}")
    for line in diff[2:]:
        print("@@" if line.startswith("@@") else line)
    print("*** End Patch")


def main() -> None:
    touched: set[Path] = set()
    for language in (
        "ar", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant"
    ):
        for book in REFS:
            path = BOOK_ROOT / language / f"{book}.json"
            rendered = render_file(language, book)
            if path.read_text(encoding="utf-8") != rendered:
                path.write_text(rendered, encoding="utf-8")
                touched.add(path)
    for path in sorted(touched):
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "--emit-patch":
        emit_patch(sys.argv[2], sys.argv[3])
    else:
        main()
