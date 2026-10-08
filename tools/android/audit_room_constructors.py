#!/usr/bin/env python3
"""Check release APK/AAB DEX files for WorkManager's reflective constructors.

This presence check does not replace optimized-release device testing.
"""

import argparse
import fnmatch
import struct
import sys
import zipfile

TARGET = "Landroidx/work/impl/WorkDatabase_Impl;"
DEFAULT_TARGETS = (
    TARGET,
    "Landroidx/work/ArrayCreatingInputMerger;",
    "Landroidx/work/OverwritingInputMerger;",
)


class DexError(ValueError):
    pass


def _u(data, pos):
    value = 0
    for shift in range(0, 35, 7):
        if pos >= len(data):
            raise DexError("truncated ULEB128 at offset %d" % pos)
        byte = data[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, pos
    raise DexError("invalid ULEB128 at offset %d" % (pos - 1))


def _range(data, off, size, label):
    if off < 0 or size < 0 or off > len(data) or size > len(data) - off:
        raise DexError("%s is outside the DEX bounds" % label)
    return data[off:off + size]


def _string(data, off):
    _, pos = _u(data, off)
    end = data.find(b"\0", pos)
    if end < 0:
        raise DexError("unterminated string at offset %d" % off)
    try:
        return data[pos:end].decode("utf-8")
    except UnicodeDecodeError as exc:
        raise DexError("invalid UTF-8 string at offset %d" % off) from exc


def _code_item(data, off, prefix):
    if not off or off % 4:
        raise DexError("constructor code_item offset is not nonzero and 4-byte aligned")
    _range(data, off, 16, "constructor code_item header")
    insns_size = struct.unpack_from(prefix + "I", data, off + 12)[0]
    _range(data, off + 16, insns_size * 2, "constructor code_item instructions")


def inspect_dex(data, target=None):
    """Return (target_found, explanation) for one standard DEX byte string."""
    target = target or TARGET
    if len(data) < 112:
        raise DexError("DEX header is truncated")
    if data[:4] != b"dex\n" or data[7] != 0:
        raise DexError("invalid DEX magic")
    version = data[4:7]
    if not version.isdigit() or version not in (b"035", b"037", b"038", b"039", b"040"):
        raise DexError("unsupported DEX version %r" % version)
    endian = struct.unpack_from("<I", data, 40)[0]
    if endian == 0x12345678:
        prefix = "<"
    elif endian == 0x78563412:
        prefix = ">"
    else:
        raise DexError("invalid DEX endian tag")
    word = lambda off: struct.unpack_from(prefix + "I", data, off)[0]
    file_size = word(32)
    if file_size > len(data) or file_size < 112:
        raise DexError("DEX file_size exceeds input bounds")
    data = data[:file_size]
    string_count, string_off = word(56), word(60)
    type_count, type_off = word(64), word(68)
    proto_count, proto_off = word(72), word(76)
    method_count, method_off = word(88), word(92)
    class_count, class_off = word(96), word(100)
    _range(data, string_off, string_count * 4, "string_ids")
    _range(data, type_off, type_count * 4, "type_ids")
    _range(data, proto_off, proto_count * 12, "proto_ids")
    _range(data, method_off, method_count * 8, "method_ids")
    _range(data, class_off, class_count * 32, "class_defs")

    def string_at(index):
        if index >= string_count:
            raise DexError("string index %d is outside string_ids" % index)
        return _string(data, word(string_off + index * 4))

    def type_at(index):
        if index >= type_count:
            raise DexError("type index %d is outside type_ids" % index)
        return string_at(word(type_off + index * 4))

    def proto_at(index):
        if index >= proto_count:
            raise DexError("proto index %d is outside proto_ids" % index)
        base = proto_off + index * 12
        return word(base + 4), word(base + 8)

    for i in range(class_count):
        base = class_off + i * 32
        class_idx, flags, class_data_off = word(base), word(base + 4), word(base + 24)
        if type_at(class_idx) != target:
            continue
        if not flags & 0x1:
            return True, "target class is not public"
        if flags & 0x400:
            return True, "target class is abstract"
        if not class_data_off:
            return True, "target class has no class_data, so its constructor is stripped"
        pos = class_data_off
        static_n, pos = _u(data, pos)
        instance_n, pos = _u(data, pos)
        direct_n, pos = _u(data, pos)
        virtual_n, pos = _u(data, pos)
        constructors = []
        for count in (static_n, instance_n):
            for _ in range(count):
                diff, pos = _u(data, pos)
                _, pos = _u(data, pos)
        for count in (direct_n, virtual_n):
            previous = 0
            for _ in range(count):
                diff, pos = _u(data, pos)
                method_idx = previous + diff
                previous = method_idx
                access, pos = _u(data, pos)
                code_off, pos = _u(data, pos)
                if method_idx >= method_count:
                    raise DexError("method index %d is outside method_ids" % method_idx)
                method_base = method_off + method_idx * 8
                name = string_at(word(method_base + 4))
                # method_id: class_idx, proto_idx, name_idx
                proto_idx = struct.unpack_from(prefix + "H", data, method_base + 2)[0]
                if name == "<init>":
                    if code_off:
                        _code_item(data, code_off, prefix)
                    ret_type, params_off = proto_at(proto_idx)
                    if params_off:
                        if params_off + 4 > len(data):
                            raise DexError("proto parameter list is outside DEX bounds")
                        param_n = word(params_off)
                    else:
                        param_n = 0
                    constructors.append((access, param_n, type_at(ret_type), code_off))
        valid = any((a & 0x1) and not (a & 0x408) and n == 0 and ret == "V" and code
                    for a, n, ret, code in constructors)
        if valid:
            return True, "public no-arg void constructor present"
        if not constructors:
            return True, "target class has no <init> constructor"
        if all(not (a & 0x1) for a, _, _, _ in constructors):
            return True, "target constructor exists but is not public"
        if all(n != 0 for _, n, _, _ in constructors):
            return True, "target constructor exists but takes parameters"
        return True, "target class has no public no-arg void constructor"
    return False, "target class not found"


def _dex_entries(path):
    if path.lower().endswith((".dex",)):
        with open(path, "rb") as handle:
            yield path, handle.read()
        return
    try:
        with zipfile.ZipFile(path) as archive:
            names = [n for n in archive.namelist() if
                     ("/" not in n and fnmatch.fnmatch(n, "classes*.dex")) or
                     (n.startswith("base/dex/") and fnmatch.fnmatch(n.rsplit("/", 1)[-1], "classes*.dex"))]
            if not names:
                raise DexError("container has no APK root or AAB base/dex classes*.dex")
            for name in names:
                yield name, archive.read(name)
    except zipfile.BadZipFile as exc:
        raise DexError("invalid ZIP/APK/AAB container") from exc


def audit(path, targets=None):
    targets = tuple(targets or DEFAULT_TARGETS)
    results = {target: [] for target in targets}
    try:
        entries = list(_dex_entries(path))
        for name, data in entries:
            try:
                for target in targets:
                    found, explanation = inspect_dex(data, target)
                    results[target].append((name, found, explanation))
            except DexError as exc:
                return False, "%s: malformed DEX: %s" % (name, exc)
    except (OSError, DexError) as exc:
        return False, str(exc)
    failures = []
    for target, outcomes in results.items():
        valid = [item for item in outcomes if item[1] and item[2] == "public no-arg void constructor present"]
        if not valid:
            candidates = [item for item in outcomes if item[1]] or outcomes
            n, _, e = candidates[0] if candidates else ("(none)", False, "target class not found")
            failures.append((target, n, e))
    if failures:
        return False, "; ".join("%s in %s: %s" % (t, n, e) for t, n, e in failures)
    return True, "all requested target constructors present"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", help="APK, AAB, or DEX to inspect")
    parser.add_argument(
        "--class-target", action="append", dest="targets",
        help="class descriptor to check instead of the defaults (repeatable)",
    )
    args = parser.parse_args(argv)
    ok, message = audit(args.artifact, args.targets)
    print(("PASS: " if ok else "FAIL: ") + message)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
