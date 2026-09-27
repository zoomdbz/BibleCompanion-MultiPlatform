"""An authentic ZIP must not bless a stale or edited extracted source tree."""

import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import import_kjv_usfm as importer


class KjvSourceIntegrityTests(unittest.TestCase):
    def test_exact_file_bytes_and_filename_match_the_independent_pin(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "02-GENeng-kjv.usfm"
            raw = b"\\id GEN\n\\c 1\n\\v 1 Pinned text.\n"
            path.write_bytes(raw)
            digest = hashlib.sha256(path.name.encode() + b"\0" + hashlib.sha256(raw).digest()).hexdigest().upper()
            with patch.object(importer, "SOURCE_FILE_SET_SHA256", digest):
                self.assertEqual(importer.verified_source_file_set({"GEN": path}, {"GEN"}), digest)
                path.write_bytes(raw.replace(b"Pinned", b"Changed"))
                with self.assertRaisesRegex(importer.UsfmError, "differ from the pinned archive"):
                    importer.verified_source_file_set({"GEN": path}, {"GEN"})

    def test_inventory_order_does_not_change_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            gen, exo = root / "GEN.usfm", root / "EXO.usfm"
            gen.write_bytes(b"GEN")
            exo.write_bytes(b"EXO")
            files = {"GEN": gen, "EXO": exo}
            self.assertEqual(importer.source_file_set_sha256(files, ["GEN", "EXO"]),
                             importer.source_file_set_sha256(files, ["EXO", "GEN"]))


if __name__ == "__main__":
    unittest.main()
