# -*- coding: utf-8 -*-
"""Sign-in buttons — the current design against what each provider's rules require.

Sources:
  Apple    https://developers.apple.com/design/human-interface-guidelines/sign-in-with-apple/
  Google   https://developers.google.com/identity/branding-guidelines
  Meta     https://www.meta.com/brand/resources/facebook/logo/
"""
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
OUT = os.path.join(DESKTOP, "ShowUp - sign-in buttons.html")


def face(fam, w, st, fn):
    with open(os.path.join(FONTS, fn), "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    return ('@font-face{font-family:"%s";font-weight:%s;font-style:%s;'
            'src:url(data:font/ttf;base64,%s) format("truetype");}' % (fam, w, st, b64))


FACES = "".join([
    face("SULora", 700, "normal", "Lora-Bold.ttf"),
    face("SUManrope", 500, "normal", "Manrope-Medium.ttf"),
    face("SUManrope", 600, "normal", "Manrope-SemiBold.ttf"),
    face("SUManrope", 700, "normal", "Manrope-Bold.ttf"),
])

# The official four-colour Google G. The single-colour version in our icon set is a recolour,
# which the branding guidelines prohibit outright.
GOOGLE_G = (
    '<svg width="20" height="20" viewBox="0 0 24 24">'
    '<path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>'
    '<path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>'
    '<path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>'
    '<path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>'
    '</svg>')

APPLE = ('<svg width="20" height="20" viewBox="0 0 24 24" fill="%s">'
         '<path d="M16 4c.5 1.5-.5 3-2 3.5C12 8 11 7 11 5.5 12.5 4 14 3.5 16 4zM18.4 13.5c-.6 1.5-1.4 3-2.9 3'
         '-1.4 0-1.9-.8-3.5-.8-1.6 0-2.1.8-3.5.8-1.5 0-2.4-1.4-3.1-2.9C4 11 4.6 7.6 6.6 6.5c1.3-.7 2.5-.3 3.5 0'
         ' 1 .3 1.4.3 2.4 0 1.1-.4 2.2-.9 3.6-.2-1.7 1.1-2.1 3.5-.4 4.8.5 1.1.8 1.6.7 2.4z"/></svg>')

FACEBOOK = ('<svg width="20" height="20" viewBox="0 0 24 24" fill="%s">'
            '<path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2'
            'v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.45 2.89h-2.34v6.99A10 10 0 0 0 22 12z"/></svg>')

PHONE = ('<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="1.7" '
         'stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1'
         '-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 '
         '1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81'
         '.72A2 2 0 0 1 22 16.92z"/></svg>')

CSS = """
:root{--page-bg:#F4F2F7;--page-fg:#1D1129;--page-mute:#6B6377;--page-rule:#DFDAE6;
--bg:#FFFBF7;--fg:#1D1129;--border:rgba(29,17,41,.12);--orange:#FE6839;
--serif:"SULora",Georgia,serif;--sans:"SUManrope",-apple-system,"Segoe UI",sans-serif;}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}}
:root[data-theme="dark"]{--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}
*{box-sizing:border-box}
body{margin:0;padding:0 24px 72px;background:var(--page-bg);color:var(--page-fg);font-family:var(--sans)}
.page{max-width:1180px;margin:0 auto;display:flex;flex-direction:column;gap:2.4rem}
header{padding-top:3.25rem;display:flex;flex-direction:column;gap:.5rem}
.kicker{margin:0;font-size:.7rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--orange)}
h1{margin:0;font-family:var(--serif);font-weight:700;font-size:clamp(1.9rem,4vw,2.5rem);line-height:1.1;letter-spacing:-.02em}
.lede{margin:0;color:var(--page-mute);max-width:70ch;line-height:1.6}
section{display:flex;flex-direction:column;gap:1rem;padding-top:1.6rem;border-top:1px solid var(--page-rule)}
.title{margin:0;font-family:var(--serif);font-size:1.3rem;font-weight:700}
.meta{margin:0;font-size:.85rem;color:var(--page-mute);max-width:70ch;line-height:1.55}
.cols{display:flex;gap:2.5rem;flex-wrap:wrap;align-items:flex-start}
.col{display:flex;flex-direction:column;gap:.8rem;width:342px}
.colname{margin:0;font-weight:700;font-size:.95rem}
.stack{display:flex;flex-direction:column;gap:10px;padding:20px;border-radius:20px;background:var(--bg);
box-shadow:0 0 0 1px var(--page-rule)}
.b{height:56px;border-radius:9999px;display:flex;align-items:center;justify-content:center;gap:8px;
font-family:var(--sans);font-weight:700;font-size:16px;width:100%;border:0;white-space:nowrap}
.b.ghost{background:transparent;color:var(--fg);border:1px solid var(--border)}
.b.sunset{background:linear-gradient(135deg,#FE6839 0%,#D05976 38%,#812AEC 100%);color:#fff;
box-shadow:0 8px 20px rgba(129,42,236,.28),0 2px 6px rgba(129,42,236,.18)}
/* provider-compliant treatments */
.b.apple{background:#000;color:#fff;font-family:-apple-system,"SF Pro Text","Segoe UI",system-ui;font-weight:600}
.b.google{background:#fff;color:#1F1F1F;border:1px solid #747775;font-family:Roboto,"Segoe UI",system-ui;font-weight:500}
.b.fb{background:#1877F2;color:#fff;font-family:"Segoe UI",Helvetica,system-ui;font-weight:600}
.tag{display:inline-flex;gap:.35rem;align-items:center;font-size:.74rem;font-weight:700;
padding:.2rem .55rem;border-radius:999px;align-self:flex-start}
.no{background:rgba(251,50,59,.12);color:#B71F26}
.yes{background:rgba(0,171,85,.14);color:#0A7A47}
.warn{background:rgba(254,104,57,.14);color:#A33F19}
table{border-collapse:collapse;font-size:.86rem;width:100%;max-width:900px}
th{text-align:left;padding:.5rem .9rem .5rem 0;border-bottom:1px solid var(--page-rule);font-weight:700}
td{padding:.5rem .9rem .5rem 0;border-bottom:1px solid var(--page-rule);vertical-align:top;line-height:1.5}
code{font-family:ui-monospace,Menlo,monospace;font-size:.85em;background:rgba(129,42,236,.09);
padding:.1rem .3rem;border-radius:4px}
"""


def btn(cls, icon, label):
    return '<button class="b %s">%s<span>%s</span></button>' % (cls, icon, label)


CURRENT = ('<div class="stack">'
           + btn("sunset", PHONE % "#fff", "Continue with phone number")
           + btn("ghost", APPLE % "#1D1129", "Continue with Apple")
           + btn("ghost", APPLE.replace(APPLE, "") + '<svg width="20" height="20" viewBox="0 0 24 24" fill="#1D1129">'
                 '<path d="M22 12.2c0-.8-.1-1.4-.2-2H12v3.9h5.6c-.2 1.3-1 2.3-2 3v2.5h3.3c1.9-1.8 3.1-4.4 3.1-7.4z'
                 'M12 22c2.7 0 5-.9 6.7-2.4l-3.3-2.5c-.9.6-2 1-3.4 1-2.6 0-4.9-1.8-5.7-4.2H2.9v2.6C4.6 19.9 8 22 12 22z'
                 'M6.3 13.9c-.2-.6-.3-1.3-.3-1.9s.1-1.3.3-1.9V7.5H2.9C2.3 8.9 2 10.4 2 12s.3 3.1.9 4.5l3.4-2.6z'
                 'M12 5.9c1.5 0 2.8.5 3.9 1.5l2.9-2.9C17 2.9 14.7 2 12 2 8 2 4.6 4.1 2.9 7.5l3.4 2.6C7.1 7.7 9.4 5.9 12 5.9z"/></svg>',
                 "Continue with Google")
           + btn("ghost", FACEBOOK % "#1D1129", "Continue with Facebook")
           + '</div>')

COMPLIANT = ('<div class="stack">'
             + btn("sunset", PHONE % "#fff", "Continue with phone number")
             + btn("apple", APPLE % "#fff", "Continue with Apple")
             + btn("google", GOOGLE_G, "Continue with Google")
             + btn("fb", FACEBOOK % "#fff", "Continue with Facebook")
             + '</div>')

# A middle road: keep the pill silhouette and our type, but restore each logo to its required
# colour on a background that satisfies the rules.
MIDDLE = ('<div class="stack">'
          + btn("sunset", PHONE % "#fff", "Continue with phone number")
          + '<button class="b" style="background:#000;color:#fff">%s<span>Continue with Apple</span></button>' % (APPLE % "#fff")
          + '<button class="b" style="background:#fff;color:#1F1F1F;border:1px solid rgba(29,17,41,.12)">%s<span>Continue with Google</span></button>' % GOOGLE_G
          + '<button class="b" style="background:#1877F2;color:#fff">%s<span>Continue with Facebook</span></button>' % (FACEBOOK % "#fff")
          + '</div>')

doc = [
    '<meta charset="utf-8"><title>ShowUp Sign-in Buttons</title>',
    '<style>%s%s</style>' % (FACES, CSS),
    '<div class="page"><header>',
    '<p class="kicker">SHOWUP-142 &#183; provider brand rules</p>',
    '<h1>The sign-in buttons</h1>',
    '<p class="lede">The design treats all four methods as one set: outlined pills with dark '
    'monochrome icons. Two of the three providers forbid that outright, and compliance is not '
    'optional &#8212; Google makes it a condition of app verification, and Meta enforces it. '
    'Here is the current design, what the rules require, and a middle road.</p></header>',

    '<section><h2 class="title">Side by side</h2>'
    '<p class="meta">Same four methods, same order, same 56 height. Only the treatment differs.</p>'
    '<div class="cols">'
    '<div class="col"><p class="colname">Current design</p>'
    '<span class="tag no">Google and Facebook non-compliant</span>' + CURRENT + '</div>'
    '<div class="col"><p class="colname">Fully compliant</p>'
    '<span class="tag yes">Follows all three rule sets</span>' + COMPLIANT + '</div>'
    '<div class="col"><p class="colname">Middle road</p>'
    '<span class="tag warn">Compliant logos, our pill shape and type</span>' + MIDDLE + '</div>'
    '</div></section>',

    '<section><h2 class="title">What each provider actually requires</h2>'
    '<table>'
    '<tr><th>Provider</th><th>Rule</th><th>Current design</th></tr>'
    '<tr><td><b>Google</b></td>'
    '<td>The <b>G</b> logo may not be recoloured or resized. It must be the standard four-colour '
    'version, on a white background. Required for <b>app verification</b>.</td>'
    '<td><span class="tag no">Fails</span> single-colour dark G on a transparent button &#8212; '
    'both halves of the rule</td></tr>'
    '<tr><td><b>Facebook</b></td>'
    '<td>The logo must be <b>white or Facebook Blue <code>#1877F2</code></b>. Recolouring it to '
    'your own brand colours is explicitly prohibited. Meta enforces by review.</td>'
    '<td><span class="tag no">Fails</span> recoloured to our dark ink</td></tr>'
    '<tr><td><b>Apple</b></td>'
    '<td>Custom buttons <i>are</i> allowed. But the title should be <b>43% of the button height</b>, '
    'the logo should match the button height, and the appearance should be white, white-with-outline '
    'or black.</td>'
    '<td><span class="tag warn">Off-spec</span> 16pt type on a 56 button is 29%, not 43% &#8212; '
    'that ratio wants 24pt. Transparent rather than one of the three appearances</td></tr>'
    '</table></section>',

    '<section><h2 class="title">The trade-off, plainly</h2>'
    '<p class="meta">The compliant row is less elegant. That is not a flaw in the execution &#8212; '
    'it is the point of the rules. Each provider wants their button to look like <i>their</i> button '
    'so people recognise it, which necessarily breaks a uniform set.</p>'
    '<p class="meta"><b>The middle road</b> is what most apps ship: each logo in its required colour '
    'on a background the rules permit, but keeping our pill silhouette, our 56 height and our type. '
    'It satisfies Google and Meta on the points they actually enforce (the logo and its background) '
    'while staying closer to the design. Apple&#8217;s type-ratio guidance is a <i>should</i> rather '
    'than a review gate, so 16pt is a defensible call &#8212; but it is worth making that call '
    'knowingly rather than by accident.</p>'
    '<p class="meta">The one thing that is <b>not</b> a real option is shipping the current row. '
    'Google verification and Meta review both check this, and both fail at submission &#8212; the '
    'most expensive moment to find out.</p></section>',
    '</div>',
]

io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(doc))
print("wrote: %s" % OUT)
