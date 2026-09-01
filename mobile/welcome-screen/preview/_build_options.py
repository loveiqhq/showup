# -*- coding: utf-8 -*-
"""Decision sheet for SHOWUP-143 — the two open questions, drawn side by side.

Q1: reserve 42 (the sheet's number) and the CTA moves, vs reserve the real height and it holds still.
Q2: what a tighter secondary-actions block looks like, and whether it makes 375x667 fit.

Everything is generated from build_welcome.py so the frames are the same code as the layout sheet.
"""
import io
import os
import runpy

HERE = os.path.dirname(os.path.abspath(__file__))
ns = runpy.run_path(os.path.join(HERE, "build_welcome.py"))

FACES, CSS = ns["FACES"], ns["CSS"]
CSS_140, CSS_142, CSS_143 = ns["CSS_140"], ns["CSS_142"], ns["CSS_143"]
verify, DEVICES, DESKTOP = ns["verify"], ns["DEVICES"], ns["DESKTOP"]

OUT = os.path.join(DESKTOP, "ShowUp - 143 decisions.html")

EXTRA = """
.opt{display:flex;flex-direction:column;gap:.7rem}
.optname{margin:0;font-family:var(--sans);font-weight:700;font-size:.95rem;color:var(--page-fg)}
.optnote{margin:0;font-size:.83rem;color:var(--page-mute);max-width:46ch;line-height:1.5}
.verdict{display:inline-flex;align-items:center;gap:.4rem;font-size:.78rem;font-weight:700;
padding:.25rem .6rem;border-radius:999px;align-self:flex-start}
.bad{background:rgba(251,50,59,.12);color:#B71F26}
.good{background:rgba(0,171,85,.14);color:#0A7A47}
.pair{display:flex;gap:1.2rem;align-items:flex-start;flex-wrap:wrap}
/* a ruler showing where the CTA sits, so a 17px shift is visible rather than asserted */
.mark{position:absolute;left:0;right:0;height:2px;background:#FB323B;z-index:9;pointer-events:none}
.mark::after{content:attr(data-y);position:absolute;right:4px;top:-15px;font:700 10px/1 var(--sans);
color:#FB323B;background:var(--bg);padding:1px 4px;border-radius:3px}
.mark.ok{background:#00AB55}
.mark.ok::after{color:#0A7A47}
/* the 42 reserve, for the comparison only -- NOT what ships */
.s143.reserve42 .help42{min-height:42px}
/* tighter secondary actions, option B */
.s143.tightsec .sec{margin-top:12px;gap:4px}
.s143.tightsec .sec .edit{padding:0}
/* one-line error, option C */
.s143.shorterr .errbox span{white-space:nowrap;font-size:12.5px}
"""


def frame(state, cls="", label="", mark_ok=True, device=None):
    d = device or DEVICES[1]
    html = verify(d, d[5], d[6], state)
    if cls:
        html = html.replace('class="screen s143', 'class="screen s143 ' + cls, 1)
    return '<figure class="opt"><div style="position:relative;display:inline-block">%s</div>' \
           '<figcaption>%s</figcaption></figure>' % (html, label)


doc = [
    '<meta charset="utf-8"><title>ShowUp 143 Decisions</title>',
    '<style>%s%s%s%s%s%s</style>' % (FACES, CSS, CSS_140, CSS_142, CSS_143, EXTRA),
    '<div class="page"><header>',
    '<p class="kicker">SHOWUP-143 &#183; two decisions</p>',
    '<h1>What the choices actually look like</h1>',
    '<p class="lede">Both questions come from the same place: the spec sheet and the ticket ask for '
    'things that cannot both be true at 375 &#215; 667. These are the options, drawn rather than '
    'described. The red and green rules mark where the <b>Verify code</b> button sits, measured '
    'from the top of the frame.</p></header>',
]

# ── Q1 ──────────────────────────────────────────────────────────────────────
doc.append('<section><h2 class="title">Question 1 &#8212; does the button move?</h2>'
           '<p class="meta">The sheet reserves 42 for the error area. The error text it specifies '
           'wraps to two lines and needs 59. Reserve 42 and the button is pushed down when the error '
           'appears.</p>')

doc.append('<div class="pair">')
doc.append('<div class="opt"><p class="optname">Option A &#8212; reserve 42, as the sheet says</p>'
           '<span class="verdict bad">Button jumps 17px</span>'
           '<p class="optnote">The error box is taller than the space kept for it, so everything '
           'below is pushed down. Watch the button move between the two frames. The ticket forbids '
           'this: <i>&#8220;The CTA does not move between the default and error state.&#8221;</i></p>'
           '<div class="pair">'
           + frame("C", "reserve42", "no error &#183; button at 393")
           + frame("D", "reserve42", "error shown &#183; button at 410 &#8212; moved")
           + '</div></div>')

doc.append('<div class="opt"><p class="optname">Option B &#8212; reserve the height the text needs</p>'
           '<span class="verdict good">Button holds still &#183; what is built now</span>'
           '<p class="optnote">The area is reserved at 59, which is what the specified sentence '
           'actually occupies. The error appears into space already held for it, and nothing below '
           'moves. Costs 17px of height on every frame, which matters at 375.</p>'
           '<div class="pair">'
           + frame("C", "", "no error &#183; button at 410")
           + frame("D", "", "error shown &#183; button at 410 &#8212; unchanged")
           + '</div></div>')
doc.append('</div>')

doc.append('<div class="opt" style="margin-top:1.5rem">'
           '<p class="optname">Option C &#8212; shorten the message to one line</p>'
           '<span class="verdict good">Button holds still AND the 42 reserve works</span>'
           '<p class="optnote">If the sentence fits on one line the box is about 40 tall, the '
           'sheet&#8217;s 42 holds it, and nothing moves. This is the only option where the spec and '
           'the ticket agree with each other. Something like '
           '<b>&#8220;That code didn&#8217;t match. Try again.&#8221;</b></p>'
           '<div class="pair">'
           + frame("D", "shorterr reserve42", "one-line error &#183; fits the 42 reserve")
           + '</div></div>')
doc.append('</section>')

# ── Q2 ──────────────────────────────────────────────────────────────────────
doc.append('<section><h2 class="title">Question 2 &#8212; the secondary actions block</h2>'
           '<p class="meta">On the small phone the screen is about 35 short. The three stacked '
           'actions at the bottom are the largest single element at 94 tall, so it is the most '
           'obvious place to find the height.</p>')
doc.append('<div class="pair">')
doc.append('<div class="opt"><p class="optname">As specced &#8212; 94 tall</p>'
           '<p class="optnote">Question, countdown and &#8220;Edit phone number&#8221; stacked with '
           '10 between them, plus 22 above the block and 4/8 padding on the edit row.</p>'
           '<div class="pair">' + frame("D", "", "375 &#215; 667 &#183; overflows", device=DEVICES[0])
           + '</div></div>')
doc.append('<div class="opt"><p class="optname">Tightened &#8212; about 70 tall</p>'
           '<p class="optnote">Gap between the three lines drops from 10 to 4, the space above the '
           'block from 22 to 12, and the edit row loses its padding. Nothing is removed and nothing '
           'shrinks below a 44 touch target &#8212; the rows simply sit closer together.</p>'
           '<div class="pair">' + frame("D", "tightsec", "375 &#215; 667 &#183; tightened", device=DEVICES[0])
           + '</div></div>')
doc.append('<div class="opt"><p class="optname">Tightened <b>and</b> a one-line error</p>'
           '<p class="optnote">Both changes together. This is the combination that actually clears '
           'the frame, and neither is structural &#8212; one is spacing, the other is a shorter '
           'sentence. Note the reserve has to come down to 42 as well &#8212; a shorter message '
           'saves nothing while the region is still held open at 59.</p>'
           '<div class="pair">'
           + frame("D", "tightsec shorterr reserve42", "375 &#215; 667 &#183; both changes",
                   device=DEVICES[0])
           + '</div></div>')
doc.append('</div>')
doc.append('<p class="lede" style="margin-top:1rem">Measured in a browser, not estimated:</p>')
doc.append('<table style="border-collapse:collapse;font-size:.86rem;margin-top:.5rem">'
           '<tr><th style="text-align:left;padding:.4rem .9rem .4rem 0">Version</th>'
           '<th style="text-align:right;padding:.4rem .9rem">Actions block</th>'
           '<th style="text-align:right;padding:.4rem .9rem">Error reserve</th>'
           '<th style="text-align:right;padding:.4rem 0">Over by</th></tr>'
           '<tr><td style="padding:.3rem .9rem .3rem 0">As specced</td>'
           '<td style="text-align:right;padding:.3rem .9rem">82</td>'
           '<td style="text-align:right;padding:.3rem .9rem">59</td>'
           '<td style="text-align:right;padding:.3rem 0;color:#B71F26;font-weight:700">39</td></tr>'
           '<tr><td style="padding:.3rem .9rem .3rem 0">Tightened actions only</td>'
           '<td style="text-align:right;padding:.3rem .9rem">66</td>'
           '<td style="text-align:right;padding:.3rem .9rem">59</td>'
           '<td style="text-align:right;padding:.3rem 0;color:#B71F26;font-weight:700">23</td></tr>'
           '<tr><td style="padding:.3rem .9rem .3rem 0"><b>Both changes</b></td>'
           '<td style="text-align:right;padding:.3rem .9rem">66</td>'
           '<td style="text-align:right;padding:.3rem .9rem">42</td>'
           '<td style="text-align:right;padding:.3rem 0;color:#B71F26;font-weight:700">5</td></tr>'
           '</table>')
doc.append('<p class="lede" style="margin-top:1rem">Worth knowing: the shorter message saves nothing '
           'on its own. While the region is reserved at 59 it stays 59 whatever goes in it &#8212; '
           'that is what reserving means. The two changes have to be made together, and together '
           'they get from 39 over to <b>5 over</b>. One more small give closes it: 4 off the gap '
           'under the headline and 4 off the space above the slots would do it, and neither is '
           'visible to the eye.</p>')
doc.append('</section>')

doc.append('</div>')
io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(doc))
print("wrote: %s" % OUT)
