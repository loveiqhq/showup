# -*- coding: utf-8 -*-
"""Render every flag from the REAL Kotlin table to an HTML sheet, so they can be looked at.

    python audit/render-flags.py

Drawing a national flag from geometry is easy to get subtly — or badly — wrong, and a wrong flag is
worse than no flag. This parses the actual `COUNTRIES` table rather than a copy of it, so what you
see here is what the app draws. Anything that stops matching the app stops rendering.
"""
import ast
import io
import operator
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KT = os.path.join(ROOT, "android-preview-project/app/src/main/java/com/showup/welcome/CountryCodes.kt")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "flags.html")

src = io.open(KT, encoding="utf-8").read()
W, H = 132.0, 84.0          # 6x the on-screen size, so detail is visible


# The expressions in the table are plain arithmetic like "5f / 9f * 14f / 22f". Walking the AST
# and allowing only numbers and the four operators keeps this a calculator: it cannot call
# anything, read anything, or import anything, however the source file changes.
_OPS = {ast.Add: operator.add, ast.Sub: operator.sub,
        ast.Mult: operator.mul, ast.Div: operator.truediv,
        ast.USub: operator.neg, ast.UAdd: operator.pos}


def arith(expr):
    def walk(node):
        if isinstance(node, ast.Expression):
            return walk(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.BinOp) and type(node.op) in _OPS:
            return _OPS[type(node.op)](walk(node.left), walk(node.right))
        if isinstance(node, ast.UnaryOp) and type(node.op) in _OPS:
            return _OPS[type(node.op)](walk(node.operand))
        raise ValueError("not arithmetic: %r" % expr)
    return walk(ast.parse(expr.replace("f", "").replace("F", ""), mode="eval"))


def col(v):
    """0xAARRGGBB or 0xRRGGBB -> #rrggbb"""
    n = int(v, 16)
    if n > 0xFFFFFF:
        n &= 0xFFFFFF
    return "#%06x" % n


def split_args(s):
    """Top-level commas only, so `listOf(a to b, c to d)` stays one argument."""
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def shapes_svg(body):
    """Translate the FlagShape calls inside a Layers(...) block into SVG."""
    out = []
    for m in re.finditer(r"FlagShape\.(\w+)\((.*?)\)," + chr(10), body + chr(10), re.S):
        kind = m.group(1)
        nums = split_args(m.group(2))

        f = arith

        if kind == "Fill":
            out.append('<rect width="%g" height="%g" fill="%s"/>' % (W, H, col(nums[0])))
        elif kind == "Stripes":
            horiz = "true" in nums[0]
            colours = re.findall(r"0x[0-9A-Fa-f]+", nums[1])
            n = len(colours)
            for i, c in enumerate(colours):
                if horiz:
                    out.append('<rect x="0" y="%g" width="%g" height="%g" fill="%s"/>'
                               % (H * i / n, W, H / n + 0.6, col(c)))
                else:
                    out.append('<rect x="%g" y="0" width="%g" height="%g" fill="%s"/>'
                               % (W * i / n, W / n + 0.6, H, col(c)))
        elif kind == "Box":
            x, y, w, h = [f(v) for v in nums[:4]]
            out.append('<rect x="%g" y="%g" width="%g" height="%g" fill="%s"/>'
                       % (x * W, y * H, w * W, h * H, col(nums[4])))
        elif kind == "Poly":
            pts = re.findall(r"(-?[\d.]+)f\s+to\s+(-?[\d.]+)f", nums[0])
            d = " ".join("%g,%g" % (float(a) * W, float(b) * H) for a, b in pts)
            out.append('<polygon points="%s" fill="%s"/>' % (d, col(nums[-1])))
        elif kind == "Disc":
            cx, cy, r = [f(v) for v in nums[:3]]
            out.append('<circle cx="%g" cy="%g" r="%g" fill="%s"/>'
                       % (cx * W, cy * H, r * H, col(nums[3])))
        elif kind == "Ring":
            cx, cy, r, sw = [f(v) for v in nums[:4]]
            out.append('<circle cx="%g" cy="%g" r="%g" fill="none" stroke="%s" stroke-width="%g"/>'
                       % (cx * W, cy * H, r * H, col(nums[4]), sw * H))
        elif kind == "Star":
            cx, cy, r = [f(v) for v in nums[:3]]
            import math
            pts = []
            for i in range(10):
                rr = (r * H) if i % 2 == 0 else (r * H * 0.382)
                a = math.radians(-90 + i * 36)
                pts.append("%g,%g" % (cx * W + rr * math.cos(a), cy * H + rr * math.sin(a)))
            out.append('<polygon points="%s" fill="%s"/>' % (" ".join(pts), col(nums[3])))
        elif kind == "Checks":
            x, y, w, h = [f(v) for v in nums[:4]]
            n = int(f(nums[4]))
            a, b = col(nums[5]), col(nums[6])
            cw, ch = w * W / n, h * H / n
            for row in range(n):
                for cl in range(n):
                    out.append('<rect x="%g" y="%g" width="%g" height="%g" fill="%s"/>'
                               % (x * W + cl * cw, y * H + row * ch, cw + 0.4, ch + 0.4,
                                  a if (row + cl) % 2 == 0 else b))
    return "".join(out)


def bands_svg(args):
    horiz = args.startswith("bandsH") or "true" in args[:24]
    weighted = re.findall(r"0x([0-9A-Fa-f]+)L?\s+to\s+(\d+)", args)
    if weighted:
        pairs = [("0x" + c, int(w)) for c, w in weighted]
    else:
        pairs = [(c, 1) for c in re.findall(r"0x[0-9A-Fa-f]+", args)]
    total = sum(w for _, w in pairs)
    out, run = [], 0.0
    for c, w in pairs:
        span = (H if horiz else W) * w / total
        if horiz:
            out.append('<rect x="0" y="%g" width="%g" height="%g" fill="%s"/>' % (run, W, span + 0.6, col(c)))
        else:
            out.append('<rect x="%g" y="0" width="%g" height="%g" fill="%s"/>' % (run, span + 0.6, H, col(c)))
        run += span
    return "".join(out)


def cross_svg(args):
    nums = re.findall(r"0x[0-9A-Fa-f]+", args)
    bg, arm = col(nums[0]), col(nums[1])
    inner = col(nums[2]) if "inner" in args and len(nums) > 2 else None
    centred = "centred = true" in args
    t = H * 0.22
    vx = (W - t) / 2 if centred else W * 0.30 - t / 2
    out = ['<rect width="%g" height="%g" fill="%s"/>' % (W, H, bg),
           '<rect x="0" y="%g" width="%g" height="%g" fill="%s"/>' % ((H - t) / 2, W, t, arm),
           '<rect x="%g" y="0" width="%g" height="%g" fill="%s"/>' % (vx, t, H, arm)]
    if inner:
        thin = t * 0.45
        out.append('<rect x="0" y="%g" width="%g" height="%g" fill="%s"/>' % ((H - thin) / 2, W, thin, inner))
        out.append('<rect x="%g" y="0" width="%g" height="%g" fill="%s"/>' % (vx + (t - thin) / 2, thin, H, inner))
    return "".join(out)


rows, drawn, chips = [], 0, []
for m in re.finditer(r'Country\("(\w\w)", "([^"]+)", "(\+\d+)", \d+, \d+,\s*(.*?),\s*"[\d ]+"\),',
                     src, re.S):
    iso, name, dial, art = m.groups()
    art = art.strip()
    if art.startswith("FlagArt.Layers"):
        svg = shapes_svg(art)
        drawn += 1
    elif art.startswith("bands") or art.startswith("FlagArt.Bands"):
        svg = bands_svg(art)
        drawn += 1
    elif art.startswith("FlagArt.Cross"):
        svg = cross_svg(art)
        drawn += 1
    else:
        svg = ('<rect width="%g" height="%g" fill="#F7F2FA"/>'
               '<text x="%g" y="%g" text-anchor="middle" font-family="system-ui" font-size="26" '
               'fill="#9E9AA6" font-weight="700">%s</text>' % (W, H, W / 2, H / 2 + 9, iso))
        chips.append("%s %s" % (iso, name))
    rows.append(
        '<figure><svg width="%g" height="%g" viewBox="0 0 %g %g">%s</svg>'
        '<figcaption>%s<span>%s</span></figcaption></figure>' % (W, H, W, H, svg, name, dial))

html = """<meta charset="utf-8"><title>ShowUp - country flags</title>
<style>
body{margin:0;padding:32px;background:#F4F2F7;font-family:system-ui,sans-serif;color:#1D1129}
h1{font-size:22px;margin:0 0 4px}p{color:#6B6377;margin:0 0 24px;font-size:14px;max-width:70ch}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:22px}
figure{margin:0}
svg{display:block;border-radius:4px;box-shadow:0 0 0 1px rgba(29,17,41,.35);background:#fff}
figcaption{margin-top:8px;font-size:13px;font-weight:600;display:flex;justify-content:space-between;gap:8px}
figcaption span{color:#6B6377;font-weight:500}
</style>
<h1>Country flags, drawn from the app's own table</h1>
<p>%d drawn, %d showing a code chip. Rendered from CountryCodes.kt, at six times the on-screen size
so the detail is visible. If one of these looks wrong, it is wrong in the app too.</p>
<div class="grid">%s</div>
""" % (drawn, len(chips), "".join(rows))

io.open(OUT, "w", encoding="utf-8", newline="\n").write(html)
print("drawn: %d   chips: %d" % (drawn, len(chips)))
for c in chips:
    print("   chip:", c)
print("wrote:", OUT)
