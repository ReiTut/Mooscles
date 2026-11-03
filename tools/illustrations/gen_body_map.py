#!/usr/bin/env python3
"""The body map of the design system (phase 4A, spec D6), adapted from Jsplice/MuscleMap (MIT).

Two steps, both run with ``python3 -I`` from outside any downloaded folder (nothing from the
source is executed: its files are read as plain text):

  1. Import, once per source commit. Reads ``packages/assets/src/male-front.ts`` and
     ``male-back.ts`` of a MuscleMap checkout and writes ``body-front.svg`` and ``body-back.svg``,
     one ``<g>`` per Mooscles region (``id`` = the region's name) plus the ``BODY`` silhouette.
     The path data is copied as it is; each path keeps its source id in ``data-source``.

       python3 -I tools/illustrations/gen_body_map.py import <MuscleMap checkout> designsystem/illustrations/musclemap

  2. Generate. Reads the two SVGs and writes ``BodyMapPaths.kt`` (form A of the spike: one path
     string per source path, grouped by view and region, parsed at run time with PathParser).
     The output is either a directory (the file is written in it as ``BodyMapPaths.kt``) or a file.

       python3 -I tools/illustrations/gen_body_map.py designsystem/illustrations/musclemap \\
           designsystem/src/commonMain/kotlin/com/mooscles/designsystem/illustration

The geometry is never changed. Numbers are written with at most one decimal, the precision of the
source; the generator stops if that would move any number.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

SOURCE_FILES = {"front": "packages/assets/src/male-front.ts", "back": "packages/assets/src/male-back.ts"}
SVG_FILES = {"front": "body-front.svg", "back": "body-back.svg"}
GENERATED = "BodyMapPaths.kt"
PACKAGE = "com.mooscles.designsystem.illustration"
SVG_NS = "http://www.w3.org/2000/svg"
DECIMALS = 1

# MuscleMap surface id (side suffix removed) -> Mooscles region. Both sides go together.
ID_TO_REGION = {
    "CHEST": "CHEST",
    "SHOULDER_FRONT": "SHOULDERS",
    "SHOULDER_SIDE": "SHOULDERS",
    "SHOULDER_REAR": "SHOULDERS",
    "BICEPS": "BICEPS",
    "TRICEPS": "TRICEPS",
    "FOREARM": "FOREARMS",
    "CORE": "ABDOMINALS",
    "OBLIQUE": "ABDOMINALS",
    "TRAPEZIUS": "TRAPS",
    "RHOMBOID": "MIDDLE_BACK",
    "LATISSIMUS": "LATS",
    "LOWER_BACK": "LOWER_BACK",
    "GLUTEUS": "GLUTES",
    "QUADRICEPS": "QUADRICEPS",
    "HAMSTRINGS": "HAMSTRINGS",
    "ADDUCTOR": "ADDUCTORS",
    "ABDUCTOR": "ABDUCTORS",
    "CALVES": "CALVES",
}

# MuscleMap group -> Mooscles region, for the muscle paths that carry no surface id.
GROUP_TO_REGION = {
    "CHEST": "CHEST",
    "SHOULDERS_FRONT": "SHOULDERS",
    "SHOULDERS_SIDE": "SHOULDERS",
    "SHOULDERS_REAR": "SHOULDERS",
    "BICEPS": "BICEPS",
    "TRICEPS": "TRICEPS",
    "FOREARMS": "FOREARMS",
    "CORE": "ABDOMINALS",
    "OBLIQUES": "ABDOMINALS",
    "TRAPEZIUS": "TRAPS",
    "RHOMBOIDS": "MIDDLE_BACK",
    "LATS": "LATS",
    "BACK_LOWER": "LOWER_BACK",
    "GLUTES": "GLUTES",
    "QUADS": "QUADRICEPS",
    "HAMSTRINGS": "HAMSTRINGS",
    "ADDUCTORS": "ADDUCTORS",
    "ABDUCTORS": "ABDUCTORS",
    "CALVES": "CALVES",
}

# The 17 regions of BodyRegion, in its order. NECK has no paths of its own: the source has no neck
# surface, so BodyMap draws it with the TRAPS paths (spike decision).
REGIONS = [
    "ABDOMINALS", "ABDUCTORS", "ADDUCTORS", "BICEPS", "CALVES", "CHEST", "FOREARMS",
    "GLUTES", "HAMSTRINGS", "LATS", "LOWER_BACK", "MIDDLE_BACK", "NECK", "QUADRICEPS",
    "SHOULDERS", "TRAPS", "TRICEPS",
]
OUTLINE = "BODY"

ENTRY = re.compile(r"\{([^{}]*)\}")
FIELD = re.compile(r'(\w+):\s*"([^"]*)"')
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def fail(message):
    raise SystemExit(f"gen_body_map: {message}")


# ---------------------------------------------------------------------------------------------
# Step 1: import from the MuscleMap TypeScript sources.


def parse_diagram(path):
    """Returns (viewBox, outline entries, muscle entries) of one BodyDiagram .ts file."""
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    view_box = re.search(r'viewBox:\s*"([^"]+)"', text).group(1)
    outline, muscles = [], []
    for match in ENTRY.finditer(text):
        fields = dict(FIELD.findall(match.group(1)))
        if "d" not in fields:
            continue
        # LEFT would mean "mirrored at render time"; the male diagrams draw both sides already.
        if fields.get("side") != "CENTER":
            fail(f"{path}: path {fields.get('id', fields.get('group'))} has side {fields.get('side')}, not CENTER")
        (muscles if "group" in fields else outline).append(fields)
    return view_box, outline, muscles


def region_of(muscle):
    surface = muscle.get("id")
    if surface:
        base = re.sub(r"_(LEFT|RIGHT)$", "", surface)
        if base not in ID_TO_REGION:
            fail(f"unmapped surface id {surface}")
        return ID_TO_REGION[base]
    if muscle["group"] not in GROUP_TO_REGION:
        fail(f"unmapped group {muscle['group']}")
    return GROUP_TO_REGION[muscle["group"]]


def import_source(checkout, svg_dir):
    os.makedirs(svg_dir, exist_ok=True)
    for view, relative in SOURCE_FILES.items():
        view_box, outline, muscles = parse_diagram(os.path.join(checkout, relative))
        if [o.get("id") for o in outline] != [OUTLINE]:
            fail(f"{relative}: expected one {OUTLINE} outline, got {[o.get('id') for o in outline]}")
        grouped = {}
        for muscle in muscles:
            grouped.setdefault(region_of(muscle), []).append(muscle)
        _, _, width, height = view_box.split()
        lines = [
            '<?xml version="1.0" encoding="UTF-8"?>',
            f"<!-- Adapted from Jsplice/MuscleMap (MIT License), {relative}. See LICENSE and PROVENANCE.txt. -->",
            f"<!-- The {view} view of the Mooscles body map: one group per region; data-source is the MuscleMap id. -->",
            f'<svg xmlns="{SVG_NS}" viewBox="{view_box}" width="{width}" height="{height}">',
            f'  <g id="{OUTLINE}">',
            f'    <path data-source="{OUTLINE}" d="{outline[0]["d"]}"/>',
            "  </g>",
        ]
        for region in REGIONS:
            if region not in grouped:
                continue
            lines.append(f'  <g id="{region}">')
            for muscle in grouped[region]:
                lines.append(f'    <path data-source="{muscle.get("id", muscle["group"])}" d="{muscle["d"]}"/>')
            lines.append("  </g>")
        lines.append("</svg>")
        target = os.path.join(svg_dir, SVG_FILES[view])
        with open(target, "w", encoding="utf-8", newline="\n") as handle:
            handle.write("\n".join(lines) + "\n")
        print(f"wrote {target}: {sum(len(v) for v in grouped.values())} muscle paths in {len(grouped)} regions")


# ---------------------------------------------------------------------------------------------
# Step 2: generate the Kotlin from the SVGs.


def commands(d):
    """Splits a path into (command, [numbers]) with implicit repeats made explicit."""
    out = []
    tokens = TOKEN.findall(d)
    if "".join(tokens).replace(" ", "") != re.sub(r"[\s,]", "", d):
        fail(f"unexpected characters in path data: {d[:40]}…")
    i = 0
    cmd = None
    while i < len(tokens):
        token = tokens[i]
        if token.isalpha():
            cmd = token
            i += 1
            if cmd in "Zz":
                out.append((cmd, []))
                continue
        if cmd is None:
            fail("path data does not start with a command")
        if cmd not in "MLCZ":
            # The source uses absolute M, L, C and Z only; anything else would need checking.
            fail(f"command {cmd} is not handled")
        n = ARITY[cmd.upper()]
        numbers = tokens[i:i + n]
        if len(numbers) != n or any(t.isalpha() for t in numbers):
            fail(f"truncated {cmd} in path data")
        out.append((cmd, numbers))
        i += n
        if cmd == "M":
            cmd = "L"  # after a moveto, implicit pairs are linetos
    return out


def fmt(token):
    """The number with at most DECIMALS decimals, failing if that would change its value."""
    value = float(token)
    text = f"{round(value, DECIMALS):.{DECIMALS}f}".rstrip("0").rstrip(".")
    if text in ("-0", ""):
        text = "0"
    if abs(float(text) - value) > 1e-9:
        fail(f"{token} has more than {DECIMALS} decimal(s)")
    return text


def compact(d):
    """The path data re-written compactly: a command letter only when it changes."""
    parts = []
    last = None
    for cmd, numbers in commands(d):
        body = ""
        for number in (fmt(t) for t in numbers):
            body += number if (not body or number.startswith("-")) else " " + number
        if cmd == last and cmd not in "Zz":
            parts.append((" " if not body.startswith("-") else "") + body)
        else:
            parts.append(cmd + body)
        last = cmd
    return "".join(parts)


def read_svg(path):
    # The SVGs are the repository's own files, written by the import step: ElementTree is enough.
    tree = ET.parse(path)
    root = tree.getroot()
    if root.tag != f"{{{SVG_NS}}}svg":
        fail(f"{path}: not an SVG")
    view_box = root.get("viewBox")
    groups = {}
    for group in root.findall(f"{{{SVG_NS}}}g"):
        name = group.get("id")
        if name != OUTLINE and name not in REGIONS:
            fail(f"{path}: unknown group {name}")
        if name == "NECK":
            fail(f"{path}: NECK has no paths of its own (it is drawn with TRAPS)")
        if name in groups:
            fail(f"{path}: group {name} appears twice")
        groups[name] = [compact(p.get("d")) for p in group.findall(f"{{{SVG_NS}}}path")]
        if not groups[name]:
            fail(f"{path}: group {name} is empty")
    if OUTLINE not in groups:
        fail(f"{path}: no {OUTLINE} group")
    return view_box, groups


def source_commit(svg_dir):
    """The MuscleMap commit named on the "Commit:" line of PROVENANCE.txt."""
    with open(os.path.join(svg_dir, "PROVENANCE.txt"), encoding="utf-8") as handle:
        match = re.search(r"^\s*Commit:\s*([0-9a-f]{40})\b", handle.read(), re.M)
    if not match:
        fail(f"{svg_dir}/PROVENANCE.txt names no 40-digit commit")
    return match.group(1)


def kotlin_string(d):
    if '"' in d or "\\" in d or "$" in d:
        fail("path data with a quote, backslash or dollar")
    return f'"{d}"'


def generate(svg_dir, out):
    views = {view: read_svg(os.path.join(svg_dir, name)) for view, name in SVG_FILES.items()}
    boxes = {box for box, _ in views.values()}
    if len(boxes) != 1:
        fail(f"the views have different viewBoxes: {boxes}")
    commit = source_commit(svg_dir)
    x, y, width, height = (float(v) for v in boxes.pop().split())
    if (x, y) != (0, 0):
        fail("the viewBox must start at 0 0")
    lines = [
        "// This file is generated by tools/illustrations/gen_body_map.py; do not edit.",
        "// Source: designsystem/illustrations/musclemap, adapted from Jsplice/MuscleMap (MIT License)",
        f"// at commit {commit}; the changes are in PROVENANCE.txt there.",
        f"package {PACKAGE}",
        "",
        "/**",
        " * The path data of the body map, in the source's viewBox of [VIEW_WIDTH] × [VIEW_HEIGHT]: per",
        " * view, the neutral silhouette and the paths of each region (both sides). [BodyRegion.NECK] has",
        " * none of its own; [BodyMapGeometry] draws it with the [BodyRegion.TRAPS] paths.",
        " */",
        "internal object BodyMapPaths {",
        f"    const val VIEW_WIDTH: Float = {fmt(repr(width))}f",
        f"    const val VIEW_HEIGHT: Float = {fmt(repr(height))}f",
    ]
    for view, (_, groups) in views.items():
        lines += [
            "",
            f"    /** The silhouette of the {view} view: head, neck, hands, feet and the outline. */",
            f"    val {view}Outline: List<String> = listOf(",
        ]
        lines += [f"        {kotlin_string(d)}," for d in groups[OUTLINE]]
        lines += [
            "    )",
            "",
            f"    /** The {view} view: each region's paths. */",
            f"    val {view}: Map<BodyRegion, List<String>> = mapOf(",
        ]
        for region in REGIONS:
            if region not in groups:
                continue
            lines.append(f"        BodyRegion.{region} to listOf(")
            lines += [f"            {kotlin_string(d)}," for d in groups[region]]
            lines.append("        ),")
        lines.append("    )")
    lines.append("}")
    text = "\n".join(lines) + "\n"
    target = os.path.join(out, GENERATED) if os.path.isdir(out) else out
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print(f"wrote {target}: {len(text.encode('utf-8'))} bytes")


def main(argv):
    if len(argv) == 4 and argv[1] == "import":
        import_source(argv[2], argv[3])
    elif len(argv) == 3:
        generate(argv[1], argv[2])
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
