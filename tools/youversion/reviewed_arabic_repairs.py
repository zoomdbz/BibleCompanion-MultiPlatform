#!/usr/bin/env python3
"""Hash-pinned review ledger for seven SAB structural repairs.

The ledger contains no chapter text.  It records only identities, hashes,
reviewed source offsets, and the exact Arabic lexemes at those offsets.  A
caller must prove every chapter-level and range-level value before using it.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def digest_text(value: str) -> str:
    return digest_bytes(value.encode("utf-8"))


def digest_json(value: object) -> str:
    return digest_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")))


# Hash fields use the compact UTF-8 JSON representation produced by
# ``digest_json``.  ``sourceTextSha256`` covers the ordered ``ranges.items()``
# sequence, not only the native range inventory.
REVIEWED_ARABIC_CHAPTERS: dict[tuple[str, str, int], dict[str, Any]] = {
    ("ar", "1CH", 2): {
        "preStorySha256": "eba06ad27f057a787b86789848aadacff2134ab8c10c9c60553d544932c2a0e3",
        "postStorySha256": "7aeb3e523f59d5c8e4e307f28d248b1544d50817c263fc51403d83837d1dc8eb",
        "sourceRangesSha256": "0a201d35114ffdbe365a4aa2d8f502d6b2a30e383cb75f8d6729c531e2feb0fa",
        "sourceTextSha256": "762252f7536ee160e47bf38a224bf0711f1770437369f54f080165ffc27bbd4e",
        "localHeadingsSha256": "43e5f642c3266ca06e589345bfe9c6b682eb1bcff38c6df79552405ba037c382",
        "sourceHeadingsSha256": "0a5886995e6a59e67e5b698299a8f8bb542cd4bde10eac6ce1b9da37a825da0c",
        "postDivineNameSpanCount": 2,
    },
    ("ar", "1CH", 24): {
        "preStorySha256": "d8b36840f26ccbf40dc3e68954511fc701f68a2ac2f78b2a91fdf8f418ff8d18",
        "postStorySha256": "d94d6b2973fda93d766aa57c52d08c196e584d841f024b9d40ed081c9c0736d1",
        "sourceRangesSha256": "9196c18fa42fccc070492cc7687c18a490798ef83d11fb5983de36a03b4140a4",
        "sourceTextSha256": "97d97aac551fd5ced5c1a073e6eac9bcc4a6a5cff599ae6927a6075df14d95c8",
        "localHeadingsSha256": "55b42f6fcf6ce3f79810dd23f5d9a3a7de207326f1c7f5002adf3c4af6b3a87b",
        "sourceHeadingsSha256": "4052a623c1d13fb068e1c5f068776d33202d8a4c32a795c239bb18d58c5e7ba8",
        "postDivineNameSpanCount": 2,
    },
    ("ar", "1CH", 25): {
        "preStorySha256": "86e39f8d692c9147eaeaa08d3b21bc2898cb91ff128f1a20c0dbb4d93b46f958",
        "postStorySha256": "33b1ab492fd67975526f1cf554bf2aac3f9b70c2460a2e1aee986670861c1a69",
        "sourceRangesSha256": "55bad9eda9f77e3894e0e114fdd21e8298fc420470f21a5aeb62c464f12efeda",
        "sourceTextSha256": "db1fccbcce9d67e7ba943c3c7cf733e0aa201dc78b0d769fa00df284d7b62042",
        "localHeadingsSha256": "852db1f76a4491769412cc52b1c570346722bd9b557c5d4d223ff3fe33f01824",
        "sourceHeadingsSha256": "852db1f76a4491769412cc52b1c570346722bd9b557c5d4d223ff3fe33f01824",
        "postDivineNameSpanCount": 3,
    },
    ("ar", "EZR", 10): {
        "preStorySha256": "cabff8b005a512316e8a75577f73f2456134318f3f95c9d435eca784f1599917",
        "postStorySha256": "a276cb9d01de0a8adfa525c923d83efaa0475fe298705d2f32aa833ec4708922",
        "sourceRangesSha256": "a4cbf8fcd060c219a9a2ee8c984aac2ef20750c356f24a5673aeeb80d711964e",
        "sourceTextSha256": "c9e27057864d6b10112a4c9ba00c46fc31612e325f9bb48aebf49ae20da5d48d",
        "localHeadingsSha256": "344aa5e80f67fa4fa91e11fddc790be3dab095f0861054b488ad62a3265a87c1",
        "sourceHeadingsSha256": "6f63b979a1bb542ea9746bcae799ff291fc9ca57b0e0a1e588f8d766717464cf",
        "postDivineNameSpanCount": 1,
    },
    ("ar", "EZR", 2): {
        "preStorySha256": "063c40a06979043ce3d271fc0f7d45504b1ab5f0dd7091fb820ae986c9fec12c",
        "postStorySha256": "572bd0a9e7fde4e0a61a8a8b3a5388750b769c93827f7e121df2db40802e405d",
        "sourceRangesSha256": "058a82320118501a5c2a9c370a0c296ad65d94069b876af0b615ea154f40a25b",
        "sourceTextSha256": "dee9723408b5372538d33579915b9bd7037d3059f49434435357e140dcacfb83",
        "localHeadingsSha256": "acfbc97257f3d0392179e13b44b8a01a2055c1ec7567949303baf43b1450b936",
        "sourceHeadingsSha256": "7b6d6483fe4f80d0978ce9b68de40c267db19480053630a1c884a5bcb456f091",
        "postDivineNameSpanCount": 1,
    },
    ("ar", "NEH", 10): {
        "preStorySha256": "22f436b08ced91d758f33679556cd79683fe93fa9259d96baa16efd8f0312671",
        "postStorySha256": "ae961760ec027f073f0ea99cf34665b359662630562c8a5d1d8b1faaab8af04a",
        "sourceRangesSha256": "5bdd68cc28f2e678847fde24322e417796101c378669eea2c06ba11144d95134",
        "sourceTextSha256": "33756eb557b193f6a547c47b0b1548e4083e55d46222da221c873dd4bc721ee0",
        "localHeadingsSha256": "74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b",
        "sourceHeadingsSha256": "c1c830c008ddee0cb9f98c22886ae9a7814db28aa90c0f5cf2b53ab9bc565e37",
        "postDivineNameSpanCount": 3,
    },
    ("ar", "NUM", 1): {
        "preStorySha256": "e396bb247121abe87bcc9803e9353d38de012e021a9caff1a118ffa622276e22",
        "postStorySha256": "d4e43a874ae2fcb188d16b50495534e55dcef1a1fa97bcb6fffc2b630c378114",
        "sourceRangesSha256": "3b1c9ae0ff139551079ec3b3aa80c14baa6c2af9c1b5a05a34ba9423095e347e",
        "sourceTextSha256": "34df707b6e6d6fc1d143e6b36aebca73eb926366974bd6546a766aaea0fbab32",
        "localHeadingsSha256": "bd5e45815cd01740ac70039224949811861fb209c912e9cd452a7ea49cac30ec",
        "sourceHeadingsSha256": "aa208dce30de144ada973a09a024139a6eb821541a1d57b2a512de857e73ea49",
        "postDivineNameSpanCount": 4,
    },
}


REVIEWED_ARABIC_DN_RANGES: dict[
    tuple[str, str, int, tuple[int, int]], dict[str, Any]
] = {
    ("ar", "1CH", 2, (3, 4)): {
        "localPrefixSha256": "76eba0b135b003d07c903457ccfc1b1b577362b2c532d75291c8a45e5302b664",
        "sourcePrefixSha256": "76eba0b135b003d07c903457ccfc1b1b577362b2c532d75291c8a45e5302b664",
        "taggedPrefixSha256": "e1b70bd1f4bf1cebc74c0f3ae9bc987dc27c5176f907f27f478792c90c073a14",
        "spans": ((144, 149, "اللهِ"), (163, 168, "اللهُ")),
    },
    ("ar", "1CH", 24, (19, 19)): {
        "localPrefixSha256": "b3e9c65f6c3a1e6073be9a72d7ea8c70cce399291c56d8fcbe6ea8b2fd2343af",
        "sourcePrefixSha256": "560c6ec39d71fec42c79cc9a520adaed89f434932f68281fe78bc43bec02cc9e",
        "taggedPrefixSha256": "2d68f4ee8dd0555a15312ccd84cd7227e5435afa4cadde38101e3f1f7f79c135",
        "spans": ((54, 59, "اللهِ"), (145, 155, "الْمَوْلَى")),
    },
    ("ar", "1CH", 25, (3, 3)): {
        "localPrefixSha256": "64faa03ca21681805e4f7e0e0749be45326d4c2db0846390d2a3b7431c2f8c28",
        "sourcePrefixSha256": "8434add2f39f0996e4e6b03b7a09f849102255e65156d83871bffe61dd2cf573",
        "taggedPrefixSha256": "45bf22fae07238f40e946f08180a4f833d58f288a485cd58f24c1fe311c37f83",
        "spans": ((214, 219, "اللهَ"),),
    },
    ("ar", "1CH", 25, (6, 6)): {
        "localPrefixSha256": "0882a2bb5565babe3870cb033b18122e3f90f4a1a6856b88e2c1f4314fb5e002",
        "sourcePrefixSha256": "2a775d3b4fa12dca177c020ac0e767046e69c4ca6b3cff2d02c98645683f7aad",
        "taggedPrefixSha256": "98a2cb0f7a463197b9582cf1570e3a74fab1a1ed854d523df6ee68663425447a",
        "spans": ((59, 64, "اللهِ"),),
    },
    ("ar", "1CH", 25, (7, 7)): {
        "localPrefixSha256": "4f61fc4a374b50cc0089d9406c195423ccfe14e04e97ee1b7e97aa916d73dbe7",
        "sourcePrefixSha256": "10d65e3c206c2b7ba3be944e1988e5024b1eeb66fb76cf4da0a19b690babe641",
        "taggedPrefixSha256": "22a784a4e7f76672735826534ee362347ea37a18d5d9281edc7e020ad8a7af51",
        "spans": ((73, 78, "لّٰهِ"),),
    },
    ("ar", "EZR", 10, (11, 11)): {
        "localPrefixSha256": "dc3d92e4c529636d23ea5d30ade21e24ec58e786b7b07662b43466abad35fa91",
        "sourcePrefixSha256": "b820e306f1ccc64728ed671851da3603e62cf8549946f7f67ab1db805550bef9",
        "taggedPrefixSha256": "eb320540d22d16eb0cf011b9cc89aa813f029ab955bd118c9d1c5ba1c1184841",
        "spans": ((23, 32, "لْمَوْلَى"),),
    },
    ("ar", "EZR", 2, (68, 68)): {
        "localPrefixSha256": "4cd92c72fcf8639fd87d3b95b651b1f03796f382884653651a45d363de5d5447",
        "sourcePrefixSha256": "2d1d3b840d5dddb64c4797ed17194ef89460f8b0f2d39690732fa6a6fb41ddfd",
        "taggedPrefixSha256": "e69fdb47c3b5620045d11ea57fdffe61b0973f6ae5a27d13155cdb13c1b5c803",
        "spans": ((31, 36, "اللهِ"),),
    },
    ("ar", "NEH", 10, (29, 29)): {
        "localPrefixSha256": "4ff1b1daafba6f5ba385e6e35f786a089bffcab24d27d3875e6cdd6895e8eeec",
        "sourcePrefixSha256": "6ab9017b16d743c33628664e49e4019df897886d1cdd2d80c6289ab5eeeb04ff",
        "taggedPrefixSha256": "545446e523f26893963168aea3767d45b92a0c2543ade434b53d89a85f8c6e5c",
        "spans": ((190, 200, "الْمَوْلَى"),),
    },
    ("ar", "NEH", 10, (34, 34)): {
        "localPrefixSha256": "f05525bd2deb500ba31c44d70cf256d879a0d7f1222b88c0532241db7f37fc09",
        "sourcePrefixSha256": "6c7deece80829c545e19c6e6efad9b2f5b87f14c309b972dbaeec090ed474cb5",
        "taggedPrefixSha256": "ac10b39b7ac7bd3e86962e4c554a9b91a9e4eba93683a6f01cae17d22a10efa0",
        "spans": ((213, 218, "اللهِ"),),
    },
    ("ar", "NEH", 10, (35, 35)): {
        "localPrefixSha256": "cb0e7ea9de06c0235a658436f238bf3ff127916eb787e44d91e732ac9630e3a4",
        "sourcePrefixSha256": "216f34a220bdf1896bbe24840cc920f701445360be6c71242dff04c91d00bf8d",
        "taggedPrefixSha256": "4e91d8481728f480544658346cfd1c8b0ac599a93ce627ffc23a149e72ec5f12",
        "spans": ((49, 54, "اللهِ"),),
    },
    ("ar", "NUM", 1, (1, 1)): {
        "localPrefixSha256": "9ef3cd5b6d622cd11853601455a38f1a7be9ca4c6f982aa910a5411bf823fe41",
        "sourcePrefixSha256": "23e5136c69768e0cd7af1b15fa8b5e1d0bff3c112993eb14969e6638bd2bf5fb",
        "taggedPrefixSha256": "243df8729d55f50ea8ed5ab366c43cf1b04d89635f09a10b179d5fcbe8a4b9dd",
        "spans": ((128, 133, "اللهُ"),),
    },
    ("ar", "NUM", 1, (17, 19)): {
        "localPrefixSha256": "3fff1c15877472f24011fec143e704bc244ae814f49d056961f9446145eaa663",
        "sourcePrefixSha256": "952582aef1f5b3454080c3fdf0f7ccbe4df3120e6bacb66473dc423cf173c4de",
        "taggedPrefixSha256": "f27bd3a99219c5252d29b9352d3fb43516874a1059f954e33d65cadf8d5274d1",
        "spans": ((311, 316, "اللهُ"),),
    },
    ("ar", "NUM", 1, (48, 48)): {
        "localPrefixSha256": "6859282dba7e10ca57a02e8cb89261ebc9bdb6cd3dfbc229467e949f390ff02a",
        "sourcePrefixSha256": "f41287ebd099db5f15ac0e422a93ff62af513e78f5065316ca1396aaf5b8736c",
        "taggedPrefixSha256": "4c82e5c03db35630b46602200e4421cb11672ae59746ea626ad44ef2e8e8b428",
        "spans": ((8, 13, "اللهَ"),),
    },
    ("ar", "NUM", 1, (54, 54)): {
        "localPrefixSha256": "db5b0ecef0095668455fbdb2f8eaa1cefc73a997e51e8a3c50030b91ad2f675c",
        "sourcePrefixSha256": "a5514e6ad8fbcef94271dab65aacda0f08e2c7d975524be157baffc561dbfeaa",
        "taggedPrefixSha256": "fa5cd9d820be049a72489f1bae52e8420db1c6efea166213751487cef20bb617",
        "spans": ((54, 59, "اللهُ"),),
    },
}


def chapter_dn_rows(language: str, code: str, chapter: int) -> dict[tuple[int, int], dict[str, Any]]:
    """Return the exact reviewed DN rows for one chapter."""
    return {
        unit: row
        for (row_language, row_code, row_chapter, unit), row in REVIEWED_ARABIC_DN_RANGES.items()
        if (row_language, row_code, row_chapter) == (language, code, chapter)
    }
