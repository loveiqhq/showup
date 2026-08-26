# -*- coding: utf-8 -*-
"""Every screen built so far, in one file, with every decision applied.

Nine screens / fifteen states:
  Welcome & sign-up  SHOWUP-140, 142, 143 (four states)
  Tutorial           SHOWUP-117, 135, 136, 137, 138, 139

Composed from the two existing generators rather than re-authored, so this cannot drift from the
sheets it is built out of. The two stylesheets share class names — .screen, .eyebrow, .slot, .sb —
so each is scoped to a wrapper class before being concatenated.

The tutorial screens are also brought up to the values settled since they were built: the real
weight-500 italic, the radial-ellipse underline wash sized in em rather than a fixed-width bar, and
the wordmark gradient.
"""
import io
import os
import re
import runpy
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DESKTOP = os.environ.get("SHOWUP_DESKTOP") or os.path.join(os.environ["USERPROFILE"], "Desktop")
OUT = os.path.join(DESKTOP, "ShowUp - ALL SCREENS final.html")

# Both generators rewrap sys.stdout for UTF-8 output. Running them in one process means the second
# wraps the first's wrapper, and when the first is garbage collected it closes the underlying
# buffer — every later print then fails. Keeping a reference to each wrapper prevents the collection
# and the close; restoring the original keeps our own output going to the real stream.
_keep = []
_orig_stdout = sys.stdout

wel = runpy.run_path(os.path.join(HERE, "build_welcome.py"))
_keep.append(sys.stdout)
sys.stdout = _orig_stdout

tut = runpy.run_path(os.path.join(HERE, "build_desktop_html.py"))
_keep.append(sys.stdout)
sys.stdout = _orig_stdout


def scope(css, cls):
    """Prefix every rule's selectors with `.cls `, leaving at-rules and :root alone.

    Both stylesheets define `.screen`, `.eyebrow`, `.slot`, `.sb`, `.hi` and `.gap` with different
    values. Concatenating them would let whichever came last silently win for both sets of frames,
    which is the kind of bug that looks like a design mistake.
    """
    out = []
    i = 0
    while i < len(css):
        brace = css.find("{", i)
        if brace < 0:
            out.append(css[i:])
            break
        selector = css[i:brace]
        stripped = selector.strip()

        # at-rules: keep the header, scope the body
        if stripped.startswith("@"):
            depth, j = 1, brace + 1
            while j < len(css) and depth:
                if css[j] == "{":
                    depth += 1
                elif css[j] == "}":
                    depth -= 1
                j += 1
            body = css[brace + 1:j - 1]
            if stripped.startswith("@font-face"):
                out.append(selector + "{" + body + "}")          # never scoped
            else:
                out.append(selector + "{" + scope(body, cls) + "}")
            i = j
            continue

        close = css.find("}", brace)
        body = css[brace + 1:close]
        parts = []
        for sel in selector.split(","):
            s = sel.strip()
            if not s:
                continue
            if s.startswith(":root") or s == "*" or s == "body" or s.startswith("@"):
                parts.append(s)                                   # shared, leave alone
            else:
                parts.append("." + cls + " " + s)
        out.append(",".join(parts) + "{" + body + "}")
        i = close + 1
    return "".join(out)


# ── the tutorial sheet predates three decisions; bring it up to date ────────
TUT_FIXES = """
/* Values settled after the tutorial screens were first built.
   The underline wash: a radial ellipse sized in em under the italic word, at weight 500 —
   tokens/colors_and_type.css. The tutorial originally used a fixed-width linear bar at 700,
   which was an inference made before that file was available. */
.tut .h-lg::after,.tut .h-xl::after{display:none}
.tut .h-lg em,.tut .h-xl i{font-style:italic;font-weight:500;position:relative;
white-space:nowrap;isolation:isolate}
.tut .h-lg em::before,.tut .h-xl i::before{content:"";position:absolute;left:-2px;right:-2px;
bottom:-.08em;height:.32em;z-index:-1;
background:radial-gradient(ellipse at 50% 100%,rgba(254,104,57,.55) 0%,rgba(254,104,57,0) 70%)}
/* the wordmark's "Up" takes the wordmark gradient, not flat violet */
.tut .wordmark .up{background:linear-gradient(96deg,#812AEC 0%,#D05976 55%,#FE6839 100%);
-webkit-background-clip:text;background-clip:text;color:transparent;font-style:italic}
"""

PAGE = """
:root{--page-bg:#F4F2F7;--page-fg:#1D1129;--page-mute:#6B6377;--page-rule:#DFDAE6}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634}}
:root[data-theme="dark"]{--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634}
*{box-sizing:border-box}
body{margin:0;padding:0 24px 80px;background:var(--page-bg);color:var(--page-fg);
font-family:"SUManrope",-apple-system,"Segoe UI",sans-serif;-webkit-font-smoothing:antialiased}
.page{max-width:1680px;margin:0 auto;display:flex;flex-direction:column;gap:2.4rem}
.masthead{padding-top:3.25rem;display:flex;flex-direction:column;gap:.55rem}
.kicker{margin:0;font-size:.7rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:#FE6839}
.masthead h1{margin:0;font-family:"SULora",Georgia,serif;font-weight:700;
font-size:clamp(2rem,4.4vw,2.9rem);line-height:1.08;letter-spacing:-.02em}
.lede{margin:0;color:var(--page-mute);max-width:74ch;line-height:1.6}
.part{margin:2.5rem 0 0;padding:0 0 .3rem;font-family:"SULora",Georgia,serif;font-weight:700;
font-size:1.75rem;letter-spacing:-.02em;border-bottom:2px solid var(--page-fg)}
.grp{display:flex;flex-direction:column;gap:.9rem;padding-top:1.5rem;border-top:1px solid var(--page-rule)}
.grp h3{margin:0;font-family:"SULora",Georgia,serif;font-size:1.28rem;font-weight:700;letter-spacing:-.01em}
.note{margin:0;font-size:.84rem;color:var(--page-mute);max-width:76ch;line-height:1.55}
.rail{display:flex;flex-wrap:wrap;gap:2rem;align-items:flex-start}
figure{margin:0;display:flex;flex-direction:column;gap:.55rem}
figcaption{font-size:.75rem;color:var(--page-mute);font-variant-numeric:tabular-nums}
.chips{display:flex;flex-wrap:wrap;gap:.4rem;margin:.2rem 0 .4rem}
.chip{font-size:.72rem;font-weight:700;padding:.2rem .6rem;border-radius:999px;
background:rgba(0,171,85,.13);color:#0A7A47}
.chip.i{background:rgba(129,42,236,.12);color:#6B1D8F}
"""

# ── frames ─────────────────────────────────────────────────────────────────
D = wel["DEVICES"]
startup, welcomeback, verify = wel["startup"], wel["welcomeback"], wel["verify"]
tut_screens, welcome_body, tut_frame = tut["SCREENS"], tut["welcome_body"], tut["frame"]
SLOT, ARROW, ICONS = tut["SLOT"], tut["ARROW"], tut["ICONS"]


def wrap(html, cls):
    return '<div class="%s">%s</div>' % (cls, html)


def fig(html, cls, caption):
    return '<figure>%s<figcaption>%s</figcaption></figure>' % (wrap(html, cls), caption)


doc = [
    '<meta charset="utf-8"><title>ShowUp All Screens</title>',
    '<style>%s%s%s%s</style>' % (
        wel["FACES"], PAGE,
        scope(wel["CSS"] + wel["CSS_140"] + wel["CSS_142"] + wel["CSS_143"], "wel"),
        scope(tut["CSS"], "tut") + TUT_FIXES,
    ),
    '<div class="page"><div class="masthead">',
    '<p class="kicker">SHOWUP-117 &#183; 135 &#183; 136 &#183; 137 &#183; 138 &#183; 139 &#183; '
    '140 &#183; 142 &#183; 143</p>',
    '<h1>Every screen, final</h1>',
    '<p class="lede">Nine screens and fifteen states, at the three device sizes the acceptance '
    'criteria name. Every decision taken is applied: the real weight-500 italic, provider-compliant '
    'sign-in buttons, readable legal text and field outlines, and the shortened error message that '
    'lets the code screen fit a small phone. Fonts are embedded &#8212; this works offline and '
    'survives being emailed.</p>',
    '<div class="chips">'
    '<span class="chip">Lora Medium Italic 500 &#183; generated</span>'
    '<span class="chip">Google / Meta / Apple compliant</span>'
    '<span class="chip">Legal text 5.03:1</span>'
    '<span class="chip">Field outlines 3.04:1</span>'
    '<span class="chip">CTA fixed across error states</span>'
    '<span class="chip i">Illustrations are placeholders</span>'
    '</div></div>',
]

# ── Part one: welcome & sign-up ────────────────────────────────────────────
doc.append('<h2 class="part">Welcome &amp; sign-up</h2>')

doc.append('<section class="grp"><h3>SHOWUP-140 &#8212; Startup, first run</h3>'
           '<p class="note">Wordmark 26 &#183; the 132 gap is the element that yields on short '
           'frames &#183; two flex:1 spacers with the social-proof row floating between them &#183; '
           'the three legal phrases are real tappable links.</p><div class="rail">')
for d in D:
    gap = 132 if d[0] != 375 else 72
    doc.append(fig(startup(d, d[5], d[6], gap), "wel",
                   "%d &#215; %d &#183; %s%s" % (d[0], d[1], d[7],
                                                 " &#183; gap 132&#8594;72" if d[0] == 375 else "")))
doc.append('</div></section>')

doc.append('<section class="grp"><h3>SHOWUP-142 &#8212; Welcome back</h3>'
           '<p class="note">Same backdrop and wordmark as Startup, built once and shared &#183; '
           'the 120 gap yields first &#183; each provider button now follows its own brand rules, '
           'with the sunset gradient kept for our own CTA only.</p><div class="rail">')
for d in D:
    gap = 120 if d[0] != 375 else 60
    doc.append(fig(welcomeback(d, d[5], d[6], gap), "wel",
                   "%d &#215; %d &#183; %s%s" % (d[0], d[1], d[7],
                                                 " &#183; gap 120&#8594;60" if d[0] == 375 else "")))
doc.append('</div></section>')

doc.append('<section class="grp"><h3>SHOWUP-142 &#8212; the other lastUsed values</h3>'
           '<p class="note">Always four buttons, canonical order, no duplicates &#183; unknown '
           'falls back to phone and hides the hint row &#183; no name drops the italic span.</p>'
           '<div class="rail">')
for lu, nm, cap in [("apple", "Leo", "lastUsed = apple"), ("google", "Leo", "lastUsed = google"),
                    ("facebook", "Leo", "lastUsed = facebook"),
                    ("nope", "Leo", "unknown &#8594; phone, hint hidden"),
                    ("phone", "", "no name &#8594; no italic span")]:
    doc.append(fig(welcomeback(D[1], D[1][5], D[1][6], 120, lu, nm), "wel", cap))
doc.append('</div></section>')

for st, title, note in [
    ("A", "SHOWUP-143 &#8212; state A, enter number",
     "Helper row reserved at 20 &#183; field outlines now 3.04:1 &#183; the flag is drawn, never an emoji."),
    ("B", "SHOWUP-143 &#8212; state B, invalid number",
     "Digits preserved, never cleared &#183; danger border with a 4px halo and an inline glyph &#183; "
     "CTA disabled at 0.45 opacity and <b>at exactly the same Y as state A</b>."),
    ("C", "SHOWUP-143 &#8212; state C, enter code",
     "Six slots 49 &#215; 62, first active with a violet caret &#183; helper region reserved &#183; "
     "CTA disabled until all six digits are in."),
    ("D", "SHOWUP-143 &#8212; state D, code mismatch",
     "Digits kept &#183; a 4% wash, never a solid red fill &#183; the shortened error is one line and "
     "fits the reserved region, so the CTA does not move &#183; cooldown released to 0."),
]:
    doc.append('<section class="grp"><h3>%s</h3><p class="note">%s</p><div class="rail">' % (title, note))
    for d in D:
        extra = " &#183; slots 44 &#215; 56" if d[0] == 375 else ""
        doc.append(fig(verify(d, d[5], d[6], st), "wel",
                       "%d &#215; %d &#183; %s%s" % (d[0], d[1], d[7], extra)))
    doc.append('</div></section>')

# ── Part two: the tutorial ─────────────────────────────────────────────────
doc.append('<h2 class="part">App tutorial</h2>')

doc.append('<section class="grp"><h3>SHOWUP-117 &#8212; Welcome</h3>'
           '<p class="note">No progress bar &#183; sunset pill CTA &#183; the wordmark and the '
           'underline wash now match the token file.</p><div class="rail">')
for i, d in enumerate(D):
    doc.append(fig(tut_frame(welcome_body(i), d[0], d[1], d[2], d[3], d[4], d[5], d[6]), "tut",
                   "%d &#215; %d &#183; %s" % (d[0], d[1], d[7])))
doc.append('</div></section>')

for sc in tut_screens:
    ah = round(230 * sc.get("scale", 1))
    doc.append('<section class="grp"><h3>%s &#8212; %s</h3>'
               '<p class="note">Progress %d/5 &#183; Back %s%s</p><div class="rail">'
               % (sc["tk"], sc["name"], sc["step"], "active" if sc["back"] else "reserved but hidden",
                  " &#183; terminal CTA, art at 0.62" if sc.get("term") else ""))
    for d in D:
        segs = "".join('<span class="%s"></span>' % ("on" if k < sc["step"] else "") for k in range(5))
        inner = ('<div class="t"><div class="prog">%s</div>'
                 '<div class="eyebrow"><i></i>%s</div>'
                 '<h2 class="h-lg" style="--ul:%dpx">%s</h2>%s%s<div class="gap"></div>'
                 '<div class="nav"><span class="back%s">Back</span>'
                 '<span class="next%s"><span>%s</span><i>%s</i></span></div></div>'
                 % (segs, sc["eyebrow"], sc["ul"], sc["head"], sc["content"], SLOT,
                    "" if sc["back"] else " off", " term" if sc.get("term") else "",
                    sc.get("nextlabel", "Next"), ARROW))
        doc.append(fig(tut_frame(inner, d[0], d[1], d[2], d[3], d[4], d[5], d[6], "--ah:%dpx;" % ah),
                       "tut", "%d &#215; %d &#183; %s" % (d[0], d[1], d[7])))
    doc.append('</div></section>')

doc.append('</div>')
io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(doc))
print("wrote: %s" % OUT)
print("size : %.1f MB" % (os.path.getsize(OUT) / 1024.0 / 1024.0))
