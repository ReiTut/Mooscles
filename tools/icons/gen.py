import re, sys, os
import xml.etree.ElementTree as ET

# Usage: python3 -I tools/icons/gen.py <dir with the Lucide 1.63.0 icons/*.svg> <icons output dir>
# The own icons are the SVGs in tools/icons/own/ (24 grid, stroke 2, round caps and joins).
# An element with a fill (fill="black", optionally fill-rule="evenodd") becomes a fill-only path:
# only the *Filled variants (the selected tab icons) have them.
SRC, OUT = sys.argv[1], sys.argv[2]
OWN_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "own")
OWN_NAMES = ["Plate","PlateRecord","Dumbbell","Barbell","Kettlebell","Bench","Cable","Machine","Rope","Body",
 "DumbbellFilled","LayoutGridFilled","HistoryFilled","PlateRecordFilled"]
LUCIDE = "1.63.0"
PKG = "com.mooscles.designsystem.icons"

# (MIcons name, lucide file)
LUCIDE_LIST = [
 ("ArrowLeft","arrow-left"),("Check","check"),("X","x"),("Plus","plus"),("Minus","minus"),
 ("ChevronRight","chevron-right"),("ChevronDown","chevron-down"),("ChevronUp","chevron-up"),
 ("EllipsisVertical","ellipsis-vertical"),("CircleAlert","circle-alert"),("CircleCheck","circle-check"),
 ("Info","info"),("Lock","lock"),("Sun","sun"),("Moon","moon"),("MonitorSmartphone","monitor-smartphone"),
 ("Download","download"),("Upload","upload"),("RefreshCw","refresh-cw"),("Timer","timer"),
 ("SkipForward","skip-forward"),("Pause","pause"),("Play","play"),
 ("Trash2","trash"),  # Lucide 1.x renamed trash-2 to trash
 ("Undo2","undo-2"),("FileText","file-text"),("Wifi","wifi"),("ImageOff","image-off"),
 # Phase 3: the tab bar and the active workout.
 ("History","rotate-ccw-clock"),  # Lucide 1.x renamed history to rotate-ccw-clock
 ("LayoutGrid","layout-grid"),("Search","search"),("Keyboard","keyboard"),("Share2","share-2"),
 ("Pencil","pencil"),("ArrowUp","arrow-up"),("ArrowDown","arrow-down"),("ArrowLeftRight","arrow-left-right"),
 # Phase 4A: the library.
 ("Settings","settings"),("SearchX","search-x"),
 # Phase 4B: the drag handle of reordering.
 ("GripVertical","grip-vertical"),
 # Phase 4B (Task 19): the routine menus.
 ("Copy","copy"),("FolderInput","folder-input"),("ArrowUpToLine","arrow-up-to-line"),("ArrowDownToLine","arrow-down-to-line"),
 # Phase 5A (plan decision 14): a chart's "View data" and "View chart".
 ("Table","table"),("ChartLine","chart-line"),
]

# Own equipment icons: SVG fragments on the same 24 grid, stroke 2, round caps and joins.

def f(x):
    s = ("%.4f" % x).rstrip("0").rstrip(".")
    if s in ("-0", ""): s = "0"
    return s + "f"

NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"M":2,"L":2,"H":1,"V":1,"C":6,"S":4,"Q":4,"T":2,"A":7,"Z":0}
NAMES = {
 "M":"moveTo","L":"lineTo","H":"horizontalLineTo","V":"verticalLineTo","C":"curveTo",
 "S":"reflectiveCurveTo","Q":"quadTo","T":"reflectiveQuadTo","A":"arcTo",
}

def parse_path(d):
    out = []; i = 0; n = len(d)
    while i < n:
        c = d[i]
        if c.isspace() or c == ",": i += 1; continue
        if not c.isalpha(): raise ValueError("bad path " + d)
        cmd = c; i += 1; k = ARGS[cmd.upper()]
        if k == 0: out.append(("close", [])); continue
        first = True
        while True:
            vals = []
            for j in range(k):
                while i < n and (d[i].isspace() or d[i] == ","): i += 1
                if cmd.upper() == "A" and j in (3, 4):
                    vals.append(float(d[i])); i += 1  # flags may be glued
                else:
                    m = NUM.match(d, i)
                    if not m: raise ValueError("bad number at %d in %s" % (i, d))
                    vals.append(float(m.group())); i = m.end()
            name = NAMES[cmd.upper()] + ("Relative" if cmd.islower() else "")
            if not first and cmd.upper() == "M":
                name = "lineTo" + ("Relative" if cmd.islower() else "")
            out.append((name, vals)); first = False
            while i < n and (d[i].isspace() or d[i] == ","): i += 1
            if i >= n or d[i].isalpha(): break
    return out

def shape_path(el):
    t = el.tag.split("}")[-1]; a = el.attrib
    g = lambda k, dflt=0.0: float(a.get(k, dflt))
    if t == "path": return a["d"]
    if t == "line": return "M%s %sL%s %s" % (a["x1"], a["y1"], a["x2"], a["y2"])
    if t == "circle":
        cx, cy, r = g("cx"), g("cy"), g("r")
        return "M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 %s 0z" % (cx-r, cy, r, r, 2*r, r, r, -2*r)
    if t == "rect":
        x, y, w, h = g("x"), g("y"), g("width"), g("height")
        rx = g("rx", a.get("ry", 0) or 0); ry = g("ry", rx)
        if rx == 0 and ry == 0: return "M%s %sh%sv%sh%sz" % (x, y, w, h, -w)
        return ("M%s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sz" %
          (x+rx, y, w-2*rx, rx, ry, rx, ry, h-2*ry, rx, ry, -rx, ry, -(w-2*rx), rx, ry, -rx, -ry, -(h-2*ry), rx, ry, rx, -ry))
    raise ValueError("unsupported element " + t)

def is_filled(el):
    return el.attrib.get("fill", "none") != "none"

def emit_path(d, el):
    if is_filled(el):
        even = el.attrib.get("fill-rule") == "evenodd"
        lines = ["    fillPath(evenOdd = true) {" if even else "    fillPath {"]
    else:
        lines = ["    strokePath {"]
    for name, vals in parse_path(d):
        if name == "close": lines.append("        close()"); continue
        if name.startswith("arcTo"):
            args = [f(vals[0]), f(vals[1]), f(vals[2]), "true" if vals[3] else "false", "true" if vals[4] else "false", f(vals[5]), f(vals[6])]
        else:
            args = [f(v) for v in vals]
        lines.append("        %s(%s)" % (name, ", ".join(args)))
    lines.append("    }")
    return "\n".join(lines)

def write(sub, name, prefix, header, shapes):
    body = "\n".join(emit_path(shape_path(e), e) for e in shapes)
    fills = any(is_filled(e) for e in shapes)
    strokes = not all(is_filled(e) for e in shapes)
    code = f"""{header}
package {PKG}.{sub}

import androidx.compose.ui.graphics.vector.ImageVector
{"import " + PKG + ".fillPath" + chr(10) if fills else ""}import {PKG}.mIcon
{"import " + PKG + ".strokePath" + chr(10) if strokes else ""}
internal val {prefix}{name}: ImageVector by lazy(LazyThreadSafetyMode.NONE) {{
  mIcon("{name}") {{
{body}
  }}
}}
"""
    d = os.path.join(OUT, sub); os.makedirs(d, exist_ok=True)
    open(os.path.join(d, prefix + name + ".kt"), "w").write(code)

for name, fn in LUCIDE_LIST:
    root = ET.parse(os.path.join(SRC, fn + ".svg")).getroot()
    hdr = (f"// Lucide {LUCIDE} (ISC), source: icons/{fn}.svg.\n"
           f"// Generated by tools/icons/gen.py, do not edit.")
    write("lucide", name, "Lucide", hdr, list(root))
for name in OWN_NAMES:
    root = ET.parse(os.path.join(OWN_DIR, name + ".svg")).getroot()
    if name.endswith("Filled"):
        kind = (f"// Own Mooscles icon, the filled variant of {name[:-len('Filled')]} for a selected tab, on the Lucide 24 grid\n"
                f"// (solid parts are fill-only paths, the rest strokes 2 with round caps and joins).")
    else:
        kind = "// Own Mooscles equipment icon, drawn on the Lucide 24 grid (stroke 2, round caps and joins)."
    hdr = (f"{kind}\n"
           f"// Generated by tools/icons/gen.py from tools/icons/own/{name}.svg, do not edit by hand.")
    write("own", name, "Own", hdr, list(root))
