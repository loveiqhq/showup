# -*- coding: utf-8 -*-
"""Welcome & sign-up flow (SHOWUP-140 / 142 / 143) — layout sheet.

Values come from, in the handoff's own order of authority:
  1. welcome/screen-startup-reference.jsx, welcome/screen-login-reference.jsx
  2. the Jira tickets (behaviour, scope, copy)
  3. the spec-sheet PNGs (nothing; summary only)

Screen 03 has no reference .jsx -- it is not yet ticketed in the handoff -- so its numbers come
from SHOWUP-143's acceptance criteria and the annotated sheet, which is the only source there is.
"""
import base64
import io
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = r"C:\Users\krnji\showup\mobile\welcome-screen"
FONTS = os.path.join(REPO, "fonts")
DESKTOP = os.environ.get("SHOWUP_DESKTOP") or os.path.join(os.environ["USERPROFILE"], "Desktop")
OUT = os.path.join(DESKTOP, "ShowUp - welcome & sign-up.html")


def face(family, weight, style, filename):
    with open(os.path.join(FONTS, filename), "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    return ('@font-face{font-family:"%s";font-weight:%s;font-style:%s;'
            'src:url(data:font/ttf;base64,%s) format("truetype");}' % (family, weight, style, b64))


FACES = "".join([
    face("SULora", 400, "normal", "Lora-Regular.ttf"),
    face("SULora", 500, "normal", "Lora-Regular.ttf"),
    # weight 500 italic — the weight the token file specifies for the emphasised word
    face("SULora", 500, "italic", "Lora-MediumItalic.ttf"),
    face("SULora", 700, "normal", "Lora-Bold.ttf"),
    face("SULora", 400, "italic", "Lora-Italic.ttf"),
    face("SULora", 700, "italic", "Lora-BoldItalic.ttf"),
    face("SUManrope", 500, "normal", "Manrope-Medium.ttf"),
    face("SUManrope", 600, "normal", "Manrope-SemiBold.ttf"),
    face("SUManrope", 700, "normal", "Manrope-Bold.ttf"),
])

# ── tokens, verbatim from tokens/colors_and_type.css ────────────────────────
CSS = """
:root{--page-bg:#F4F2F7;--page-fg:#1D1129;--page-mute:#6B6377;--page-rule:#DFDAE6;}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}}
:root[data-theme="dark"]{--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}
:root{
--bg:#FFFBF7;--elevated:#FFFFFF;--fg:#1D1129;
--muted:rgba(29,17,41,.62);--subtle:rgba(29,17,41,.46);--faint:rgba(29,17,41,.24);
--border:rgba(29,17,41,.12);
/* Field outlines take ink 46%, not the 12% border token. WCAG 1.4.11 wants 3:1 for the
   boundary that identifies a control, and the white fill is 1.03:1 against the canvas —
   the outline is doing all the work. 12% is 1.28:1; 46% is 3.04 against the page and
   3.14 against the fill. */
--field-outline:rgba(29,17,41,.46);
--primary:#812AEC;--lavender:#A78BFA;--orange:#FE6839;--neutral200:#4B3B5A;
--danger:#FB323B;--danger-fg:#B71F26;
--grad-sunset:linear-gradient(135deg,#FE6839 0%,#D05976 38%,#812AEC 100%);
--grad-wordmark:linear-gradient(96deg,#812AEC 0%,#D05976 55%,#FE6839 100%);
--shadow-cta:0 8px 20px rgba(254,104,57,.32),0 2px 6px rgba(254,104,57,.20);
--shadow-violet:0 8px 20px rgba(129,42,236,.28),0 2px 6px rgba(129,42,236,.18);
--serif:"SULora",Georgia,serif;--sans:"SUManrope",-apple-system,"Segoe UI",sans-serif;
--ease:cubic-bezier(.22,1,.36,1);
}
*{box-sizing:border-box}
body{margin:0;padding:0 24px 72px;background:var(--page-bg);color:var(--page-fg);font-family:var(--sans);-webkit-font-smoothing:antialiased}
.page{max-width:1680px;margin:0 auto;display:flex;flex-direction:column;gap:2.5rem}
header{padding-top:3.25rem;display:flex;flex-direction:column;gap:.5rem}
.kicker{margin:0;font-size:.7rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--orange)}
h1{margin:0;font-family:var(--serif);font-weight:700;font-size:clamp(1.9rem,4vw,2.6rem);line-height:1.1;letter-spacing:-.02em}
.lede{margin:0;color:var(--page-mute);max-width:70ch;line-height:1.6}
section{display:flex;flex-direction:column;gap:.9rem;padding-top:1.6rem;border-top:1px solid var(--page-rule)}
.title{margin:0;font-family:var(--serif);font-size:1.35rem;font-weight:700;letter-spacing:-.01em}
.meta{margin:0;font-size:.84rem;color:var(--page-mute)}
.rail{display:flex;flex-wrap:wrap;gap:2rem;align-items:flex-start}
figure{margin:0;display:flex;flex-direction:column;gap:.6rem}
figcaption{font-size:.76rem;color:var(--page-mute);font-variant-numeric:tabular-nums}

/* ── phone frame ─────────────────────────────────────────────────────── */
.screen{position:relative;width:var(--w);height:var(--h);flex:none;overflow:hidden;background:var(--bg);
border-radius:var(--r,42px);box-shadow:0 0 0 1px var(--page-rule),0 20px 46px -18px rgba(29,17,41,.4);
isolation:isolate;display:flex;flex-direction:column}
/* backdrop: full-bleed at z0, BEHIND the status bar and home indicator */
.orb{position:absolute;pointer-events:none;z-index:0;filter:blur(10px)}
.wash{position:absolute;top:0;left:0;right:0;height:360px;pointer-events:none;z-index:0;
background:linear-gradient(180deg,rgba(255,229,210,.55) 0%,rgba(255,251,247,0) 70%)}
.sb{height:var(--st);flex:none;display:flex;align-items:center;justify-content:space-between;
padding:0 30px;position:relative;z-index:5;font-family:-apple-system,"Segoe UI",system-ui;font-weight:600;font-size:17px;color:#1D1129}
.sb.sm{padding:0 18px;font-size:14px}
.sb .ic{display:flex;gap:6px;align-items:center}
.hi{height:var(--sbm);flex:none;display:flex;align-items:center;justify-content:center;position:relative;z-index:5}
.hi i{width:134px;height:5px;border-radius:999px;background:#1D1129;opacity:.85;display:block}
.body{flex:1;position:relative;z-index:1;display:flex;flex-direction:column;overflow:hidden;padding:20px 24px 0}
.gap{flex:1 1 0;min-height:0}

/* ── wordmark ────────────────────────────────────────────────────────── */
.wm{font-family:var(--serif);font-weight:700;letter-spacing:-.02em;display:inline-flex;
align-items:baseline;gap:.18em;line-height:1;font-size:26px;flex:none}
.wm .up{background:var(--grad-wordmark);-webkit-background-clip:text;background-clip:text;
color:transparent;font-style:italic}
.wm .dot{color:var(--orange);font-family:var(--serif);font-style:normal}

/* ── the signature underline wash (tokens/colors_and_type.css) ───────── */
.ul em,.ul .it{font-style:italic;font-weight:500;position:relative;white-space:nowrap;isolation:isolate}
.ul em::after,.ul .it::after{content:"";position:absolute;left:-2px;right:-2px;bottom:-.08em;
height:.32em;z-index:-1;
background:radial-gradient(ellipse at 50% 100%,rgba(254,104,57,.55) 0%,rgba(254,104,57,0) 70%)}

/* ── buttons ─────────────────────────────────────────────────────────── */
.btn{height:56px;padding:0 28px;border-radius:9999px;display:inline-flex;align-items:center;
justify-content:center;gap:8px;font-family:var(--sans);font-weight:700;font-size:16px;
white-space:nowrap;width:100%;border:0;transition:transform 180ms var(--ease),filter 180ms;flex:none}
.btn.sunset{background:var(--grad-sunset);color:#fff;box-shadow:var(--shadow-violet)}
.btn.ghost{background:transparent;color:var(--fg);border:1px solid var(--border)}
/* Each provider dictates its own button. Google forbids recolouring or resizing the G and
   requires a white background; Meta requires its mark in white or #1877F2. The uniform
   ghost row the design specified breaks both. */
.btn.apple{background:#000;color:#fff}
.btn.google{background:#fff;color:#1F1F1F;border:1px solid #747775}
.btn.facebook{background:#1877F2;color:#fff}
.btn.off{opacity:.45}
.btn svg{flex:none}

/* ── shared type ─────────────────────────────────────────────────────── */
.h1{margin:0;font-family:var(--serif);font-weight:700;letter-spacing:-.02em;line-height:1.05;
color:var(--fg);text-wrap:balance;flex:none}
.legal{margin:0;font-size:12px;color:var(--muted);text-align:center;line-height:1.45;flex:none}
.legal b{color:var(--fg);font-weight:600}
.eyebrow{flex:none;align-self:flex-start;display:inline-flex;align-items:center;gap:6px;
padding:5px 10px;border-radius:9999px;background:rgba(167,139,250,.16);font-weight:700;font-size:11px;
line-height:1.2;letter-spacing:.08em;text-transform:uppercase;color:var(--primary)}
.eyebrow i{width:5px;height:5px;border-radius:50%;background:var(--orange);flex:none}
"""

# ── 140 Startup ─────────────────────────────────────────────────────────────
CSS_140 = """
.s140 .stack{margin-top:var(--wgap,132px);display:flex;flex-direction:column;gap:18px;flex:none}
.s140 .h1{font-size:44px}
.s140 .l1{display:block;font-size:32px;font-weight:700;letter-spacing:-.015em;margin-bottom:4px}
.s140 .sub{margin:0;font-family:var(--sans);font-weight:600;font-size:19px;line-height:1.4;
color:var(--fg);max-width:320px;text-wrap:pretty;flex:none}
.s140 .proof{display:flex;align-items:center;justify-content:center;gap:8px;margin-bottom:14px;flex:none}
.s140 .proof span{font-family:var(--sans);font-size:13px;font-weight:500;color:var(--muted);letter-spacing:.01em}
.s140 .proof b{color:var(--fg);font-weight:700}
.s140 .legal{margin-bottom:14px}
.s140 .cta{margin-bottom:10px;flex:none}
.s140 .login{display:flex;justify-content:center;margin-bottom:14px;flex:none}
.s140 .login button{background:none;border:0;padding:6px 8px;font-family:var(--sans);font-size:14px;
font-weight:600;color:var(--muted);letter-spacing:.01em;min-height:44px}
.s140 .login u{color:var(--primary);text-decoration:underline;text-underline-offset:3px;text-decoration-thickness:1.5px}
"""

# ── 142 Welcome back ────────────────────────────────────────────────────────
CSS_142 = """
.s142 .stack{margin-top:var(--wgap,120px);display:flex;flex-direction:column;gap:18px;flex:none}
.s142 .h1{font-size:44px}
.s142 .sub{margin:0;font-family:var(--sans);font-weight:500;font-size:17px;line-height:1.45;
color:var(--neutral200);max-width:280px;text-wrap:pretty;flex:none}
.s142 .stackbtn{display:flex;flex-direction:column;gap:10px;margin-bottom:12px;flex:none}
.s142 .hint{display:flex;align-items:center;justify-content:center;gap:6px;margin-bottom:2px}
.s142 .hint i{width:6px;height:6px;border-radius:50%;background:var(--orange);
box-shadow:0 0 0 3px rgba(254,104,57,.18);flex:none}
.s142 .hint span{font-family:var(--sans);font-size:12px;font-weight:600;color:var(--muted);letter-spacing:.01em}
.s142 .help{margin:0 0 8px}
.s142 .legal2{margin:0 0 8px;font-size:11.5px;color:var(--muted);text-align:center;line-height:1.45;flex:none}
.s142 .legal2 b{color:var(--muted);font-weight:600;text-decoration:underline;text-underline-offset:2px}
"""

# ── 143 Phone verification ──────────────────────────────────────────────────
CSS_143 = """
.s143{--kbd:214px}
.s143 .body{padding:4px 24px 0}
.s143 .hdr{height:44px;flex:none;display:flex;align-items:center;position:relative;z-index:2;padding:0 24px}
.s143 .hdr svg{color:var(--fg)}
.s143 .h1{font-size:38px;margin:14px 0 10px}
.s143 .sub{margin:0;font-family:var(--sans);font-weight:500;font-size:15px;line-height:1.45;
color:var(--neutral200);max-width:320px;flex:none}
.s143 .sub b{font-weight:700;color:var(--fg);white-space:nowrap}
.s143 .row{display:flex;gap:8px;margin-top:22px;flex:none}
.s143 .cc{height:56px;border-radius:14px;background:#fff;border:1.5px solid var(--field-outline);
padding:0 14px;display:flex;align-items:center;gap:8px;flex:none}
.s143 .cc .flag{width:22px;height:14px;border-radius:2px;overflow:hidden;display:flex;flex:none;
box-shadow:0 0 0 .5px rgba(29,17,41,.35)}
.s143 .cc .flag b{flex:1}
.s143 .cc .code{font-family:var(--sans);font-weight:600;font-size:16px;color:var(--fg)}
.s143 .inp{flex:1;height:56px;border-radius:14px;background:#fff;border:1.5px solid var(--field-outline);
padding:0 18px;display:flex;align-items:center;font-family:var(--sans);font-weight:600;font-size:17px;
font-variant-numeric:tabular-nums;letter-spacing:.3px;color:var(--fg);overflow:hidden;white-space:nowrap;
transition:border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.s143 .inp.err{border-color:var(--danger);box-shadow:0 0 0 4px rgba(251,50,59,.10)}
.s143 .inp .ph{color:var(--faint)}
.s143 .inp .caret{width:2px;height:20px;background:var(--primary);margin-left:1px;flex:none}
.s143 .inp .bang{width:22px;height:22px;border-radius:50%;background:var(--danger);color:#fff;
font-family:var(--serif);font-weight:700;font-size:14px;display:flex;align-items:center;
justify-content:center;flex:none;margin-left:auto}
/* helper regions are RESERVED, never conditional -- the CTA must not move */
.s143 .help20{min-height:20px;margin-top:10px;padding-left:4px;font-family:var(--sans);font-weight:500;
font-size:13px;line-height:1.35;color:var(--muted);flex:none}
.s143 .help20.err{font-weight:600;color:var(--danger-fg)}
/* The sheet reserves 42 here. The error string it specifies wraps to two lines at every one
   of the three widths, making the box 59 -- so a 42 reserve lets the CTA jump 17px, which
   ticket SHOWUP-143 forbids outright: "The CTA does not move between the default and error
   state on either screen." The ticket wins on behaviour, so the region is reserved at the
   height the specified copy actually needs. Design to confirm whether the 42 or the string
   is the one to change. */
.s143 .help42{min-height:42px;margin-top:14px;padding-left:2px;flex:none}
.s143 .cta{margin-top:22px;flex:none}
.s143 .cta.c{margin-top:16px}
/* 375 x 667: the keyboard leaves the least room of the three frames. The sheet fixes the order in
   which things give -- eyebrow->headline margin first, then the slot row 49x62 -> 44x56 gap 6.
   The CTA never drops below 56 and neither helper region is ever hidden. */
.s143.tight .h1{margin:2px 0 4px}
.s143.tight .slots{margin-top:8px;gap:6px}
.s143.tight .cta.c{margin-top:8px}
.s143.tight .sec{margin-top:8px;gap:4px}
.s143.tight .help42{margin-top:6px}
.s143.tight .row{margin-top:12px}
.s143.tight .cta{margin-top:12px}
/* code slots */
.s143 .slots{display:flex;gap:8px;justify-content:space-between;margin-top:26px;flex:none}
.s143 .slot{width:var(--sw,49px);height:var(--sh,62px);border-radius:14px;background:#fff;
border:1.5px solid var(--field-outline);display:flex;align-items:center;justify-content:center;
font-family:var(--serif);font-weight:700;font-size:30px;color:var(--fg);font-variant-numeric:tabular-nums;
box-shadow:0 1px 2px rgba(46,1,71,.04);transition:border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.s143 .slot.filled{border-color:rgba(29,17,41,.32)}
.s143 .slot.active{border-color:var(--primary);box-shadow:0 0 0 4px rgba(129,42,236,.10)}
.s143 .slot.active .caret{width:2px;height:28px;background:var(--primary);display:block}
.s143 .slots.bad .slot{border-color:var(--danger);box-shadow:0 0 0 4px rgba(251,50,59,.10);
background:rgba(251,50,59,.04);color:#7A1F26}
.s143 .errbox{display:flex;align-items:flex-start;gap:10px;padding:10px 14px;border-radius:12px;
background:rgba(251,50,59,.07);border:1px solid rgba(251,50,59,.18)}
.s143 .errbox i{width:18px;height:18px;border-radius:50%;background:var(--danger);color:#fff;
font-family:var(--serif);font-weight:700;font-size:12px;display:flex;align-items:center;
justify-content:center;flex:none}
.s143 .errbox span{font-family:var(--sans);font-weight:500;font-size:13.5px;line-height:1.4;color:var(--danger-fg)}
/* secondary actions */
.s143 .sec{margin-top:14px;display:flex;flex-direction:column;gap:6px;align-items:center;flex:none}
.s143 .sec .q{font-family:var(--sans);font-weight:500;font-size:14px;color:var(--muted)}
.s143 .sec .cool{font-family:var(--sans);font-weight:600;font-size:14px;color:var(--muted);font-variant-numeric:tabular-nums}
.s143 .sec .live{font-family:var(--sans);font-weight:700;font-size:14px;color:var(--primary);
text-decoration:underline;text-underline-offset:3px}
.s143 .sec .edit{display:flex;align-items:center;gap:6px;font-family:var(--sans);font-weight:600;
font-size:14px;color:var(--muted);padding:4px 8px}
/* keypad -- a MOCK of the system keyboard, drawn only so the artboard shows the true
   content height. The app ships the platform keypad; this component is never built. */
.s143 .kbd{flex:none;background:#D1CDC8;padding:8px 3px 4px;position:relative;z-index:2}
.s143 .kbd .r{display:flex;gap:6px;margin-bottom:6px}
.s143 .kbd .k{flex:1;height:46px;border-radius:6px;background:#fff;box-shadow:0 1px 0 rgba(29,17,41,.18);
display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px}
.s143 .kbd .k.blank{background:transparent;box-shadow:none}
.s143 .kbd .k b{font-family:-apple-system,"Segoe UI",system-ui;font-weight:400;font-size:24px;color:#1D1129;line-height:1}
.s143 .kbd .k s{font-family:-apple-system,"Segoe UI",system-ui;font-weight:600;font-size:9px;
letter-spacing:1.4px;color:var(--muted);text-decoration:none;line-height:1}
"""

ICONS_SB = ('<svg width="18" height="11" viewBox="0 0 18 11"><rect y="6" width="3" height="4" rx=".6" fill="#1D1129"/>'
            '<rect x="5" y="4" width="3" height="6" rx=".6" fill="#1D1129"/><rect x="10" y="2" width="3" height="8" rx=".6" fill="#1D1129"/>'
            '<rect x="15" width="3" height="10" rx=".6" fill="#1D1129"/></svg>'
            '<svg width="16" height="11" viewBox="0 0 16 11"><path d="M8 3.5C9.9 3.5 11.6 4.2 12.9 5.4L14 4.3C12.4 2.8 10.3 1.8 8 1.8C5.7 1.8 3.6 2.8 2 4.3L3.1 5.4C4.4 4.2 6.1 3.5 8 3.5Z" fill="#1D1129"/>'
            '<path d="M8 6.5C8.9 6.5 9.8 6.8 10.4 7.4L11.5 6.3C10.5 5.4 9.3 4.9 8 4.9C6.7 4.9 5.5 5.4 4.5 6.3L5.6 7.4C6.2 6.8 7.1 6.5 8 6.5Z" fill="#1D1129"/>'
            '<circle cx="8" cy="9.5" r="1" fill="#1D1129"/></svg>'
            '<svg width="25" height="12" viewBox="0 0 25 12"><rect x=".5" y=".5" width="21" height="11" rx="3" stroke="#1D1129" stroke-opacity=".4" fill="none"/>'
            '<rect x="2" y="2" width="18" height="8" rx="1.5" fill="#1D1129"/>'
            '<path d="M23 4v4c.6-.2 1.2-.9 1.2-2 0-1.1-.6-1.8-1.2-2z" fill="#1D1129" fill-opacity=".4"/></svg>')

# Lucide geometry, 24x24, stroke 1.7, currentColor -- verbatim from components/shared.jsx
def ico(name, size=20, stroke=1.7):
    P = {
        "calendar": '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/>'
                    '<line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>',
        "phone": '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.37 1.9.72 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.35 1.85.59 2.81.72A2 2 0 0 1 22 16.92z"/>',
        "apple": '<path d="M16 4c.5 1.5-.5 3-2 3.5C12 8 11 7 11 5.5 12.5 4 14 3.5 16 4zM18.4 13.5c-.6 1.5-1.4 3-2.9 3-1.4 0-1.9-.8-3.5-.8-1.6 0-2.1.8-3.5.8-1.5 0-2.4-1.4-3.1-2.9C4 11 4.6 7.6 6.6 6.5c1.3-.7 2.5-.3 3.5 0 1 .3 1.4.3 2.4 0 1.1-.4 2.2-.9 3.6-.2-1.7 1.1-2.1 3.5-.4 4.8.5 1.1.8 1.6.7 2.4z" fill="currentColor"/>',
        "google": '<path d="M22 12.2c0-.8-.1-1.4-.2-2H12v3.9h5.6c-.2 1.3-1 2.3-2 3v2.5h3.3c1.9-1.8 3.1-4.4 3.1-7.4zM12 22c2.7 0 5-.9 6.7-2.4l-3.3-2.5c-.9.6-2 1-3.4 1-2.6 0-4.9-1.8-5.7-4.2H2.9v2.6C4.6 19.9 8 22 12 22zM6.3 13.9c-.2-.6-.3-1.3-.3-1.9s.1-1.3.3-1.9V7.5H2.9C2.3 8.9 2 10.4 2 12s.3 3.1.9 4.5l3.4-2.6zM12 5.9c1.5 0 2.8.5 3.9 1.5l2.9-2.9C17 2.9 14.7 2 12 2 8 2 4.6 4.1 2.9 7.5l3.4 2.6C7.1 7.7 9.4 5.9 12 5.9z" fill="currentColor"/>',
        "facebook": '<path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.51 1.49-3.9 3.78-3.9 1.1 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.45 2.89h-2.34v6.99A10 10 0 0 0 22 12z" fill="currentColor"/>',
        "chevron-down": '<path d="m6 9 6 6 6-6"/>',
        "arrow-left": '<path d="M19 12H5M12 19l-7-7 7-7"/>',
        "edit": '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"/>',
    }[name]
    fill = "" if "fill=" in P else ' fill="none"'
    return ('<svg width="%d" height="%d" viewBox="0 0 24 24"%s stroke="currentColor" stroke-width="%s" '
            'stroke-linecap="round" stroke-linejoin="round" style="display:block;flex:none">%s</svg>'
            % (size, size, fill, stroke, P))


GOOGLE_G = '<svg width="18" height="18" viewBox="0 0 24 24" style="display:block;flex:none"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>'

WORDMARK = '<div class="wm">Show<span class="up">Up</span><span class="dot">.</span></div>'

# 375x667 has no notch and no home indicator; 390 and 430 do.
DEVICES = [(375, 667, 20, 0, 34, True, False, "iPhone SE"),
           (390, 844, 54, 28, 42, False, True, "reference frame"),
           (430, 932, 54, 28, 48, False, True, "iPhone Pro Max")]


def chrome(sm, home):
    sb = ('<div class="sb%s"><span>4:20</span><span class="ic">%s</span></div>'
          % (" sm" if sm else "", ICONS_SB))
    hi = '<div class="hi">%s</div>' % ('<i></i>' if home else "")
    return sb, hi


def backdrop(peach=True, orange=".32", violet=".28"):
    """Three layers on Startup / Welcome back; two (no peach) on phone verification."""
    o = ('<div class="orb" style="top:-15%%;right:-25%%;width:520px;height:520px;'
         'background:radial-gradient(circle,rgba(254,104,57,%s) 0%%,rgba(254,104,57,0) 65%%)"></div>' % orange)
    v = ('<div class="orb" style="bottom:-20%%;left:-30%%;width:600px;height:600px;'
         'background:radial-gradient(circle,rgba(129,42,236,%s) 0%%,rgba(129,42,236,0) 65%%)"></div>' % violet)
    return o + v + ('<div class="wash"></div>' if peach else "")


def backdrop143(dim=False):
    """Two layers, top-weighted. C and D drop to .22 / .20 -- the code screen is calmer."""
    a, b = (".22", ".20") if dim else (".26", ".22")
    return ('<div class="orb" style="top:-18%%;right:-30%%;width:460px;height:460px;'
            'background:radial-gradient(circle,rgba(254,104,57,%s) 0%%,rgba(254,104,57,0) 65%%)"></div>'
            '<div class="orb" style="top:-10%%;left:-25%%;width:420px;height:420px;'
            'background:radial-gradient(circle,rgba(129,42,236,%s) 0%%,rgba(129,42,236,0) 65%%)"></div>' % (a, b))


# ── screen 140 ──────────────────────────────────────────────────────────────
def startup(w, sm, home, wgap):
    sb, hi = chrome(sm, home)
    return ('<div class="screen s140" style="--w:%dpx;--h:%dpx;--st:%dpx;--sbm:%dpx;--r:%dpx;--wgap:%dpx">'
            '%s%s<div class="body">%s'
            '<div class="stack">'
            '<h1 class="h1 ul"><span class="l1">Stop texting for days.</span>Start <em>meeting</em> today.</h1>'
            '<p class="sub">Your availability. Your intent. Your date &#8212; today or tomorrow.</p>'
            '</div>'
            '<div class="gap"></div>'
            '<div class="proof"><span style="color:var(--orange)">%s</span>'
            '<span><b>234.000 Dates</b> already organized</span></div>'
            '<div class="gap"></div>'
            '<p class="legal">By creating an account, you agree to our <b>Terms &amp; Conditions</b> and '
            'acknowledge that you have read our <b>Privacy Policy</b>. See our <b>Legal Notice</b>.</p>'
            '<div class="cta"><button class="btn sunset">Create free account</button></div>'
            '<div class="login"><button>Already have an account? <u>Log in</u></button></div>'
            '</div>%s</div>'
            % (w[0], w[1], w[2], w[3], w[4], wgap, backdrop(), sb, WORDMARK, ico("calendar", 16, 2), hi))


# ── screen 142 ──────────────────────────────────────────────────────────────
CANONICAL = ["phone", "apple", "google", "facebook"]
METHODS = {
    "phone":    ("Continue with phone number", "phone",    "Last login was via phone"),
    "apple":    ("Continue with Apple",        "apple",    "Last login was via Apple"),
    "google":   ("Continue with Google",       "google",   "Last login was via Google"),
    "facebook": ("Continue with Facebook",     "facebook", "Last login was via Facebook"),
}


def welcomeback(w, sm, home, wgap, last_used="phone", name="Leo"):
    sb, hi = chrome(sm, home)
    known = last_used in CANONICAL
    primary = last_used if known else "phone"       # unknown falls back to phone
    rest = [k for k in CANONICAL if k != primary]
    head = ('Welcome back <em>%s</em>' % name) if name else "Welcome back"
    hint = ('<div class="hint"><i></i><span>%s</span></div>' % METHODS[primary][2]) if known else ""
    # Phone is ours to style; the other three each follow their provider's rules.
    PROVIDER = {"apple": "apple", "google": "google", "facebook": "facebook", "phone": "ghost"}
    def provider_btn(k):
        cls = PROVIDER[k]
        if k == "google":
            mark = GOOGLE_G                      # never tinted
        elif k in ("apple", "facebook"):
            mark = ico(METHODS[k][1], 18).replace('stroke="currentColor"', 'stroke="#fff"') \
                                          .replace('fill="currentColor"', 'fill="#fff"')
        else:
            mark = ico(METHODS[k][1], 18)
        return '<button class="btn %s">%s<span>%s</span></button>' % (cls, mark, METHODS[k][0])

    # Only phone takes the sunset pill, and only while it is the promoted method. A provider
    # keeps its own livery in every position -- the promotion is carried by position and by the
    # hint row above. Epic note: "gradient reserved for our own CTAs".
    if primary == "phone":
        btns = '<button class="btn sunset">%s<span>%s</span></button>' % (
            ico(METHODS[primary][1], 18).replace('stroke="currentColor"', 'stroke="#fff"'),
            METHODS[primary][0])
    else:
        btns = provider_btn(primary)
    for k in rest:
        btns += provider_btn(k)
    return ('<div class="screen s142" style="--w:%dpx;--h:%dpx;--st:%dpx;--sbm:%dpx;--r:%dpx;--wgap:%dpx">'
            '%s%s<div class="body">%s'
            '<div class="stack">'
            '<h1 class="h1 ul">%s</h1>'
            '<p class="sub">Sign back in to check your availability and see who&#8217;s free today.</p>'
            '</div>'
            '<div class="gap"></div>'
            '<div class="stackbtn">%s%s</div>'
            '<div class="gap"></div>'
            '<p class="legal help">Trouble signing in? <b>Get help</b> or <b>Use a different account</b></p>'
            '<p class="legal2"><b>Legal Notice</b> &#183; <b>Privacy Policy</b></p>'
            '</div>%s</div>'
            % (w[0], w[1], w[2], w[3], w[4], wgap, backdrop(), sb, WORDMARK, head, hint, btns, hi))


# ── screen 143 ──────────────────────────────────────────────────────────────
def keypad():
    rows = [[("1", ""), ("2", "ABC"), ("3", "DEF")],
            [("4", "GHI"), ("5", "JKL"), ("6", "MNO")],
            [("7", "PQRS"), ("8", "TUV"), ("9", "WXYZ")],
            [("", None), ("0", "+"), ("del", None)]]
    out = ['<div class="kbd">']
    for r in rows:
        out.append('<div class="r">')
        for d, s in r:
            if d == "":
                out.append('<div class="k blank"></div>')
            elif d == "del":
                out.append('<div class="k blank"><b style="font-size:18px">&#9003;</b></div>')
            else:
                sub = ('<s>%s</s>' % s) if s else ""
                out.append('<div class="k"><b>%s</b>%s</div>' % (d, sub))
        out.append('</div>')
    out.append('</div>')
    return "".join(out)


def verify(w, sm, home, state):
    """A = enter number · B = invalid number · C = enter code · D = code mismatch."""
    sb, hi = chrome(sm, home)
    small = w[0] == 375
    sw, sh = (44, 56) if small else (49, 62)
    hdr = '<div class="hdr">%s</div>' % ico("arrow-left", 22, 2)
    eyebrow = '<div class="eyebrow"><i></i>Phone verification</div>'

    if state in ("A", "B"):
        bad = state == "B"
        digits = ('0151 2' if bad else '176 123 45 678')
        inp = ('<div class="inp%s">%s<span class="caret"></span>%s</div>'
               % (" err" if bad else "", digits,
                  '<span class="bang">!</span>' if bad else ""))
        helper = ('<div class="help20 err">Please enter a valid number e.g. 176 123 45 678</div>'
                  if bad else '<div class="help20">Standard message rates may apply.</div>')
        cta = ('<div class="cta"><button class="btn sunset%s">Send me the code</button></div>'
               % (" off" if bad else ""))
        content = (eyebrow +
                   '<h1 class="h1 ul">What&#8217;s your <em>number</em>?</h1>'
                   '<p class="sub">We&#8217;ll send a 6-digit code to verify it is you.</p>'
                   '<div class="row">'
                   '<div class="cc"><span class="flag"><b style="background:#000"></b>'
                   '<b style="background:#DD0000"></b><b style="background:#FFCE00"></b></span>'
                   '<span class="code">+49</span>%s</div>%s</div>%s%s'
                   % (ico("chevron-down", 16, 2), inp, helper, cta))
    else:
        bad = state == "D"
        vals = ["4", "8", "2", "1", "7", "0"] if bad else ["", "", "", "", "", ""]
        slots = []
        for i, v in enumerate(vals):
            cls = "slot"
            if bad:
                pass
            elif i == 0:
                cls += " active"
            inner = v if v else ('<span class="caret"></span>' if (not bad and i == 0) else "")
            slots.append('<div class="%s" style="--sw:%dpx;--sh:%dpx">%s</div>' % (cls, sw, sh, inner))
        row = ('<div class="slots%s" aria-label="Enter your 6-digit verification code">%s</div>'
               % (" bad" if bad else "", "".join(slots)))
        helper = ('<div class="help42"><div class="errbox"><i>!</i>'
                  '<span>That code didn&#8217;t match. Try again.</span></div></div>'
                  if bad else '<div class="help42"></div>')
        resend = ('<span class="live">Send a new code</span>' if bad
                  else '<span class="cool">Send a new code in 0:21</span>')
        content = (eyebrow +
                   '<h1 class="h1 ul">Enter your <em>code</em>.</h1>'
                   '<p class="sub">We just sent a 6-digit code to<br><b>+49 176 123 45 678</b>.</p>'
                   '%s%s'
                   '<div class="cta c"><button class="btn sunset">Verify code</button></div>'
                   '<div class="sec"><span class="q">Didn&#8217;t receive a code?</span>%s'
                   '<span class="edit">%s Edit phone number</span></div>'
                   % (row, helper, resend, ico("edit", 13, 1.8)))

    dim = state in ("C", "D")
    return ('<div class="screen s143%s" style="--w:%dpx;--h:%dpx;--st:%dpx;--sbm:%dpx;--r:%dpx">'
            '%s%s%s<div class="body">%s<div class="gap"></div></div>%s%s</div>'
            % (" tight" if small else "", w[0], w[1], w[2], w[3], w[4],
               backdrop143(dim), sb, hdr, content, keypad(), hi))


# ── assemble ────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    html = [
        '<meta charset="utf-8"><title>ShowUp Welcome and Sign-up</title>',
        '<style>%s%s%s%s%s</style>' % (FACES, CSS, CSS_140, CSS_142, CSS_143),
        '<div class="page"><header>',
        '<p class="kicker">SHOWUP-140 &#183; 142 &#183; 143</p>',
        '<h1>Welcome &amp; sign-up</h1>',
        '<p class="lede">Every screen at the three device sizes in the acceptance criteria. Startup and '
        'Welcome back share one backdrop and one wordmark at 26 &#8212; they are meant to read as the same '
        'space. Phone verification is four states of one column, with both helper regions reserved so the '
        'CTA never moves. Fonts are embedded: this file works offline.</p></header>',
    ]

    html.append('<section><h2 class="title">SHOWUP-140 &#8212; Startup (first run)</h2>'
                '<p class="meta">wordmark 26 &#183; 132 flexible gap &#183; two flex:1 spacers around the '
                'social-proof row &#183; sunset CTA</p><div class="rail">')
    for d in DEVICES:
        gap = 132 if d[0] != 375 else 72        # the 132 gap is the element that yields on short frames
        html.append('<figure>%s<figcaption>%d &#215; %d &#183; %s%s</figcaption></figure>'
                    % (startup(d, d[5], d[6], gap), d[0], d[1], d[7],
                       " &#183; gap 132&#8594;72" if d[0] == 375 else ""))
    html.append('</div></section>')

    html.append('<section><h2 class="title">SHOWUP-142 &#8212; Welcome back (re-login)</h2>'
                '<p class="meta">same backdrop and wordmark as Startup &#183; 120 flexible gap &#183; '
                'four methods, canonical order, only the last-used one is sunset</p><div class="rail">')
    for d in DEVICES:
        gap = 120 if d[0] != 375 else 60
        html.append('<figure>%s<figcaption>%d &#215; %d &#183; %s%s</figcaption></figure>'
                    % (welcomeback(d, d[5], d[6], gap), d[0], d[1], d[7],
                       " &#183; gap 120&#8594;60" if d[0] == 375 else ""))
    html.append('</div></section>')

    html.append('<section><h2 class="title">SHOWUP-142 &#8212; the other lastUsed values</h2>'
                '<p class="meta">at 390 &#215; 844 &#183; always four buttons, no duplicates, no reordering '
                'beyond lifting the primary &#183; unknown falls back to phone and hides the hint row</p>'
                '<div class="rail">')
    D390 = DEVICES[1]
    for lu, cap in [("apple", "lastUsed = apple"), ("google", "lastUsed = google"),
                    ("facebook", "lastUsed = facebook"), ("nope", "lastUsed = unknown &#8594; phone, no hint"),
                    ("phone", "no name &#8594; no italic span")]:
        nm = "" if cap.startswith("no name") else "Leo"
        html.append('<figure>%s<figcaption>%s</figcaption></figure>'
                    % (welcomeback(D390, D390[5], D390[6], 120, lu, nm), cap))
    html.append('</div></section>')

    for state, title, note in [
        ("A", "State A &#8212; enter number", "helper region reserved at 20 &#183; CTA enabled"),
        ("B", "State B &#8212; invalid number", "digits preserved &#183; danger border + 4px halo + inline ! &#183; CTA disabled at 0.45 &#183; CTA has not moved"),
        ("C", "State C &#8212; enter code", "six slots 49&#215;62 &#183; first active with violet caret &#183; helper region reserved at 42 &#183; CTA disabled"),
        ("D", "State D &#8212; code mismatch", "digits kept &#183; 4% wash, never a solid fill &#183; error glued to the slots &#183; CTA stays enabled &#183; cooldown released to 0"),
    ]:
        html.append('<section><h2 class="title">SHOWUP-143 &#8212; %s</h2><p class="meta">%s</p><div class="rail">'
                    % (title, note))
        for d in DEVICES:
            extra = " &#183; slots 44&#215;56" if d[0] == 375 else ""
            html.append('<figure>%s<figcaption>%d &#215; %d &#183; %s%s</figcaption></figure>'
                        % (verify(d, d[5], d[6], state), d[0], d[1], d[7], extra))
        html.append('</div></section>')

    html.append('</div>')
    io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(html))
    print("wrote: %s" % OUT)
    print("size : %.1f MB" % (os.path.getsize(OUT) / 1024.0 / 1024.0))
