#!/usr/bin/env python3
"""Build reviewed Korean KRV Jesus-word boundaries for KJV-wj mixed units.

The pinned KJV overlay decides whose words receive J. Korean reporting verbs
locate boundaries in the exact KRV text; dialogue/translation recasts below
are explicit reviewed exceptions. The builder validates all 626 rows in memory
and never rewrites an existing ledger.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

from import_traditional_editions import BOOKS
from jesus_word_spans import ReviewedJesusSpans, sha256_text
from propose_jesus_word_spans import build_queue

ROOT = Path(__file__).resolve().parents[2]
OPEN = re.compile(
    "가라사대|이르시되|말씀하시되|물으시되|외치시되|소리지르시되|"
    "대답하시되|경계하시되|이르시기를"
)
CLOSE = re.compile(
    r" 하시니라| 하시니| 하시고| 하시더라| 하시매| 하시거늘|"
    r" 하신대| 하셨으나| 하시기로| 하시므로| 하셨더니|"
    r" 하신지라| 하시다가| 하시며| 하신즉| 하시다| 하시되|"
    r" 하셨음이라| 하셨느니라| 하셨으니"
)
NARRATOR = re.compile(r" 예수께서 ")
BOOK_ORDER = {book: index for index, (_, _, book) in enumerate(BOOKS)}

# Every case without a safe formula boundary is pinned to exact KRV words.
# None means no direct wording in the same native unit. Matthew 20:32 moves
# to 20:33; all other None entries are genuine target omissions.
MANUAL: dict[str, tuple[list[str] | None, str]] = {
    "matthew:8:32": (["가라"], "Short command before KRV narrator 하시니."),
    "matthew:9:6": (["그러나 인자가 세상에서 죄를 사하는 권세가 있는 줄을 너희로 알게 하려 하노라", "일어나 네 침상을 가지고 집으로 가라"], "Two Jesus turns split by KRV reporting formula."),
    "matthew:9:28": (["내가 능히 이 일 할 줄을 믿느냐"], "Blind men's answer begins at 대답하되."),
    "matthew:13:51": (["이 모든 것을 깨달았느냐"], "Jesus question precedes 하시니 and disciples' reply."),
    "matthew:14:29": (["오라"], "Short command before 하시니."),
    "matthew:15:34": (["너희에게 떡이 몇개나 있느냐"], "The disciples answer after 가로되."),
    "matthew:16:4": (["악하고 음란한 세대가 표적을 구하나 요나의 표적 밖에는 보여 줄 표적이 없느니라"], "Narration resumes at 하시고."),
    "matthew:17:23": (["죽임을 당하고 제 삼일에 살아나리라"], "Narration resumes at 하시니."),
    "matthew:20:21": (["무엇을 원하느뇨"], "The woman's answer follows 가로되."),
    "matthew:20:22": (["너희 구하는 것을 너희가 알지 못하는도다 나의 마시려는 잔을 너희가 마실 수 있느냐"], "The disciples answer begins at 저희가 말하되."),
    "matthew:20:32": (None, "KJV-wj question moves to KRV Matthew 20:33 before the blind men's reply."),
    "matthew:21:25": (["요한의 세례가 어디로서 왔느냐 하늘로서냐 사람에게로서냐"], "Jesus' question ends before the council's deliberation."),
    "matthew:21:31": (["그 둘 중에 누가 아비의 뜻대로 하였느뇨", "내가 진실로 너희에게 이르노니 세리들과 창기들이 너희보다 먼저 하나님의 나라에 들어가리라"], "Two Jesus turns surround the hearers' answer."),
    "matthew:22:19": (["셋돈을 내게 보이라"], "The penny is brought after 하시니."),
    "matthew:22:42": (["너희는 그리스도에 대하여 어떻게 생각하느냐 뉘 자손이냐"], "The hearers answer at 대답하되."),
    "matthew:23:2": (["서기관들과 바리새인들이 모세의 자리에 앉았으니"], "KRV omits KJV's reporting formula; this whole native unit is speech."),
    "matthew:26:56": (["그러나 이렇게 된 것은 다 선지자들의 글을 이루려 함이니라"], "Disciples' flight follows 하시더라."),
    "matthew:26:18": (["성안 아무에게 가서 이르되 선생님 말씀이 내 때가 가까왔으니 내 제자들과 함께 유월절을 네 집에서 지키겠다 하시더라 하라"], "The inner 하시더라 belongs to Jesus' instruction; narration resumes at final 하신대."),
    "matthew:28:20": (["내가 너희에게 분부한 모든 것을 가르쳐 지키게 하라 볼찌어다 내가 세상 끝날까지 너희와 항상 함께 있으리라"], "KRV reporting formula follows the final commission."),
    "mark:2:10": (["그러나 인자가 땅에서 죄를 사하는 권세가 있는 줄을 너희로 알게 하려하노라"], "Jesus speech precedes his address to the paralytic."),
    "mark:5:9": (["네 이름이 무엇이냐"], "The demoniac's answer begins at 가로되."),
    "mark:8:5": (["너희에게 떡 몇 개나 있느냐"], "The disciples' answer begins at 가로되."),
    "mark:8:19": (["내가 떡 다섯 개를 오천 명에게 떼어 줄 때에 조각 몇 바구니를 거두었더냐"], "The disciples' answer begins at 가로되."),
    "mark:8:20": (["또 일곱 개를 사천 명에게 떼어 줄 때에 조각 몇 광주리를 거두었더냐"], "The disciples' answer begins at 가로되."),
    "mark:8:29": (["너희는 나를 누구라 하느냐"], "Peter's answer begins at 베드로가."),
    "mark:9:31": (None, "KRV recasts the KJV direct speech as indirect narration ending 말씀하시는 연고더라."),
    "mark:10:33": (["보라 우리가 예루살렘에 올라가노니 인자가 대제사장들과 서기관들에게 넘기우매 저희가 죽이기로 결안하고 이방인들에게 넘겨주겠고"], "KRV omits KJV's saying formula; the whole native unit is speech."),
    "mark:10:51": (["네게 무엇을 하여주기를 원하느냐"], "Blind man's reply starts at 소경이 가로되."),
    "mark:12:16": (["이 화상과 이 글이 뉘 것이냐"], "The hearers' answer begins at 가로되."),
    "mark:14:72": (["닭이 두번 울기 전에 네가 세번 나를 부인하리라"], "KJV-wj marks Jesus' remembered prior words within Peter's narration."),
    "luke:5:24": (["그러나 인자가 땅에서 죄를 사하는 권세가 있는 줄을 너희로 알게 하리라", "내가 네게 이르노니 일어나 네 침상을 가지고 집으로 가라"], "Two Jesus turns split by KRV reporting formula."),
    "luke:5:27": (["나를 좇으라"], "Short command before 하시니."),
    "luke:7:13": (["울지 말라"], "Short command before 하시고."),
    "luke:8:8": (["더러는 좋은 땅에 떨어지매 나서 백배의 결실을 하였느니라", "들을 귀 있는 자는 들을찌어다"], "Two Jesus turns split by KRV narrator and 외치시되."),
    "luke:8:30": (["네 이름이 무엇이냐"], "KRV places 물으신즉 after Jesus' question."),
    "luke:8:39": (["집으로 돌아가 하나님이 네게 어떻게 큰 일 행하신 것을 일일이 고하라"], "Man's later proclamation is narration after 하시니."),
    "luke:8:45": (["내게 손을 댄 자가 누구냐"], "KRV has the first KJV-wj question only; it omits Peter's later verbatim citation."),
    "luke:9:20": (["너희는 나를 누구라 하느냐"], "Peter's answer begins at 베드로가."),
    "luke:9:55": (None, "KRV says Jesus rebuked them but omits the KJV rebuke words."),
    "luke:9:56": (None, "KRV omits KJV's Son-of-man saying; only travel narration remains."),
    "luke:9:59": (["나를 좇으라"], "The other person's reply follows 하시니."),
    "luke:18:41": (["네게 무엇을 하여 주기를 원하느냐"], "The blind man's answer begins at 가로되."),
    "luke:20:16": (["와서 그 농부들을 진멸하고 포도원을 다른 사람들에게 주리라"], "Hearers' protest begins after 하시니."),
    "luke:20:24": (["데나리온 하나를 내게 보이라 뉘 화상과 글이 여기 있느냐"], "The hearers answer begins at 대답하되."),
    "luke:20:23": (None, "KRV contains only Jesus' speech introducer, omitting KJV-wj Why tempt ye me."),
    "luke:22:31": (["시몬아, 시몬아, 보라 사단이 밀 까부르듯 하려고 너희를 청구하였으나"], "Whole KRV unit is Jesus' speech."),
    "luke:22:35": (["내가 너희를 전대와 주머니와 신도 없이 보내었을 때에 부족한 것이 있더냐"], "The disciples answer begins at 가로되."),
    "luke:22:51": (["이것까지 참으라"], "Jesus' command ends before the two narrator 하시 verbs."),
    "luke:23:46": (["아버지여 내 영혼을 아버지 손에 부탁하나이다"], "KRV narrator resumes at 하고 이 말씀을 하신 후."),
    "luke:24:19": (["무슨 일이뇨"], "The disciples' answer begins at 가로되."),
    "john:1:38": (["무엇을 구하느냐"], "The disciples' answer begins at 가로되."),
    "john:1:39": (["와 보라"], "Narration resumes at 그러므로 저희가."),
    "john:2:8": (["이제는 떠서 연회장에게 갖다 주라"], "Narration resumes at 하시매."),
    "john:4:7": (["물을 좀 달라"], "Request precedes 하시니."),
    "john:6:64": (["그러나 너희 중에 믿지 아니하는 자들이 있느니라"], "Narrator's explanation resumes at 하시니."),
    "john:8:41": (["너희는 너희 아비의 행사를 하는도다"], "The hearers' answer begins at 대답하되."),
    "john:11:34": (["그를 어디 두었느냐"], "The mourners' answer begins at 가로되."),
    "john:11:43": (["나사로야 나오라"], "Jesus' cry follows the narrator's 큰 소리로 and precedes 부르시니."),
    "john:12:28": (["아버지여 아버지의 이름을 영광스럽게 하옵소서"], "The heavenly voice is not Jesus' speech."),
    "john:12:36": (["너희에게 아직 빛이 있을 동안에 빛을 믿으라 그리하면 빛의 아들이 되리라"], "Narration resumes at 예수께서 이 말씀을."),
    "john:18:7": (["누구를 찾느냐"], "KRV indirect-question suffix 고 and reply are outside Jesus' exact words."),
    "john:20:16": (["마리아야"], "Mary's reply and translator gloss follow 하시거늘."),
    "john:21:5": (["얘들아 너희에게 고기가 있느냐"], "The disciples' answer begins at 대답하되."),
    "john:21:15": (["요한의 아들 시몬아 네가 이 사람들보다 나를 더 사랑하느냐", "내 어린 양을 먹이라"], "Two Jesus turns surround Peter's answer."),
    "john:21:16": (["요한의 아들 시몬아 네가 나를 사랑하느냐", "내 양을 치라"], "Two Jesus turns surround Peter's answer."),
    "john:21:17": (["요한의 아들 시몬아 네가 나를 사랑하느냐", "내 양을 먹이라"], "Peter's recollection of the question is narration, not a third Jesus turn."),
    "acts:1:4": (["예루살렘을 떠나지 말고 내게 들은바 아버지의 약속하신 것을 기다리라"], "KRV combines the KJV-wj turns into one continuous command."),
    "acts:9:6": (["네가 일어나 성으로 들어가라 행할 것을 네게 이를 자가 있느니라"], "The Lord's words precede 하시니."),
    "acts:11:16": (["요한은 물로 세례 주었으나 너희는 성령으로 세례 받으리라"], "Peter recalls Jesus' exact words within his narration."),
    "acts:20:35": (["주는 것이 받는 것보다 복이 있다"], "Paul quotes Jesus' maxim within Paul's own speech."),
    "acts:22:7": (["사울아 사울아 네가 왜 나를 핍박하느냐"], "The Lord's words follow narrator 가로되."),
    "acts:26:14": (["사울아 사울아 네가 어찌하여 나를 핍박하느냐 가시채를 뒤발질하기가 네게 고생이니라"], "The Lord's words follow narrator 이르되."),
    "revelation:1:8": (["나는 알파와 오메가라"], "KJV-wj ends after Alpha/Omega and beginning/end; KRV has no beginning/end clause and recasts the remaining divine titles."),
    "revelation:1:11": (["너 보는 것을 책에 써서 에베소, 서머나, 버가모, 두아디라, 사데, 빌라델비아, 라오디게아 일곱 교회에 보내라"], "KRV omits KJV's first Alpha/Omega clause but has the second command."),
}


def key(row: dict) -> str:
    return f"{row['bookId']}:{row['chapter']}:{row['verse']}"


def automatic(row: dict) -> tuple[list[str] | None, str] | None:
    raw = row["targetText"]
    shape = row["kjvSpeechShape"]
    if shape not in {"middle", "suffix"}:
        return None
    openings = list(OPEN.finditer(raw))
    if not openings:
        return None
    opening = openings[0] if shape == "middle" else openings[-1]
    start = opening.end()
    while start < len(raw) and raw[start] == " ":
        start += 1
    closings = list(CLOSE.finditer(raw, start))
    if len(closings) > 1:
        return None
    if shape == "middle" and not closings:
        return None
    end = closings[0].start() if closings else len(raw)
    speech = raw[start:end].rstrip()
    if not speech or NARRATOR.search(speech):
        return None
    return [speech], f"KRV reporting formula {opening.group()} and {'narrator close ' + closings[0].group().strip() if closings else 'native-unit end'}."


def build_ledger(root: Path = ROOT) -> dict:
    queue = build_queue(root, "ko", "korrv")
    if queue["candidateCount"] != 626:
        raise ValueError(f"Pinned KJV-wj mixed candidate inventory changed: {queue['candidateCount']}")
    kjv_manifest = json.loads((root / "shared/assets/books/editions/en/kjv1769/_manifest.json").read_text(encoding="utf-8"))
    target_manifest = json.loads((root / "shared/assets/books/editions/ko/korrv/_manifest.json").read_text(encoding="utf-8"))
    output = []
    reviewed = set()
    relocation_ref = "matthew:20:32"
    target_book = json.loads(
        (root / "shared/assets/books/editions/ko/korrv/new_testament/matthew.json")
        .read_text(encoding="utf-8")
    )
    relocation_target = next(
        verse for chapter in target_book["chapters"] if chapter["number"] == 20
        for verse in chapter["verses"] if verse["verse"] == 33
    )
    relocation_raw = relocation_target["text"].replace("[J]", "").replace("[/J]", "")
    relocation_speech = "너희에게 무엇을 하여주기를 원하느냐"
    if relocation_raw.count(relocation_speech) != 1:
        raise ValueError("KRV Matthew 20:33 relocated speech changed or repeated")
    for candidate in queue["rows"]:
        ref = key(candidate)
        manual = MANUAL.get(ref)
        resolved = manual if manual is not None else automatic(candidate)
        if resolved is None:
            raise ValueError(f"Unresolved KRV speech boundary: {ref}: {candidate['targetText']}")
        selected, method = resolved
        if manual is not None:
            reviewed.add(ref)
        raw = candidate["targetText"]
        spans = []
        if selected is not None:
            for exact in selected:
                if not exact or exact not in raw:
                    raise ValueError(f"Manual/automatic KRV speech absent at {ref}: {exact!r}")
                if raw.count(exact) > 1:
                    raise ValueError(f"Repeated KRV speech needs explicit occurrence at {ref}: {exact!r}")
                spans.append({"exactText": exact})
        relocation = ref == relocation_ref
        item = {
            "collection": candidate["collection"],
            "bookId": candidate["bookId"],
            "chapter": candidate["chapter"],
            "verse": candidate["verse"],
            **({"verseEnd": candidate["verseEnd"]} if candidate.get("verseEnd") else {}),
            "sourceTextSha256": sha256_text(raw),
            "kjvWjVerseSha256": candidate["kjvTextSha256"],
            **({
                "speechRelocatedTo": {
                    "chapter": 20,
                    "verse": 33,
                    "targetTextSha256": sha256_text(relocation_raw),
                    "exactText": relocation_speech,
                    "fulfillment": "supplemental",
                }
            } if relocation else {"noTargetSpeech": True, "reason": method} if selected is None else {}),
            "spans": spans,
            "review": {
                "status": "reviewed",
                "evidence": (
                    f"Pinned KJV1769 wj at {candidate['bookId']} {candidate['chapter']}:{candidate['verse']}; "
                    f"exact ko/korrv text and speaker grammar: {method}"
                ),
            },
        }
        output.append(item)
    output.append({
        "collection": "new_testament",
        "bookId": "matthew",
        "chapter": 20,
        "verse": 33,
        "sourceTextSha256": sha256_text(relocation_raw),
        "kjvWjVerseSha256": next(
            row["kjvWjVerseSha256"] for row in output
            if key(row) == relocation_ref
        ),
        "supplementalFor": [
            {"collection": "new_testament", "bookId": "matthew", "chapter": 20, "verse": 32}
        ],
        "spans": [{"exactText": relocation_speech}],
        "review": {
            "status": "reviewed",
            "evidence": (
                "Pinned KJV1769 WJ Matthew 20:32 question occurs in exact KRV 20:33 "
                "after 가라사대 and before the blind men's 가로되 reply."
            ),
        },
    })
    extra = set(MANUAL) - reviewed
    if extra:
        raise ValueError(f"Manual rows no longer in candidate inventory: {sorted(extra)}")
    output.sort(key=lambda row: (BOOK_ORDER[row["bookId"]], row["chapter"], row["verse"]))
    ledger = {
        "schemaVersion": 1,
        "language": "ko",
        "editionId": "korrv",
        "semanticAuthority": {
            "edition": "en/kjv1769",
            "sourceArchiveSha256": kjv_manifest["source"]["archiveSha256"],
        },
        "targetSource": {
            "url": target_manifest["source"]["url"],
            "sourceFileSha256": target_manifest["source"]["sourceFileSha256"],
        },
        "rows": output,
    }
    checker = ReviewedJesusSpans(
        "ko", "korrv", Path("<in-memory KRV ledger>"),
        {(r["collection"], r["bookId"], r["chapter"], r["verse"]): r for r in output}, set(), set(),
    )
    for candidate in queue["rows"]:
        checker.apply(candidate["collection"], candidate["bookId"], candidate["chapter"],
                      candidate["verse"], candidate["targetText"], candidate.get("verseEnd", candidate["verse"]))
    tagged_relocation = checker.apply(
        "new_testament", "matthew", 20, 33, relocation_raw, 33
    )
    checker.verify_relocations(
        "new_testament", "matthew",
        [{"number": 20, "verses": [{"verse": 33, "text": tagged_relocation}]}],
    )
    checker.validate_coverage({(r["collection"], r["bookId"], r["chapter"], r["verse"]) for r in queue["rows"]})
    return ledger


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--apply", action="store_true", help="write the validated ledger only if absent")
    args = parser.parse_args()
    ledger = build_ledger(args.root)
    if args.apply:
        path = args.root / "tools/traditional/jesus_word_spans/ko_korrv.json"
        if path.exists():
            raise ValueError(f"Refusing to overwrite existing reviewed ledger: {path}")
        path.write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "semanticCandidates": 626,
        "ledgerRows": len(ledger["rows"]),
        "manualExceptions": len(MANUAL),
        "reviewedOmissions": [key(row) for row in ledger["rows"] if row.get("noTargetSpeech")],
        "relocations": [key(row) for row in ledger["rows"] if row.get("speechRelocatedTo")],
        "supplementalTargetRows": sum(row.get("supplementalFor") is not None for row in ledger["rows"]),
        "reviewedSpeechRows": sum(bool(row["spans"]) for row in ledger["rows"]),
        "written": args.apply,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
