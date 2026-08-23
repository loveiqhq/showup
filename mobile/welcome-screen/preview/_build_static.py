# -*- coding: utf-8 -*-
"""Build one self-contained HTML of all six tutorial screens, fonts embedded, for the Desktop."""
import io, os, sys, base64
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = r"C:\Users\krnji\showup\mobile\welcome-screen"
FONTS = os.path.join(REPO, "fonts")
DESKTOP = os.path.join(os.environ["USERPROFILE"], "OneDrive - INTERNATIONAL UNIVERSITY OF SARAJEVO", "Desktop")
if not os.path.isdir(DESKTOP):
    DESKTOP = os.path.join(os.environ["USERPROFILE"], "Desktop")
OUT = os.path.join(DESKTOP, "ShowUp - 6 tutorial screens.html")

# ── fonts as data URIs so the file is portable ──────────────────────────
def face(family, weight, style, filename):
    with open(os.path.join(FONTS, filename), "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    return ('@font-face{font-family:"%s";font-weight:%s;font-style:%s;'
            'src:url(data:font/ttf;base64,%s) format("truetype");}' % (family, weight, style, b64))

FACES = "".join([
    face("SULora", 400, "normal", "Lora-Regular.ttf"),
    face("SULora", 700, "normal", "Lora-Bold.ttf"),
    face("SULora", 400, "italic", "Lora-Italic.ttf"),
    face("SULora", 700, "italic", "Lora-BoldItalic.ttf"),
    face("SUManrope", 500, "normal", "Manrope-Medium.ttf"),
    face("SUManrope", 600, "normal", "Manrope-SemiBold.ttf"),
    face("SUManrope", 700, "normal", "Manrope-Bold.ttf"),
])

HEART = ("M99.8 172.9C78 152.1 17.9 111.3 20 63.7C22.3 22.2 58.2 16.7 66.6 17.1"
         "C82.6 17.1 97 27.6 100 36.6C103 27.6 117.4 17.1 133.4 17.1"
         "C141.8 16.7 177.7 22.2 180 63.7C182.1 111.3 122 152.1 99.8 172.9Z")

CSS = """
:root{--page-bg:#F4F2F7;--page-fg:#1D1129;--page-mute:#6B6377;--page-rule:#DFDAE6;}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}}
:root[data-theme="dark"]{--page-bg:#14111A;--page-fg:#ECE8F2;--page-mute:#9990A6;--page-rule:#2B2634;}
:root{--bg:#FFFBF7;--orange:#FE6839;--purple:#812AEC;--fg:#1D1129;--neutral:#4B3B5A;
--faint:rgba(29,17,41,.24);--subtle:rgba(29,17,41,.46);--track:rgba(29,17,41,.12);
--eyebrowbg:rgba(167,139,250,.16);
--serif:"SULora",Georgia,serif;--sans:"SUManrope",-apple-system,"Segoe UI",sans-serif;}
*{box-sizing:border-box}
body{margin:0;padding:0 24px 72px;background:var(--page-bg);color:var(--page-fg);font-family:var(--sans);-webkit-font-smoothing:antialiased}
.page{max-width:1560px;margin:0 auto;display:flex;flex-direction:column;gap:2.5rem}
header{padding-top:3.25rem;display:flex;flex-direction:column;gap:.5rem}
.kicker{margin:0;font-size:.7rem;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--orange)}
h1{margin:0;font-family:var(--serif);font-weight:700;font-size:clamp(1.9rem,4vw,2.6rem);line-height:1.1;letter-spacing:-.02em}
.lede{margin:0;color:var(--page-mute);max-width:66ch;line-height:1.6}
section{display:flex;flex-direction:column;gap:.9rem;padding-top:1.6rem;border-top:1px solid var(--page-rule)}
.title{margin:0;font-family:var(--serif);font-size:1.35rem;font-weight:700;letter-spacing:-.01em}
.meta{margin:0;font-size:.84rem;color:var(--page-mute)}
.rail{display:flex;flex-wrap:wrap;gap:2rem;align-items:flex-start}
figure{margin:0;display:flex;flex-direction:column;gap:.6rem}
figcaption{font-size:.76rem;color:var(--page-mute);font-variant-numeric:tabular-nums}
/* phone */
.screen{position:relative;width:var(--w);height:var(--h);flex:none;overflow:hidden;background:var(--bg);
border-radius:var(--r,42px);box-shadow:0 0 0 1px var(--page-rule),0 20px 46px -18px rgba(29,17,41,.4);isolation:isolate}
.sb{position:absolute;top:0;left:0;right:0;height:var(--st);z-index:3;display:flex;align-items:center;
justify-content:space-between;padding:0 30px;font-size:15px;font-weight:700;color:var(--fg);font-variant-numeric:tabular-nums}
.sb.sm{padding:0 18px;font-size:13px}
.sb .ic{display:flex;align-items:center;gap:5px}
.hi{position:absolute;left:50%;transform:translateX(-50%);bottom:9px;width:140px;height:5px;border-radius:3px;background:rgba(29,17,41,.85);z-index:3}
/* ── screen 1: welcome ── */
.w{position:relative;z-index:2;height:100%;display:flex;flex-direction:column;padding:calc(var(--st) + 20px) 24px calc(var(--sbm) + 20px)}
.wordmark{flex:none;font-family:var(--serif);font-size:26px;line-height:1;font-weight:600;color:var(--fg);letter-spacing:-.01em}
.wordmark .up{color:var(--purple);font-style:italic}.wordmark .dot{color:var(--orange)}
.gap{flex:1 1 0;min-height:8px}
.cluster{flex:0 1 auto;display:flex;flex-direction:column;gap:16px;min-height:0}
.heart{margin-bottom:4px;flex:0 1 190px;min-height:0;align-self:center;width:200px;max-width:100%;display:flex;align-items:center;justify-content:center}
.heart svg{width:auto;height:100%;max-width:100%;display:block}
.h-xl{margin:0;flex:none;position:relative;font-family:var(--serif);font-weight:700;font-size:42px;line-height:1.05;letter-spacing:-.02em;color:var(--fg)}
.h-xl i{font-style:italic}
.h-xl::after{content:"";position:absolute;left:2px;bottom:-6px;width:210px;height:12px;border-radius:50%;
background:linear-gradient(90deg,rgba(254,104,57,0),rgba(254,104,57,.55) 26%,rgba(224,86,122,.45) 62%,rgba(129,42,236,0));filter:blur(5px)}
.subhead{flex:none;display:flex;align-items:center;gap:8px;font-weight:600;font-size:18px;color:var(--fg)}
.subhead svg{width:20px;height:20px;flex:none}
.body16{margin:0;flex:none;max-width:320px;font-weight:500;font-size:16px;line-height:1.5;color:var(--neutral)}
.foot{flex:none;display:flex;flex-direction:column;gap:10px}
.pill{height:56px;width:100%;border:0;border-radius:999px;color:#fff;font-family:var(--sans);font-weight:700;font-size:18px;
display:flex;align-items:center;justify-content:center;gap:12px;
background:linear-gradient(90deg,#FE6839,#F1554E 32%,#B33FBE 72%,#812AEC);
box-shadow:0 10px 26px -8px rgba(129,42,236,.55),0 2px 6px rgba(254,104,57,.28)}
.pill svg{width:20px;height:20px}
.cap{margin:0;text-align:center;font-weight:600;font-size:12px;color:var(--subtle)}
/* ── screens 2-6: the shell ── */
.t{position:relative;z-index:2;height:100%;display:flex;flex-direction:column;padding:calc(var(--st) + 8px) 24px var(--sbm)}
.prog{flex:none;display:flex;gap:6px;margin-bottom:24px}
.prog span{flex:1 1 0;height:5px;border-radius:999px;background:var(--track)}
.prog span.on{background:var(--purple)}
.eyebrow{flex:none;align-self:flex-start;display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:999px;
background:var(--eyebrowbg);font-weight:700;font-size:11px;line-height:1.2;letter-spacing:.08em;text-transform:uppercase;color:var(--purple)}
.eyebrow i{width:5px;height:5px;border-radius:50%;background:var(--orange);flex:none}
.h-lg{flex:none;position:relative;margin:14px 0 28px;font-family:var(--serif);font-weight:700;font-size:34px;line-height:1.1;
letter-spacing:-.015em;color:var(--fg);text-wrap:pretty}
.h-lg em{font-style:italic}
.h-lg::after{content:"";position:absolute;left:2px;bottom:-4px;width:var(--ul,186px);height:10px;border-radius:50%;
background:linear-gradient(90deg,rgba(254,104,57,0),rgba(254,104,57,.5) 28%,rgba(224,86,122,.4) 64%,rgba(129,42,236,0));filter:blur(4px)}
.art{flex:0 1 var(--ah,230px);min-height:0;margin-top:24px;display:flex;align-items:center;justify-content:center;position:relative}
.art .glow{position:absolute;width:220px;height:190px;pointer-events:none;
background:radial-gradient(circle,rgba(254,104,57,.16),rgba(129,42,236,.10) 48%,transparent 70%);filter:blur(6px)}
.slot{position:relative;height:100%;aspect-ratio:248/210;max-width:100%;border:1.5px dashed rgba(129,42,236,.38);
border-radius:18px;background:rgba(167,139,250,.07);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;color:var(--purple);overflow:hidden}
.slot svg{width:26px;height:26px;opacity:.5;flex:none}
.slot b{font-size:11px;font-weight:700;letter-spacing:.07em;text-transform:uppercase;white-space:nowrap}
.slot i{font-style:normal;font-size:10.5px;font-weight:600;color:var(--subtle);font-variant-numeric:tabular-nums;white-space:nowrap}
.nav{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:24px}
.back{font-weight:600;font-size:14px;color:var(--subtle)}
.back.off{color:transparent}
.next{display:flex;align-items:center;gap:14px}
.next span{font-weight:700;font-size:17px;color:var(--fg)}
.next i{width:56px;height:56px;border-radius:50%;background:var(--orange);display:flex;align-items:center;justify-content:center;flex:none;
box-shadow:0 10px 24px -8px rgba(254,104,57,.65),0 2px 6px rgba(254,104,57,.3)}
.next.term i{background:linear-gradient(135deg,#FE6839,#D05976 38%,#812AEC);box-shadow:0 10px 26px -8px rgba(129,42,236,.6),0 2px 8px rgba(129,42,236,.35)}
.next svg{width:20px;height:20px}
.next.term svg{width:22px;height:22px}
.rules{flex:none;display:flex;flex-direction:column;gap:var(--rg,8px);margin-bottom:var(--rmb,0)}
.rules>div{display:flex;align-items:flex-start;gap:9px}
.rules i{width:6px;height:6px;border-radius:50%;background:var(--orange);flex:none;margin-top:6px}
.rules p{margin:0;font-weight:600;font-size:13px;line-height:1.4;letter-spacing:-.01em;color:var(--fg)}
.rules.nw p{white-space:nowrap}
.rules .d{color:var(--faint);font-weight:500}.rules .c{color:var(--purple)}
.copy{flex:none;font-weight:500;font-size:16px;line-height:1.5;color:var(--neutral);text-wrap:pretty}
.copy p{margin:0}.copy p+p{margin-top:10px}.copy em{font-style:italic;font-weight:700}.copy strong{font-weight:700}
.stmts{flex:none;display:flex;flex-direction:column;gap:11px}
.stmts>div{display:flex;align-items:flex-start;gap:12px}
.stmts i{width:7px;height:7px;border-radius:50%;background:var(--orange);flex:none;margin-top:7px}
.stmts p{margin:0;font-weight:600;font-size:14.5px;line-height:1.42;color:var(--fg)}
.closing{flex:none;margin-top:5px;font-weight:500;font-size:14.5px;line-height:1.5;color:var(--neutral);text-wrap:pretty}
.closing strong{font-weight:700;color:var(--fg)}
"""

ICONS = ('<svg width="17" height="12" viewBox="0 0 17 12" fill="currentColor"><rect y="8" width="3" height="4" rx="1"/>'
         '<rect x="4.5" y="5.5" width="3" height="6.5" rx="1"/><rect x="9" y="3" width="3" height="9" rx="1"/>'
         '<rect x="13.5" width="3" height="12" rx="1"/></svg>'
         '<svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><path d="M8 11.2 5.9 8.9a3 3 0 0 1 4.2 0zM3.2 6.2 1.4 4.3a9.4 9.4 0 0 1 13.2 0l-1.8 1.9a6.8 6.8 0 0 0-9.6 0z"/></svg>'
         '<svg width="25" height="12" viewBox="0 0 25 12" fill="none"><rect x=".6" y=".6" width="20" height="10.8" rx="3" stroke="currentColor" stroke-opacity=".4"/>'
         '<rect x="2.2" y="2.2" width="16.8" height="7.6" rx="1.8" fill="currentColor"/></svg>')

ARROW = ('<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" '
         'stroke-linejoin="round"><path d="M4 12h15M13 6l6 6-6 6"/></svg>')

SLOT = ('<div class="art"><div class="glow"></div><div class="slot">'
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">'
        '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.5" cy="9.5" r="1.8"/><path d="M21 16l-5-5-6 6-2-2-5 5"/></svg>'
        '<b>Illustration</b><i>248 &#215; 210</i></div></div>')

DEVICES = [(375, 667, 20, 0, 34, True, False, "iPhone SE"),
           (390, 844, 54, 28, 42, False, True, "reference frame"),
           (430, 932, 54, 28, 48, False, True, "iPhone Pro Max")]

def welcome_body(i):
    return f'''<div class="w">
<div class="wordmark">Show <span class="up">Up</span><span class="dot">.</span></div>
<div class="gap"></div>
<div class="cluster">
<div class="heart"><svg viewBox="0 0 200 190">
<defs><linearGradient id="g{i}" x1="18%" y1="6%" x2="82%" y2="96%"><stop offset="0%" stop-color="#FE6839"/><stop offset="46%" stop-color="#E8565E"/><stop offset="100%" stop-color="#9333D9"/></linearGradient>
<radialGradient id="gl{i}" cx="50%" cy="48%" r="52%"><stop offset="0%" stop-color="#FE6839" stop-opacity=".26"/><stop offset="100%" stop-color="#FE6839" stop-opacity="0"/></radialGradient></defs>
<ellipse cx="100" cy="92" rx="96" ry="88" fill="url(#gl{i})"/>
<path d="{HEART}" fill="url(#g{i})"/>
<ellipse cx="66" cy="52" rx="24" ry="15" fill="#fff" fill-opacity=".17" transform="rotate(-18 66 52)"/>
<circle cx="13" cy="40" r="4.5" fill="#A877E6"/><circle cx="162" cy="130" r="3.5" fill="#FE6839" fill-opacity=".7"/>
<path d="M186 17l3.2 8.8 8.8 3.2-8.8 3.2-3.2 8.8-3.2-8.8-8.8-3.2 8.8-3.2z" fill="#FBBF4B"/></svg></div>
<h2 class="h-xl">Welcome<br>to <i>Show Up.</i></h2>
<div class="subhead">We&#8217;re happy to see you <svg viewBox="0 0 24 24" fill="#FE6839"><path d="M12 21s-8.5-5.6-8.5-11A5 5 0 0 1 12 7.2 5 5 0 0 1 20.5 10c0 5.4-8.5 11-8.5 11Z"/></svg></div>
<p class="body16">Let us quickly explain how Show Up works.</p>
</div>
<div class="gap"></div>
<div class="foot"><div class="pill">Show me how {ARROW}</div><p class="cap">Takes less than a minute</p></div>
</div>'''

def rules_html(rows, gap, mb, nowrap):
    inner = "".join(f'<div><i></i><p>{a}<span class="d"> &#8212; </span><span class="c">{b}</span></p></div>' for a, b in rows)
    return f'<div class="rules{" nw" if nowrap else ""}" style="--rg:{gap}px;--rmb:{mb}px">{inner}</div>'

STMTS = ["Every profile has a Show-up Rate.",
         "Showing up to dates is reflected positively.",
         "Not showing up is reflected negatively.",
         "A persistently low Show-up Rate reduces your visibility to others.",
         "Miss a date without fair notice and you can&#8217;t search for new dates for 24 hours."]

SCREENS = [
    dict(n=2, tk="SHOWUP-135", name="Meet in real life", step=1, back=False, ul=186,
         eyebrow="Meet people in real life", head='We want you to <em>actually</em> meet.',
         content=rules_html([("No texting for weeks","date in real life instead"),
                             ("No ghosting","we penalize unreliability"),
                             ("No collecting matches","you meet who you match")], 8, 16, True) +
         '<div class="copy"><p>If you match here, you <em>will meet</em> in real life. A match is a committed date &#8212; not a maybe.</p>'
         '<p><strong>Showing up to dates boosts your profile</strong> by highlighting your reliability and increasing your visibility. '
         '<strong>Missing dates without fair notice upfront</strong> reduces your visibility for others.</p></div>'),
    dict(n=3, tk="SHOWUP-136", name="Match on availability", step=2, back=True, ul=210,
         eyebrow="Match on availability", head='Match people who are <em>free to date</em> when you are.',
         content=rules_html([("Visible only when you&#8217;re free to date","check in and state your available times to meet"),
                             ("Synchronised schedules","we only show you people to date who are available when you are"),
                             ("Different day, different vibe","match on what you&#8217;re in the mood for right now, not a static bio")], 12, 0, False)),
    dict(n=4, tk="SHOWUP-137", name="Match means meet", step=3, back=True, ul=170,
         eyebrow="Match means meet", head='A match is a <em>binding</em> date.',
         content=rules_html([("You decide who you like","if you match, you will meet"),
                             ("We suggest the time","a date and time that works for both of your schedules"),
                             ("We pick the place","a safe, public spot halfway between you")], 12, 0, False)),
    dict(n=5, tk="SHOWUP-138", name="30 minutes", step=4, back=True, ul=150,
         eyebrow="30 minutes, no pressure", head='Just <em>thirty minutes</em>.',
         content=rules_html([("Low-pressure 30-minute dates","quick, relaxed meetups to see if you click in real life"),
                             ("30 minutes up","stay if you&#8217;re vibing, or leave with a smile &#8212; no hard feelings"),
                             ("Built-in icebreakers","fun, easy prompts to keep the conversation flowing")], 12, 0, False)),
    dict(n=6, tk="SHOWUP-139", name="Show up, every time", step=5, back=True, ul=196, term=True, scale=0.62,
         nextlabel="I&#8217;m ready to show up",
         eyebrow="Show up, every time", head='If you don&#8217;t show up, <em>there&#8217;s a cost</em>.',
         content='<div class="stmts">' + "".join(f'<div><i></i><p>{t}</p></div>' for t in STMTS) + '</div>'
                 '<p class="closing"><strong>Show Up is for reliable people.</strong> Life happens. Stay fair and show respect '
                 'for each other, and your Show-up Rate will reflect it.</p>'),
]

def frame(inner, w, h, st, sbm, r, sm, home, extra=""):
    return (f'<div class="screen" style="--w:{w}px;--h:{h}px;--st:{st}px;--sbm:{sbm}px;--r:{r}px;{extra}">'
            f'<div class="sb{" sm" if sm else ""}"><span>4:20</span><span class="ic">{ICONS}</span></div>'
            f'{inner}{"<div class=\'hi\'></div>" if home else ""}</div>')

html = ['<meta charset="utf-8"><title>ShowUp &#8212; 6 tutorial screens</title>',
        f'<style>{FACES}{CSS}</style>',
        '<div class="page"><header>',
        '<p class="kicker">SHOWUP-117 &#183; 135 &#183; 136 &#183; 137 &#183; 138 &#183; 139</p>',
        '<h1>The six tutorial screens</h1>',
        '<p class="lede">Every screen at the three device sizes in the acceptance criteria. Screens 2&#8211;6 share '
        'one shell &#8212; progress bar, eyebrow, headline, illustration block, a single flexible gap and the nav row; '
        'only the content differs. Illustrations are placeholders awaiting artwork. This file is self-contained: '
        'fonts are embedded, so it works offline and can be emailed.</p></header>']

# screen 1
html.append('<section><h2 class="title">Screen 1 &#8212; Welcome</h2>'
            '<p class="meta">SHOWUP-117 &#183; Tutorial 1 &#183; no progress bar &#183; sunset pill CTA</p><div class="rail">')
for i, (w, h, st, sbm, r, sm, home, label) in enumerate(DEVICES):
    html.append(f'<figure>{frame(welcome_body(i), w, h, st, sbm, r, sm, home)}'
                f'<figcaption>{w} &#215; {h} &#183; {label}</figcaption></figure>')
html.append('</div></section>')

# screens 2-6
for sc in SCREENS:
    ah = round(230 * sc.get("scale", 1))
    bits = [f'<section><h2 class="title">Screen {sc["n"]} &#8212; {sc["name"]}</h2>',
            f'<p class="meta">{sc["tk"]} &#183; progress {sc["step"]}/5 &#183; back '
            f'{"active" if sc["back"] else "hidden"}{" &#183; terminal CTA" if sc.get("term") else ""}'
            f'{" &#183; art scale 0.62" if sc.get("scale") else ""}</p><div class="rail">']
    for (w, h, st, sbm, r, sm, home, label) in DEVICES:
        segs = "".join(f'<span class="{"on" if k < sc["step"] else ""}"></span>' for k in range(5))
        inner = (f'<div class="t"><div class="prog">{segs}</div>'
                 f'<div class="eyebrow"><i></i>{sc["eyebrow"]}</div>'
                 f'<h2 class="h-lg" style="--ul:{sc["ul"]}px">{sc["head"]}</h2>'
                 f'{sc["content"]}{SLOT}<div class="gap"></div>'
                 f'<div class="nav"><span class="back{"" if sc["back"] else " off"}">Back</span>'
                 f'<span class="next{" term" if sc.get("term") else ""}"><span>{sc.get("nextlabel","Next")}</span>'
                 f'<i>{ARROW}</i></span></div></div>')
        bits.append(f'<figure>{frame(inner, w, h, st, sbm, r, sm, home, f"--ah:{ah}px;")}'
                    f'<figcaption>{w} &#215; {h} &#183; {label}</figcaption></figure>')
    bits.append('</div></section>')
    html.append("".join(bits))

html.append('</div>')
doc = "\n".join(html)
io.open(OUT, "w", encoding="utf-8", newline="\n").write(doc)
print("wrote: %s" % OUT)
print("size : %.1f MB (fonts embedded, fully self-contained)" % (os.path.getsize(OUT) / 1024 / 1024))
