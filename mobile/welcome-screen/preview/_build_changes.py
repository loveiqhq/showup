# -*- coding: utf-8 -*-
"""What changed — the font question, the sign-in buttons, and the field outlines, drawn."""
import base64
import glob
import io
import os

REPO = r"C:\Users\krnji\showup\mobile\welcome-screen"
FONTS = os.path.join(REPO, "fonts")
def _desktop():
    """The Desktop the user actually sees.

    Windows redirects Desktop into OneDrive on managed accounts, and then %USERPROFILE%\Desktop
    still exists as an empty leftover -- so writing there succeeds and the file is invisible.
    Prefer a OneDrive Desktop when one exists, and take the most recently used if several do.
    """
    if os.environ.get("SHOWUP_DESKTOP"):
        return os.environ["SHOWUP_DESKTOP"]
    home = os.environ["USERPROFILE"]
    candidates = [p for p in glob.glob(os.path.join(home, "OneDrive*", "Desktop"))
                  if os.path.isdir(p)]
    if candidates:
        return max(candidates, key=os.path.getmtime)
    return os.path.join(home, "Desktop")


DESKTOP = _desktop()
OUT = os.path.join(DESKTOP, "ShowUp - what changed.html")


def face(fam, w, st, fn):
    with open(os.path.join(FONTS, fn), "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    return ('@font-face{font-family:"%s";font-weight:%s;font-style:%s;'
            'src:url(data:font/ttf;base64,%s) format("truetype");}' % (fam, w, st, b64))


FACES = "".join([
    face("SULora", 400, "normal", "Lora-Regular.ttf"),
    face("SULora", 700, "normal", "Lora-Bold.ttf"),
    face("SULora", 400, "italic", "Lora-Italic.ttf"),
    face("SULora", 500, "italic", "Lora-MediumItalic.ttf"),   # generated — see fonts/README
    face("SULora", 700, "italic", "Lora-BoldItalic.ttf"),
    face("SUManrope", 500, "normal", "Manrope-Medium.ttf"),
    face("SUManrope", 600, "normal", "Manrope-SemiBold.ttf"),
    face("SUManrope", 700, "normal", "Manrope-Bold.ttf"),
])

GOOGLE_G = ('<svg width="18" height="18" viewBox="0 0 24 24" style="display:block;flex:none">'
            '<path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>'
            '<path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>'
            '<path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>'
            '<path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>'
            '</svg>')


def mark(kind, colour):
    P = {
        "apple": '<path d="M16 4c.5 1.5-.5 3-2 3.5C12 8 11 7 11 5.5 12.5 4 14 3.5 16 4zM18.4 13.5c-.6 1.5-1.4 3-2.9 3-1.4 0-1.9-.8-3.5-.8-1.6 0-2.1.8-3.5.8-1.5 0-2.4-1.4-3.1-2.9C4 11 4.6 7.6 6.6 6.5c1.3-.7 2.5-.3 3.5 0 1 .3 1.4.3 2.4 0 1.1-.4 2.2-.9 3.6-.2-1.7 1.1-2.1 3.5-.4 4.8.5 1.1.8 1.6.7 2.4z"/>',
        "facebook": '<path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.45 2.89h-2.34v6.99A10 10 0 0 0 22 12z"/>',
        "googleflat": '<path d="M22 12.2c0-.8-.1-1.4-.2-2H12v3.9h5.6c-.2 1.3-1 2.3-2 3v2.5h3.3c1.9-1.8 3.1-4.4 3.1-7.4zM12 22c2.7 0 5-.9 6.7-2.4l-3.3-2.5c-.9.6-2 1-3.4 1-2.6 0-4.9-1.8-5.7-4.2H2.9v2.6C4.6 19.9 8 22 12 22zM6.3 13.9c-.2-.6-.3-1.3-.3-1.9s.1-1.3.3-1.9V7.5H2.9C2.3 8.9 2 10.4 2 12s.3 3.1.9 4.5l3.4-2.6zM12 5.9c1.5 0 2.8.5 3.9 1.5l2.9-2.9C17 2.9 14.7 2 12 2 8 2 4.6 4.1 2.9 7.5l3.4 2.6C7.1 7.7 9.4 5.9 12 5.9z"/>',
        "phone": '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81.72A2 2 0 0 1 22 16.92z"/>',
    }[kind]
    if kind == "phone":
        return ('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="%s" '
                'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" '
                'style="display:block;flex:none">%s</svg>' % (colour, P))
    return ('<svg width="18" height="18" viewBox="0 0 24 24" fill="%s" '
            'style="display:block;flex:none">%s</svg>' % (colour, P))


CSS = """
:root{--page-bg:#F4F2F7;--page-fg:#1D1129;--page-mute:#6B6377;--page-rule:#DFDAE6;
--bg:#FFFBF7;--fg:#1D1129;--orange:#FE6839;--muted:rgba(29,17,41,.62);
--serif:"SULora",Georgia,serif;--sans:"SUManrope",-apple-system,"Segoe UI",sans-serif;}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}}
:root[data-theme="dark"]{--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}
*{box-sizing:border-box}
body{margin:0;padding:0 24px 72px;background:var(--page-bg);color:var(--page-fg);font-family:var(--sans)}
.page{max-width:1120px;margin:0 auto;display:flex;flex-direction:column;gap:2.4rem}
header{padding-top:3.25rem;display:flex;flex-direction:column;gap:.5rem}
.kicker{margin:0;font-size:.7rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--orange)}
h1{margin:0;font-family:var(--serif);font-weight:700;font-size:clamp(1.9rem,4vw,2.5rem);line-height:1.1;letter-spacing:-.02em}
.lede{margin:0;color:var(--page-mute);max-width:70ch;line-height:1.6}
section{display:flex;flex-direction:column;gap:1rem;padding-top:1.6rem;border-top:1px solid var(--page-rule)}
.title{margin:0;font-family:var(--serif);font-size:1.3rem;font-weight:700}
.meta{margin:0;font-size:.86rem;color:var(--page-mute);max-width:72ch;line-height:1.6}
.card{background:var(--bg);border-radius:20px;padding:26px;box-shadow:0 0 0 1px var(--page-rule);color:#1D1129}
.row{display:flex;gap:2rem;flex-wrap:wrap;align-items:flex-start}
.lbl{margin:0 0 .5rem;font-size:.76rem;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--page-mute)}
/* font specimens */
.spec{font-family:var(--serif);font-weight:700;font-size:38px;line-height:1.05;letter-spacing:-.02em;color:#1D1129}
.spec em{font-style:italic;position:relative;isolation:isolate;white-space:nowrap}
.spec em::after{content:"";position:absolute;left:-2px;right:-2px;bottom:-.08em;height:.32em;z-index:-1;
background:radial-gradient(ellipse at 50% 100%,rgba(254,104,57,.55) 0%,rgba(254,104,57,0) 70%)}
.w400 em{font-weight:400}
.w500 em{font-weight:500}
.w700 em{font-weight:700}
.big{font-size:76px}
/* buttons */
.btn{height:56px;border-radius:9999px;display:flex;align-items:center;justify-content:center;gap:8px;
font-family:var(--sans);font-weight:700;font-size:16px;width:100%;border:0;white-space:nowrap}
.btn.ghost{background:transparent;color:#1D1129;border:1px solid rgba(29,17,41,.12)}
.btn.apple{background:#000;color:#fff}
.btn.google{background:#fff;color:#1F1F1F;border:1px solid #747775}
.btn.facebook{background:#1877F2;color:#fff}
.btn.sunset{background:linear-gradient(135deg,#FE6839 0%,#D05976 38%,#812AEC 100%);color:#fff;
box-shadow:0 8px 20px rgba(129,42,236,.28),0 2px 6px rgba(129,42,236,.18)}
.stack{display:flex;flex-direction:column;gap:10px;width:342px}
/* fields */
.fld{height:56px;border-radius:14px;background:#fff;display:flex;align-items:center;padding:0 18px;
font-family:var(--sans);font-weight:600;font-size:17px;color:#1D1129;width:342px}
.old{border:1.5px solid rgba(29,17,41,.12)}
.new{border:1.5px solid rgba(29,17,41,.46)}
.slots{display:flex;gap:8px}
.slot{width:49px;height:62px;border-radius:14px;background:#fff;display:flex;align-items:center;
justify-content:center;font-family:var(--serif);font-weight:700;font-size:30px;color:#1D1129}
.tag{display:inline-flex;font-size:.74rem;font-weight:700;padding:.2rem .55rem;border-radius:999px;align-self:flex-start}
.no{background:rgba(251,50,59,.12);color:#B71F26}
.yes{background:rgba(0,171,85,.14);color:#0A7A47}
.warn{background:rgba(254,104,57,.14);color:#A33F19}
table{border-collapse:collapse;font-size:.86rem;max-width:820px}
th{text-align:left;padding:.5rem .9rem .5rem 0;border-bottom:1px solid var(--page-rule)}
td{padding:.5rem .9rem .5rem 0;border-bottom:1px solid var(--page-rule);line-height:1.5}
"""

doc = [
    '<meta charset="utf-8"><title>ShowUp What Changed</title>',
    '<style>%s%s</style>' % (FACES, CSS),
    '<div class="page"><header>',
    '<p class="kicker">SHOWUP-140 &#183; 142 &#183; 143</p>',
    '<h1>What changed, and the font question</h1>',
    '<p class="lede">Three things: the sign-in buttons now follow each provider&#8217;s rules, the '
    'input outlines are dark enough to see, and the italic weight is the one thing still waiting on '
    'a file from design.</p></header>',

    # ── FONT ────────────────────────────────────────────────────────────────
    '<section><h2 class="title">1 &#183; The italic weight</h2>'
    '<p class="meta">The design says the emphasised word in each headline should be '
    '<b>Lora Medium Italic (weight 500)</b>. The font files we were given contain only '
    '<b>400</b> and <b>700</b> italic &#8212; nothing between them, and no master file to generate '
    'one from. We are using 400.</p>'
    '<div class="card"><div class="row">'
    '<div><p class="lbl">400 &#183; what we use now</p>'
    '<div class="spec w400">Start <em>meeting</em> today.</div></div>'
    '<div><p class="lbl">500 &#183; the real file, generated</p>'
    '<div class="spec w500">Start <em>meeting</em> today.</div></div>'
    '<div><p class="lbl">700 &#183; too heavy</p>'
    '<div class="spec w700">Start <em>meeting</em> today.</div></div>'
    '</div></div>'
    '<p class="meta"><b>The middle one is now a real font.</b> The two cuts we were given turned out '
    'to interpolate cleanly — 889 glyphs, identical structure — so the missing 500 was generated '
    'from them rather than faked. Every point is a genuine interpolation between two real masters, '
    'which is the same arithmetic a variable font performs. It is not what the foundry would ship, '
    'since a type designer may hand-correct an intermediate master, but it is a legitimate 500.</p>'
    '<div class="card" style="margin-top:.5rem"><div class="row">'
    '<div><p class="lbl">400 at large size</p><div class="spec w400 big"><em>meeting</em></div></div>'
    '<div><p class="lbl">500 &#183; real</p><div class="spec w500 big"><em>meeting</em></div></div>'
    '<div><p class="lbl">700</p><div class="spec w700 big"><em>meeting</em></div></div>'
    '</div></div>'
    '<p class="meta"><b>The decision:</b> either design sends one file &#8212; '
    '<code>Lora-MediumItalic.ttf</code> &#8212; or confirms 400 is fine. Looking at these side by '
    'side, 400 reads perfectly well against the 700 around it. 700 is clearly wrong: the emphasis '
    'disappears because it matches the rest of the headline.</p></section>',

    # ── BUTTONS ─────────────────────────────────────────────────────────────
    '<section><h2 class="title">2 &#183; The sign-in buttons &#8212; now compliant</h2>'
    '<p class="meta">Built to each provider&#8217;s requirements, as you asked. The pill shape, the '
    '56 height and our Manrope label are kept &#8212; those are ours and no provider constrains '
    'them. What changed is each logo&#8217;s colour and the background it sits on.</p>'
    '<div class="row">'
    '<div><p class="lbl">Before</p><span class="tag no">Google and Meta both broken</span>'
    '<div class="stack" style="margin-top:.6rem">'
    + '<button class="btn sunset">%s<span>Continue with phone number</span></button>' % mark("phone", "#fff")
    + '<button class="btn ghost">%s<span>Continue with Apple</span></button>' % mark("apple", "#1D1129")
    + '<button class="btn ghost">%s<span>Continue with Google</span></button>' % mark("googleflat", "#1D1129")
    + '<button class="btn ghost">%s<span>Continue with Facebook</span></button>' % mark("facebook", "#1D1129")
    + '</div></div>'
    '<div><p class="lbl">Now</p><span class="tag yes">Follows all three rule sets</span>'
    '<div class="stack" style="margin-top:.6rem">'
    + '<button class="btn sunset">%s<span>Continue with phone number</span></button>' % mark("phone", "#fff")
    + '<button class="btn apple">%s<span>Continue with Apple</span></button>' % mark("apple", "#fff")
    + '<button class="btn google">%s<span>Continue with Google</span></button>' % GOOGLE_G
    + '<button class="btn facebook">%s<span>Continue with Facebook</span></button>' % mark("facebook", "#fff")
    + '</div></div></div>'
    '<table style="margin-top:1rem">'
    '<tr><th>Provider</th><th>What their rule says</th><th>What we do now</th></tr>'
    '<tr><td><b>Google</b></td><td>The G may not be recoloured or resized, and must sit on white. '
    'Required for app verification.</td><td>Official four-colour G on white, with Google&#8217;s own '
    '<code>#747775</code> border</td></tr>'
    '<tr><td><b>Facebook</b></td><td>Logo must be white or Facebook Blue <code>#1877F2</code>. '
    'Recolouring to a host brand&#8217;s palette is prohibited.</td><td>White mark on Facebook '
    'Blue</td></tr>'
    '<tr><td><b>Apple</b></td><td>Custom buttons allowed; appearance should be white, '
    'white-with-outline or black.</td><td>Black with a white mark</td></tr>'
    '</table>'
    '<p class="meta" style="margin-top:.8rem"><b>One point still open on Apple.</b> Their guidance '
    'also says the label should be 43% of the button height &#8212; at 56 that is 24pt, and ours is '
    '16pt. That one is a <i>should</i> rather than a review gate, and matching it would make the '
    'Apple button&#8217;s text noticeably larger than the other three. We have kept 16 for '
    'consistency. Worth design confirming, but it will not fail review.</p></section>',

    # ── FIELDS ──────────────────────────────────────────────────────────────
    '<section><h2 class="title">3 &#183; The input outlines &#8212; now visible</h2>'
    '<p class="meta">The box outlines were <b>1.28</b> against a required <b>3.0</b>. Because the '
    'white fill is almost identical to the page background (1.03), that outline is the only thing '
    'showing where the field is. They are now <b>3.04</b> against the page and <b>3.14</b> against '
    'the fill.</p>'
    '<div class="card"><div class="row">'
    '<div><p class="lbl">Before &#183; 1.28 &#8212; fails</p>'
    '<div class="fld old">176 123 45 678</div>'
    '<div class="slots" style="margin-top:14px">'
    + "".join('<div class="slot old">%s</div>' % d for d in "482170") + '</div></div>'
    '<div><p class="lbl">Now &#183; 3.04 &#8212; passes</p>'
    '<div class="fld new">176 123 45 678</div>'
    '<div class="slots" style="margin-top:14px">'
    + "".join('<div class="slot new">%s</div>' % d for d in "482170") + '</div></div>'
    '</div></div>'
    '<p class="meta"><b>Where the value came from:</b> it is a colour the design system already '
    'contained. The same ink at 46% was being used for small text, where it fails, while the '
    'outlines used 12%, where they fail. Text moved up to 62% and outlines moved up to 46% &#8212; '
    'both now pass, and no new colour was invented.</p></section>',
    '</div>',
]

io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(doc))
print("wrote: %s" % OUT)
