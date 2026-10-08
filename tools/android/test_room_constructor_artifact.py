import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(__file__)
sys.path.insert(0, HERE)
import audit_room_constructors as audit


def _uleb(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        out.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(out)


def dex_fixture(kind="pass", descriptor=audit.TARGET):
    strings = [descriptor, "<init>", "V"]
    if kind == "missing":
        method_count = 0
    else:
        method_count = 1
    string_ids, type_ids, proto_ids, method_ids, class_defs = 112, 128, 144, 160, 168
    data = bytearray(256)
    cursor = 256
    code_off = 0
    if kind not in ("no_code", "bad_code"):
        code_off = 256
        insns_size = 0x1000 if kind == "truncated_code" else 1
        data.extend(struct.pack("<HHHHIIH", 1, 1, 0, 0, 0, insns_size, 0x000E))
        cursor += 18
        while cursor % 4:
            data.append(0)
            cursor += 1
    elif kind == "bad_code":
        code_off = 0x100000
    string_offsets = []
    for value in strings:
        string_offsets.append(cursor)
        raw = value.encode()
        blob = _uleb(len(value)) + raw + b"\0"
        data.extend(blob)
        cursor += len(blob)
    params_off = 0
    if kind == "args":
        while cursor % 4:
            data.append(0)
            cursor += 1
        params_off = cursor
        data.extend(struct.pack("<IHH", 1, 1, 0))
        cursor += 8
    class_data_off = cursor
    flags = 0x401 if kind == "abstract" else 0x1
    if kind == "nonpublic_class":
        flags = 0
    direct_count = 0 if kind == "missing" else 1
    class_data = b"\0\0" + _uleb(direct_count) + b"\0"
    if direct_count:
        access = 0x1 if kind not in ("private", "static", "abstract_ctor") else 0x2
        if kind == "static":
            access = 0x9
        elif kind == "abstract_ctor":
            access = 0x401
        class_data += b"\0" + _uleb(access) + _uleb(code_off)
    data.extend(class_data)
    cursor += len(class_data)
    data[0:8] = b"dex\n035\0"
    struct.pack_into("<I", data, 32, len(data))
    struct.pack_into("<I", data, 36, 112)
    struct.pack_into("<I", data, 40, 0x12345678)
    struct.pack_into("<I", data, 56, len(strings))
    struct.pack_into("<I", data, 60, string_ids)
    struct.pack_into("<I", data, 64, 2)
    struct.pack_into("<I", data, 68, type_ids)
    struct.pack_into("<I", data, 72, 1)
    struct.pack_into("<I", data, 76, proto_ids)
    struct.pack_into("<I", data, 88, method_count)
    struct.pack_into("<I", data, 92, method_ids)
    struct.pack_into("<I", data, 96, 1)
    struct.pack_into("<I", data, 100, class_defs)
    for i, off in enumerate(string_offsets):
        struct.pack_into("<I", data, string_ids + i * 4, off)
    struct.pack_into("<II", data, type_ids, 0, 2)
    struct.pack_into("<III", data, proto_ids, 2, 1, params_off)
    if method_count:
        struct.pack_into("<HHI", data, method_ids, 0, 0, 1)
    struct.pack_into("<IIIIIIII", data, class_defs, 0, flags, 0, 0, 0, 0, class_data_off, 0)
    return bytes(data)


class RoomConstructorArtifactTests(unittest.TestCase):
    def test_constructor_variants(self):
        expected = {"pass": True, "missing": False, "private": False, "args": False, "abstract": False}
        for kind, wanted in expected.items():
            ok, message = audit.inspect_dex(dex_fixture(kind))
            self.assertEqual(ok and message == "public no-arg void constructor present", wanted, (kind, message))

    def test_missing_class_and_truncated_dex(self):
        found, message = audit.inspect_dex(dex_fixture("pass", "Lother/Thing;"))
        self.assertFalse(found)
        self.assertIn("not found", message)
        with self.assertRaises(audit.DexError):
            audit.inspect_dex(dex_fixture("pass")[:-1])

    def test_visibility_and_executable_constructor_requirements(self):
        for kind, phrase in (("nonpublic_class", "not public"), ("static", "no public"),
                             ("abstract_ctor", "no public"), ("no_code", "no public")):
            found, message = audit.inspect_dex(dex_fixture(kind))
            self.assertTrue(found)
            self.assertIn(phrase, message)

    def test_code_item_bounds(self):
        for kind in ("bad_code", "truncated_code"):
            with self.assertRaises(audit.DexError):
                audit.inspect_dex(dex_fixture(kind))

    def test_declared_bounds_malformed_uleb_and_invalid_container(self):
        malformed = bytearray(dex_fixture("pass"))
        malformed[-1] = 0x80
        with self.assertRaises(audit.DexError):
            audit.inspect_dex(bytes(malformed))
        outside = bytearray(dex_fixture("pass"))
        struct.pack_into("<I", outside, 60, len(outside) + 4)
        with self.assertRaises(audit.DexError):
            audit.inspect_dex(bytes(outside))
        shorter = bytearray(dex_fixture("pass"))
        struct.pack_into("<I", shorter, 32, 200)
        with self.assertRaises(audit.DexError):
            audit.inspect_dex(bytes(shorter))
        with tempfile.NamedTemporaryFile(suffix=".aab", delete=False) as handle:
            handle.write(b"not a zip")
            path = handle.name
        try:
            ok, message = audit.audit(path)
            self.assertFalse(ok)
            self.assertIn("invalid ZIP", message)
        finally:
            os.unlink(path)

    def test_zip_apk_root_and_aab_base_dex_multidex(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = os.path.join(directory, "app.apk")
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("classes.dex", dex_fixture("missing"))
                z.writestr("classes2.dex", dex_fixture("pass"))
            self.assertEqual(audit.audit(apk, [audit.TARGET])[0], True)
            aab = os.path.join(directory, "app.aab")
            with zipfile.ZipFile(aab, "w") as z:
                z.writestr("base/dex/classes.dex", dex_fixture("pass"))
                z.writestr("feature/dex/classes.dex", dex_fixture("missing"))
            self.assertEqual(audit.audit(aab, [audit.TARGET])[0], True)

    def test_cli_returns_nonzero_with_specific_failure(self):
        with tempfile.NamedTemporaryFile(suffix=".dex", delete=False) as handle:
            handle.write(dex_fixture("private"))
            path = handle.name
        try:
            result = subprocess.run([sys.executable, os.path.join(HERE, "audit_room_constructors.py"), path], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("not public", result.stdout)
        finally:
            os.unlink(path)

    def test_default_targets_require_all_three_constructors(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "app.aab")
            for bad_index in (None, 1, 2):
                with self.subTest(bad_index=bad_index):
                    with zipfile.ZipFile(path, "w") as archive:
                        for index, target in enumerate(audit.DEFAULT_TARGETS):
                            kind = "missing" if index == bad_index else "pass"
                            archive.writestr(
                                "base/dex/classes%d.dex" % (index + 1),
                                dex_fixture(kind, target),
                            )
                    ok, message = audit.audit(path)
                    self.assertEqual(ok, bad_index is None, message)
                    if bad_index is not None:
                        self.assertIn(audit.DEFAULT_TARGETS[bad_index], message)
                        self.assertIn("no <init>", message)

    def test_failure_reports_the_dex_that_contains_the_target(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "app.apk")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("classes.dex", dex_fixture("pass", "Lother/Thing;"))
                archive.writestr("classes2.dex", dex_fixture("private"))
            ok, message = audit.audit(path, [audit.TARGET])
            self.assertFalse(ok)
            self.assertIn("classes2.dex", message)
            self.assertIn("not public", message)

    def test_cli_target_override_is_repeatable(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "app.apk")
            with zipfile.ZipFile(path, "w") as archive:
                for index, target in enumerate(audit.DEFAULT_TARGETS[:2]):
                    archive.writestr(
                        "classes%d.dex" % (index + 1), dex_fixture("pass", target),
                    )
            command = [sys.executable, os.path.join(HERE, "audit_room_constructors.py"), path]
            for target in audit.DEFAULT_TARGETS[:2]:
                command.extend(["--class-target", target])
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS:", result.stdout)


if __name__ == "__main__":
    unittest.main()
