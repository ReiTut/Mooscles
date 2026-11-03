#!/usr/bin/env python3
"""Shape the digits 0-9 of each font with HarfBuzz, with and without `tnum`.

Usage:
    python3 tools/check_fonts.py FONT.ttf [FONT.ttf ...]
        [--lock FILE] [--require-tabular GLOB ...] [--require-proportional GLOB ...]

For every font it prints the advance (in font units) of each digit, shaped once
with no features and once with `tnum`. Variable fonts with a `wght` axis are
shaped at 400, 500, 600 and 700 as well as at their default instance.

It exits with 1 when:
  * a font matched by --require-tabular has digits of different widths with `tnum`;
  * a font matched by --require-proportional has digits all of the same width
    without `tnum` (the control could never fail);
  * a file's SHA-256 differs from the --lock file, or a font is missing from it;
  * a --lock entry has no matching font argument (a stale entry, or a font file
    that was deleted or renamed).

The lock file has one `<file name> <source URL at a pinned commit> <sha256>` line
per font, separated by whitespace; blank lines and lines starting with `#` are
ignored. Fonts are matched by the file name (basename) of each argument. GLOBs are
matched against the file name and the full path (fnmatch).

This is a host-side indicator only. Android (Minikin) and Compose Multiplatform on
iOS (Skia/SkParagraph) shape with HarfBuzz too, but only a measurement on the
device or simulator proves what the app renders.

Requires fonttools and uharfbuzz (versions pinned in .github/workflows/ci.yml).
"""

import argparse
import fnmatch
import hashlib
import os
import sys

import uharfbuzz as hb
from fontTools.ttLib import TTFont

DIGITS = "0123456789"
WEIGHTS = (400, 500, 600, 700)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_lock(path):
    lock = {}
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) != 3:
                raise SystemExit(f"{path}:{number}: expected '<file name> <source URL> <sha256>'")
            name, _source, digest = parts
            if len(digest) != 64 or any(c not in "0123456789abcdefABCDEF" for c in digest):
                raise SystemExit(f"{path}:{number}: '{digest}' is not a SHA-256")
            if name in lock:
                raise SystemExit(f"{path}:{number}: {name} is listed twice")
            lock[name] = digest.lower()
    return lock


def matches(path, patterns):
    name = os.path.basename(path)
    return any(fnmatch.fnmatch(name, p) or fnmatch.fnmatch(path, p) for p in patterns)


def digit_advances(font, features):
    advances = []
    for digit in DIGITS:
        buf = hb.Buffer()
        buf.add_str(digit)
        buf.guess_segment_properties()
        hb.shape(font, buf, features)
        advances.append(sum(pos.x_advance for pos in buf.glyph_positions))
    return advances


def instances(path):
    """(label, variations) pairs: the default instance, then each weight if the font has `wght`."""
    tt = TTFont(path, lazy=True)
    result = [("default", None)]
    if "fvar" in tt:
        axes = {axis.axisTag: axis for axis in tt["fvar"].axes}
        if "wght" in axes:
            low, high = axes["wght"].minValue, axes["wght"].maxValue
            result += [(f"wght={w}", {"wght": w}) for w in WEIGHTS if low <= w <= high]
    has_tnum = False
    if "GSUB" in tt and tt["GSUB"].table.FeatureList is not None:
        has_tnum = any(r.FeatureTag == "tnum" for r in tt["GSUB"].table.FeatureList.FeatureRecord)
    tt.close()
    return result, has_tnum


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("fonts", nargs="+")
    parser.add_argument("--lock")
    parser.add_argument("--require-tabular", action="append", default=[], metavar="GLOB")
    parser.add_argument("--require-proportional", action="append", default=[], metavar="GLOB")
    args = parser.parse_args(argv)

    lock = read_lock(args.lock) if args.lock else None
    problems = []

    for path in args.fonts:
        name = os.path.basename(path)
        digest = sha256(path)
        print(f"== {name}")
        print(f"   sha256 {digest}")
        if lock is not None:
            expected = lock.get(name)
            if expected is None:
                problems.append(f"{name}: not in the lock file {args.lock}")
            elif expected != digest:
                problems.append(f"{name}: sha256 {digest} differs from the lock ({expected})")

        face = hb.Face(hb.Blob.from_file_path(path))
        cases, has_tnum = instances(path)
        print(f"   upem {face.upem}, GSUB tnum feature: {'yes' if has_tnum else 'no'}")
        for label, variations in cases:
            font = hb.Font(face)
            if variations:
                font.set_variations(variations)
            plain = digit_advances(font, {})
            tabular = digit_advances(font, {"tnum": True})
            plain_equal = len(set(plain)) == 1
            tabular_equal = len(set(tabular)) == 1
            print(f"   [{label}] no features: {plain} -> {'equal' if plain_equal else 'proportional'}")
            print(f"   [{label}] tnum:        {tabular} -> {'equal' if tabular_equal else 'NOT equal'}")
            if matches(path, args.require_tabular) and not tabular_equal:
                problems.append(f"{name} [{label}]: digits differ in width with tnum: {tabular}")
            if matches(path, args.require_proportional) and plain_equal:
                problems.append(
                    f"{name} [{label}]: digits already equal without tnum ({plain}), so the control cannot fail"
                )

    if lock is not None:
        checked = {os.path.basename(path) for path in args.fonts}
        for name in sorted(set(lock) - checked):
            problems.append(f"{name}: listed in the lock file {args.lock} but no such font was checked")

    if problems:
        print("\nFAILED:")
        for problem in problems:
            print(f"- {problem}")
        return 1
    print("\nOK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
