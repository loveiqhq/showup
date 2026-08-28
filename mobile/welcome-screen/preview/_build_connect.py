# -*- coding: utf-8 -*-
"""Connect an account + Re-login SSO (SHOWUP-144 / 145) — layout sheet.

Values come from, in the handoff's own order of authority:
  0. the epic's provider-sign-in note      - overrules everything below
  1. welcome/screen-connect-reference.jsx  - wins on numbers
  2. the Jira tickets                      - win on behaviour, scope, copy
  3. the spec-sheet PNGs                   - win on nothing

The shell, fonts, tokens, icons and device list are imported from _build_welcome rather than
forked, so the two sheets cannot drift apart — the same reason the app has one AuthMethodList
instead of two.

    python preview/_build_connect.py
"""
import glob
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from _build_welcome import (  # noqa: E402
    FACES, CSS, DEVICES, GOOGLE_G, METHODS, WORDMARK, backdrop, chrome, ico,
)

# Deliberately NOT re-wrapping sys.stdout: importing _build_welcome already replaced it with a
# UTF-8 wrapper, and wrapping that wrapper closes the underlying buffer when the first one is
# collected -- the print at the end then dies with "I/O operation on closed file".

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
OUT = os.path.join(DESKTOP, "ShowUp - connect account.html")

# ── connect-only CSS. Everything else is inherited from the welcome sheet. ──
CSS144 = """
.s144 .body{padding:20px 24px 0}
.s144 .wgap{height:var(--wgap);flex:none}
.s144 .stack{display:flex;flex-direction:column;gap:16px;flex:none}
/* The heart is INSIDE the text flow, not a flex sibling.
   The sheet and the ticket both put it "on the baseline" at the end of the headline, and making it
   part of the sentence is the only way it lands there on whichever line the text happens to end.
   It also removes a whole class of bug: a sibling in a flex row reserves its width on EVERY line
   and, because a flex item defaults to min-width:auto -- "never get narrower than my longest
   unbreakable run" -- the nowrap emphasis span made that floor the full line. The headline then
   refused to wrap, overflowed, and pushed the heart off the screen. In the flow it cannot. */
.s144 .h1{font-size:var(--hs);line-height:1.05;text-wrap:balance}
/* The emphasis and the heart travel together, so the heart is never orphaned onto its own line. */
.s144 .h1 .nb{white-space:nowrap}
.s144 .h1 .nb .heart{display:inline-block;vertical-align:baseline;margin-left:12px}
.s144 .sub{margin:0;font-family:var(--sans);font-weight:500;font-size:16px;line-height:1.45;
color:var(--neutral);max-width:310px}
.s144 .methods{display:flex;flex-direction:column;gap:10px;margin-bottom:12px;flex:none}

/* The skip: a different shape and weight from the three connect buttons so it never reads as a
   fourth provider. Dashed 1.5, and NEVER disabled or dimmed. */
/* --border (ink 12%) as the reference draws it. It was briefly darkened to ink 46% for
   WCAG 1.4.11, which wants 3:1 for the boundary that identifies a control -- see
   audit/AUDIT-connect-144-145.md finding 7. Reverted on request: this is the designed look, the
   label and arrow carry the control's identity at 5.03:1, and the deviation is recorded rather
   than made silently. */
.s144 .skip{margin-top:4px;height:52px;border-radius:16px;background:var(--elevated);
border:1.5px dashed var(--border);display:inline-flex;align-items:center;justify-content:center;
gap:8px;font-family:var(--sans);font-weight:600;font-size:15px;color:var(--muted)}
.s144 .skip.hot{color:var(--fg)}

/* Banner and notice are INSERTED above the list, never overlaid, so the buttons do not move. */
.s144 .banner{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border-radius:14px;
background:rgba(251,50,59,.07);border:1px solid rgba(251,50,59,.18);margin-bottom:14px;flex:none}
.s144 .banner i{flex:none;width:20px;height:20px;border-radius:50%;background:var(--danger);
color:#fff;display:inline-flex;align-items:center;justify-content:center;font-family:var(--serif);
font-weight:700;font-size:13px;line-height:1;margin-top:1px;font-style:normal}
.s144 .banner p{margin:0;font-family:var(--sans);font-size:13.5px;line-height:1.4;
color:var(--danger-fg);font-weight:500}
.s144 .notice{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border-radius:14px;
background:#F7F2FA;border:1px solid rgba(29,17,41,.06);margin-bottom:12px;flex:none}
.s144 .notice i{flex:none;width:20px;height:20px;border-radius:50%;background:rgba(29,17,41,.10);
display:inline-flex;align-items:center;justify-content:center;color:var(--fg)}
.s144 .notice p{margin:0;font-family:var(--sans);font-size:13.5px;line-height:1.4;color:var(--fg)}
.s144 .notice p b{font-weight:600}
.s144 .notice p span{color:var(--muted)}

/* Spinner: 18, 2px, one turn every 0.9s. */
.s144 .spin{width:18px;height:18px;border-radius:50%;border:2px solid rgba(255,255,255,.35);
border-top-color:#fff;animation:sa-spin .9s linear infinite;flex:none}
.s144 .spin.dark{border-color:rgba(29,17,41,.22);border-top-color:var(--fg)}
@keyframes sa-spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion:reduce){.s144 .spin,.s144 .ring circle{animation:none}}

/* E and F replace the header block with a centred hero in the SAME column - not a new route. */
.s144 .hero{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;
gap:var(--herogap);text-align:center;min-height:0}
.s144 .ringbox{position:relative;width:120px;height:120px;flex:none}
.s144 .ring{position:absolute;inset:0}
.s144 .ring circle.arc{animation:sa-spin 1.4s linear infinite;transform-origin:60px 60px}
.s144 .puck{position:absolute;inset:18px;border-radius:50%;background:#fff;
box-shadow:0 4px 12px rgba(46,1,71,.06);display:inline-flex;align-items:center;justify-content:center}
.s144 .halo{position:absolute;inset:0;border-radius:50%;
background:radial-gradient(circle,rgba(0,171,85,.22) 0%,rgba(0,171,85,0) 70%)}
.s144 .puck.ok{inset:16px;box-shadow:0 8px 24px rgba(0,171,85,.18),0 2px 6px rgba(46,1,71,.08)}
.s144 .badge{position:absolute;right:4px;bottom:4px;width:36px;height:36px;border-radius:50%;
background:#00AB55;color:#fff;display:inline-flex;align-items:center;justify-content:center;
box-shadow:0 4px 10px rgba(0,171,85,.45);border:3px solid var(--bg);box-sizing:border-box}
.s144 .h2{margin:0;font-family:var(--serif);font-weight:700;letter-spacing:-.015em;
color:var(--fg);font-size:var(--h2s);line-height:var(--h2lh)}
.s144 .herosub{margin:0;font-family:var(--sans);font-weight:500;font-size:15px;line-height:1.45;
color:var(--muted);max-width:var(--subw)}
.s144 .eyebrow{display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:9999px;
background:rgba(254,104,57,.12);color:var(--orange);font-family:var(--sans);font-weight:700;
font-size:11px;letter-spacing:.08em;text-transform:uppercase}
.s144 .eyebrow i{width:5px;height:5px;border-radius:9999px;background:var(--orange)}
.s144 .heroblock{display:flex;flex-direction:column;align-items:center;gap:10px}
.s144 .linkblock{display:flex;flex-direction:column;align-items:center;gap:8px;padding:0 32px}
.s144 .ctarow{display:flex;justify-content:flex-end;margin-bottom:18px;flex:none}
.s144 .next{display:inline-flex;align-items:center;gap:14px;font-family:var(--sans);
font-weight:700;font-size:17px;color:var(--fg)}
.s144 .next span{width:56px;height:56px;border-radius:50%;background:var(--grad-sunset);
display:inline-flex;align-items:center;justify-content:center;box-shadow:var(--shadow-violet)}

/* The conflict modal: bottom-anchored, content-sized, never given a height. */
.s144 .scrim{position:absolute;inset:0;z-index:2;background:rgba(20,12,30,.42);pointer-events:none}
.s144 .sheetwrap{position:absolute;inset:0;z-index:3;display:flex;flex-direction:column;
justify-content:flex-end;pointer-events:none}
.s144 .sheet{margin:8px;border-radius:28px;background:var(--elevated);padding:26px 24px 22px;
box-shadow:0 30px 80px rgba(46,1,71,.28),0 4px 12px rgba(46,1,71,.10)}
.s144 .sheet .shield{width:56px;height:56px;border-radius:50%;background:rgba(254,104,57,.12);
display:inline-flex;align-items:center;justify-content:center;color:var(--orange);margin-bottom:16px}
.s144 .sheet h3{margin:0 0 10px;font-family:var(--serif);font-weight:700;font-size:26px;
letter-spacing:-.015em;line-height:1.15;color:var(--fg)}
.s144 .sheet p.b{margin:0 0 8px;font-family:var(--sans);font-weight:500;font-size:15px;
line-height:1.45;color:var(--neutral)}
.s144 .sheet p.b b{color:var(--fg);font-weight:700}
.s144 .sheet p.rule{margin:0 0 22px;font-family:var(--sans);font-weight:500;font-size:13px;
line-height:1.4;color:var(--muted)}
.s144 .sheet .btn{width:100%;height:54px;margin-bottom:8px}
.s144 .sheet .plain{width:100%;height:50px;border-radius:9999px;background:transparent;border:0;
color:var(--muted);font-family:var(--sans);font-weight:600;font-size:15px}
.s144 .sheet .hi{position:static}
"""

SHORT = {"apple": "Apple", "google": "Google", "facebook": "Facebook"}
CONNECT_ORDER = ["apple", "google", "facebook"]
HEART = ('<svg class="heart" width="30" height="30" viewBox="0 0 24 24">'
         '<path d="M12 21S3.5 15.4 3.5 10C3.5 6.5 8.5 4.7 12 7.2C15.5 4.7 20.5 6.5 20.5 10'
         'C20.5 15.4 12 21 12 21Z" fill="#FE6839"/></svg>')
CHECK = ('<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
         'stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">'
         '<path d="M20 6 9 17l-5-5"/></svg>')
CLOSE = ('<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
         'stroke-width="2.4" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>')
SHIELD = ('<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
          'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'
          '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>')
ARROW_R = ('<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
           'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">'
           '<path d="M5 12h14M12 5l7 7-7 7"/></svg>')


def mark(k, white=False):
    """The provider's own mark. Google's G is never tinted — that is the rule it states loudest."""
    if k == "google":
        return GOOGLE_G
    svg = ico(METHODS[k][1], 18)
    if white:
        svg = svg.replace('stroke="currentColor"', 'stroke="#fff"') \
                 .replace('fill="currentColor"', 'fill="#fff"')
    return svg


def method_btn(k, loading=False, dim=False):
    """A provider button, always in that provider's own livery.

    There is no error variant and no promoted variant: the promotion is carried by POSITION, and
    the failure by the banner above the list. Restyling the button is the one thing all three
    guidelines forbid, and the epic's note puts the guideline above our layout.
    """
    cls = {"apple": "apple", "google": "google", "facebook": "facebook"}[k]
    glyph = ('<span class="spin%s"></span>' % ("" if k != "google" else " dark")) if loading \
        else mark(k, white=(k in ("apple", "facebook")))
    return ('<button class="btn %s%s">%s<span>%s</span></button>'
            % (cls, " off" if dim else "", glyph, METHODS[k][0]))


def skip_row(hot=False):
    return ('<button class="skip%s"><span>Skip and continue to profile</span>%s</button>'
            % (" hot" if hot else "", ARROW_R))


def methods_block(loading=None, suggested=None, banner=None, notice=None):
    order = list(CONNECT_ORDER)
    if suggested:
        order = [suggested] + [k for k in order if k != suggested]
    btns = "".join(method_btn(k, loading=(loading == k), dim=(loading is not None and loading != k))
                   for k in order)
    return "%s%s<div class=\"methods\">%s%s</div>" % (
        banner or "", notice or "", btns, skip_row(hot=loading is not None))


def header(hs):
    return ('<div class="stack">'
            '<h1 class="h1 ul">Welcome to <span class="nb"><em>Show Up</em>%s</span></h1>'
            '<p class="sub">Connect an account for easier future sign-ins.<br>'
            'Or continue and start creating your profile.</p></div>' % HEART)


LEGAL = ('<p class="legal">By continuing you agree to our <b>Terms</b> and '
         '<b>Privacy Policy</b>.</p>')


def frame(w, inner, extra="", wordmarkGap=True):
    sb, hi = chrome(w[5], w[6])
    compact = w[1] < 700
    return ('<div class="screen s144" style="--w:%dpx;--h:%dpx;--st:%dpx;--sbm:%dpx;--r:%dpx;'
            '--wgap:%dpx;--hs:%dpx">%s%s<div class="body">%s</div>%s%s</div>'
            % (w[0], w[1], w[2], w[3], w[4], 44 if compact else 96, 34 if compact else 40,
               backdrop(), sb,
               WORDMARK + ('<div class="wgap"></div>' if wordmarkGap else '') + inner, hi, extra))


def list_state(w, loading=None, suggested=None, banner=None, notice=None, extra=""):
    return frame(w, '%s<div class="gap"></div>%s%s'
                 % (header(w), methods_block(loading, suggested, banner, notice), LEGAL), extra)


def banner_html(msg):
    return '<div class="banner"><i>!</i><p>%s</p></div>' % msg


def notice_html(provider):
    return ('<div class="notice"><i>%s</i><p><b>Sign-in cancelled.</b> '
            '<span>You closed the %s sheet before we could finish.</span></p></div>'
            % (CLOSE, SHORT[provider]))


def linking(w, provider="apple"):
    ring = ('<div class="ringbox"><svg class="ring" width="120" height="120" viewBox="0 0 120 120">'
            '<defs><linearGradient id="sa-ring" x1="0" y1="0" x2="1" y2="1">'
            '<stop offset="0%%" stop-color="#FE6839"/><stop offset="100%%" stop-color="#812AEC"/>'
            '</linearGradient></defs>'
            '<circle cx="60" cy="60" r="52" stroke="rgba(29,17,41,0.06)" stroke-width="4" fill="none"/>'
            '<circle class="arc" cx="60" cy="60" r="52" stroke="url(#sa-ring)" stroke-width="4" '
            'fill="none" stroke-dasharray="91.5 326.7" stroke-linecap="round"/></svg>'
            '<div class="puck">%s</div></div>'
            % mark(provider).replace('width="18" height="18"', 'width="42" height="42"'))
    subj = "Apple ID" if provider == "apple" else (
        "Google account" if provider == "google" else "Facebook account")
    body = ('%s<div class="linkblock"><h2 class="h2 ul">Signing you <em>in</em>&#8230;</h2>'
            '<p class="herosub">Verifying your %s and setting things up. This takes a second.</p>'
            '</div>' % (ring, subj))
    return frame(w, '<div class="hero" style="--herogap:24px;--h2s:28px;--h2lh:1.15;--subw:none">%s</div>'
                    '<p class="legal">Don&#8217;t close the app.</p>' % body, wordmarkGap=False)


def success(w, provider="apple", name="Leo"):
    head = ("You&#8217;re <em>in</em>, %s." % name) if name else "You&#8217;re <em>in</em>."
    puck = ('<div class="ringbox"><div class="halo"></div><div class="puck ok">%s</div>'
            '<div class="badge">%s</div></div>'
            % (mark(provider).replace('width="18" height="18"', 'width="44" height="44"'), CHECK))
    body = ('%s<div class="heroblock"><span class="eyebrow"><i></i>%s connected</span>'
            '<h2 class="h2 ul">%s</h2>'
            '<p class="herosub">We&#8217;ll never post or message anyone on your behalf. '
            'Let&#8217;s finish your profile in 90 seconds.</p></div>'
            % (puck, SHORT[provider], head))
    cta = ('<div class="ctarow"><span class="next">Continue<span>'
           '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" '
           'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'
           '<path d="M5 12h14M12 5l7 7-7 7"/></svg></span></span></div>')
    return frame(w, '<div class="hero" style="--herogap:28px;--h2s:32px;--h2lh:1.1;--subw:280px">%s</div>%s'
                 % (body, cta), wordmarkGap=False)


def conflict(w, email="leo@gmail.com", owner="google"):
    who = SHORT[owner]
    if email:
        line = ('<b>%s</b> is already on Show Up &#8212; you signed up with %s. '
                'Continue with %s to pick up where you left off.' % (email, who, who))
    else:
        line = ('That account is already on Show Up &#8212; you signed up with %s. '
                'Continue with %s to pick up where you left off.' % (who, who))
    cls = {"apple": "apple", "google": "google", "facebook": "facebook"}[owner]
    sheet = ('<div class="scrim"></div><div class="sheetwrap"><div class="sheet">'
             '<div class="shield">%s</div>'
             '<h3>You already have an account.</h3>'
             '<p class="b">%s</p>'
             '<p class="rule">One person, one account. Show-up Rates only work if you '
             'can&#8217;t start over.</p>'
             '<button class="btn %s">%s<span>Continue with %s</span></button>'
             '<button class="plain">Use a different account</button>'
             '</div><div class="hi">%s</div></div>'
             % (SHIELD, line, cls, mark(owner, white=(owner in ("apple", "facebook"))), who,
                '<i></i>' if w[6] else ""))
    # The list underneath is the IDLE list, in canonical order. The conflict must not reorder it.
    return list_state(w, extra=sheet)


# ── page ────────────────────────────────────────────────────────────────────
D375, D390, D430 = DEVICES

html = ['<meta charset="utf-8"><title>ShowUp - Connect account</title>',
        '<style>%s%s%s</style>' % (FACES, CSS, CSS144),
        '<div class="page">',
        '<header class="hd"><h1>Connect an account</h1>',
        '<p class="lede">SHOWUP-144 and SHOWUP-145. Ten states out of one component, plus the '
        'shared method list that both screens use. Rendered from the same numbers the app is '
        'built from.</p>',
        '<p class="lede"><b>Provider buttons are built to Apple&#8217;s, Google&#8217;s and '
        'Meta&#8217;s own published specs, at equal prominence.</b> No gradient on a provider '
        'button in any state, no re-tinted mark, and the permitted title text in every state - '
        'which is why the tapped one is signalled by position and a spinner rather than by a '
        'change of fill, and why a failed one keeps its own livery with the message in the banner '
        'above.</p>',
        '</header>']

SECTIONS = [
    ("A &#8212; idle", "three connect buttons and the skip &#183; canonical order &#183; no phone",
     lambda d: list_state(d)),
    ("B &#8212; tapped, in flight", "Apple promoted to first &#183; spinner &#183; siblings at .45 "
     "&#183; the skip is live and DARKER, never dimmed",
     lambda d: list_state(d, loading="apple", suggested="apple")),
    ("E &#8212; linking", "capped at 8s, falls out to error &#183; no exit by design",
     lambda d: linking(d)),
    ("F &#8212; success", "eyebrow &#183; the privacy promise in our voice, after the sheet is gone",
     lambda d: success(d)),
    ("F &#8212; success, no name returned", "falls back to &#8220;You&#8217;re in.&#8221; &#183; "
     "never &#8220;You&#8217;re in, null.&#8221;",
     lambda d: success(d, name="")),
    ("G &#8212; cancelled", "neutral, bg-raised &#183; no danger colour &#183; Apple still first",
     lambda d: list_state(d, suggested="apple", notice=notice_html("apple"))),
    ("H &#8212; error, network", "danger banner &#183; the button keeps its own livery &#183; "
     "the other two providers and the skip stay live",
     lambda d: list_state(d, suggested="apple",
                          banner=banner_html("We couldn&#8217;t reach Apple. Check your "
                                             "connection and try again."))),
    ("H &#8212; error, declined", "Google tapped &#183; same layout, different string",
     lambda d: list_state(d, suggested="google",
                          banner=banner_html("Google didn&#8217;t return a valid sign-in. "
                                             "Try again, or use a different method."))),
    ("I &#8212; conflict", "content-sized, bottom-anchored &#183; names the OWNING provider "
     "&#183; scrim is not tappable &#183; no close icon &#183; the list behind is unchanged",
     lambda d: conflict(d)),
]

for title, note, fn in SECTIONS:
    html.append('<section><h2 class="title">%s</h2><p class="meta">%s</p><div class="rail">' % (title, note))
    for d in DEVICES:
        html.append('<figure>%s<figcaption>%d &#215; %d &#183; %s</figcaption></figure>'
                    % (fn(d), d[0], d[1], d[7]))
    html.append('</div></section>')

# The cases that only matter once, at the size that stresses them.
html.append('<section><h2 class="title">Conflict &#8212; the two content cases</h2>'
            '<p class="meta">a 38-character address grows the sheet upward with no clipping, no '
            'internal scroll and no truncation &#183; with no address the sentence changes shape '
            'rather than rendering an empty bold span</p><div class="rail">')
html.append('<figure>%s<figcaption>375 &#215; 667 &#183; 38-character address</figcaption></figure>'
            % conflict(D375, email="leonhard.schwarzkopf@studio-mantis.com"))
html.append('<figure>%s<figcaption>390 &#215; 844 &#183; no address available</figcaption></figure>'
            % conflict(D390, email=None))
html.append('<figure>%s<figcaption>390 &#215; 844 &#183; tapped Apple, account owned by Apple'
            '</figcaption></figure>' % conflict(D390, owner="apple"))
html.append('</div></section>')

html.append('<section><h2 class="title">The provider sub-tasks are not all done</h2>'
            '<p class="meta">a provider whose credential work is unfinished ships HIDDEN, not '
            'disabled &#8212; a dead button is worse than an absent one, and the stack still fills '
            'without a gap</p><div class="rail">')
_saved = list(CONNECT_ORDER)
CONNECT_ORDER[:] = ["apple", "google"]
html.append('<figure>%s<figcaption>390 &#215; 844 &#183; Facebook cut from v1</figcaption></figure>'
            % list_state(D390))
CONNECT_ORDER[:] = ["apple"]
html.append('<figure>%s<figcaption>390 &#215; 844 &#183; Apple only</figcaption></figure>'
            % list_state(D390))
CONNECT_ORDER[:] = _saved
html.append('</div></section>')

html.append('</div>')

# Re-centre each hero mark on its own ink.
#
# The provider logos are authored to sit optically correct BESIDE a text label, which leaves them a
# little high in their own 24-unit artboard -- Apple's occupies y 3.8..16.5, so its centre is ~1.8
# units above the box centre. Against a label that is invisible; alone inside a 120px ring it is
# not. The apps do the same thing from the path bounds (BrandIconView.inkOffset / Icon's
# opticalCentre); here the browser measures it, so there are no per-icon constants to drift.
html.append("<script>")
html.append("for (const svg of document.querySelectorAll('.puck svg')) {")
html.append("  const ps = [...svg.querySelectorAll('path')];")
html.append("  if (!ps.length) continue;")
html.append("  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;")
html.append("  for (const p of ps) {")
html.append("    const b = p.getBBox();")
html.append("    x0 = Math.min(x0, b.x); y0 = Math.min(y0, b.y);")
html.append("    x1 = Math.max(x1, b.x + b.width); y1 = Math.max(y1, b.y + b.height);")
html.append("  }")
html.append("  const dx = (12 - (x0 + x1) / 2).toFixed(2);")
html.append("  const dy = (12 - (y0 + y1) / 2).toFixed(2);")
html.append("  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');")
html.append("  g.setAttribute('transform', 'translate(' + dx + ',' + dy + ')');")
html.append("  while (svg.firstChild) { g.appendChild(svg.firstChild); }")
html.append("  svg.appendChild(g);")
html.append("}")
html.append("</script>")

io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(html))
print("wrote: %s" % OUT)
print("size : %.1f MB" % (os.path.getsize(OUT) / 1024.0 / 1024.0))
