# -*- coding: utf-8 -*-
"""A clickable walkthrough of the six tutorial screens.

The static sheet (build_desktop_html.py) is for comparing layout across device sizes. This one is
for walking the flow: one phone, real buttons, and the three animations the apps have.

Content, tokens and markup come from the static generator via runpy, so the two files cannot drift.
"""
import io
import os
import runpy

# Deliberately no sys.stdout rewrap here: build_desktop_html.py does its own, and wrapping an
# already-wrapped stream closes the underlying buffer when the outer wrapper is collected.
HERE = os.path.dirname(os.path.abspath(__file__))
ns = runpy.run_path(os.path.join(HERE, "build_desktop_html.py"))

FACES, CSS, ICONS, ARROW, SLOT = ns["FACES"], ns["CSS"], ns["ICONS"], ns["ARROW"], ns["SLOT"]
SCREENS, welcome_body, DESKTOP = ns["SCREENS"], ns["welcome_body"], ns["DESKTOP"]

INTERACTIVE_CSS = """
.stage{display:flex;flex-direction:column;align-items:center;gap:1.5rem;padding-top:.5rem}
.phone{position:relative;overflow:hidden;background:var(--bg);flex:none;isolation:isolate;
border-radius:var(--r);box-shadow:0 0 0 1px var(--page-rule),0 26px 60px -20px rgba(29,17,41,.45);
width:var(--w);height:var(--h);transition:width .3s ease,height .3s ease}
.card{position:absolute;inset:0;opacity:0;pointer-events:none;
transition:opacity .22s ease, transform .32s cubic-bezier(.2,.7,.3,1)}
.card.active{opacity:1;pointer-events:auto;transform:translateX(0)}
.card.from-right{transform:translateX(64px)}
.card.from-left{transform:translateX(-64px)}
.card.to-left{transform:translateX(-64px)}
.card.to-right{transform:translateX(64px)}
/* the segment colour tweens, exactly as it does natively */
.prog span{background:var(--track);transition:background-color .32s ease}
.prog span.on{background:var(--purple)}
.next,.back,.pill{cursor:pointer;user-select:none}
.next i,.pill{transition:transform .18s cubic-bezier(.2,.7,.3,1)}
.next:active i,.pill:active{transform:scale(.94)}
.back:active{opacity:.55}
.back.off{pointer-events:none}
.controls{display:flex;flex-wrap:wrap;gap:.5rem;align-items:center;justify-content:center}
.controls button{font-family:var(--sans);font-size:.82rem;font-weight:600;padding:.5rem .9rem;
border-radius:999px;border:1px solid var(--page-rule);background:transparent;color:var(--page-fg);
cursor:pointer;transition:background .15s ease,border-color .15s ease,color .15s ease}
.controls button:hover{border-color:var(--orange)}
.controls button[aria-pressed="true"]{background:var(--orange);border-color:var(--orange);color:#fff}
.hint{margin:0;font-size:.8rem;color:var(--page-mute);text-align:center;font-variant-numeric:tabular-nums}
@media (prefers-reduced-motion:reduce){.card,.prog span,.next i,.pill{transition:none!important}}
"""


def tutorial_card(sc):
    """One card body. Progress starts empty and JS fills it on entry, so the tween is real."""
    segs = "".join('<span></span>' for _ in range(5))
    back_cls = "" if sc["back"] else " off"
    term_cls = " term" if sc.get("term") else ""
    nxt = (sc["n"] + 1) if sc["n"] < 6 else 1
    return (
        '<div class="t"><div class="prog" data-step="%d">%s</div>'
        '<div class="eyebrow"><i></i>%s</div>'
        '<h2 class="h-lg" style="--ul:%dpx">%s</h2>'
        '%s%s<div class="gap"></div>'
        '<div class="nav"><span class="back%s" data-go="%d">Back</span>'
        '<span class="next%s" data-go="%d"><span>%s</span><i>%s</i></span></div></div>'
        % (sc["step"], segs, sc["eyebrow"], sc["ul"], sc["head"], sc["content"], SLOT,
           back_cls, sc["n"] - 1, term_cls, nxt, sc.get("nextlabel", "Next"), ARROW)
    )


cards = ['<div class="card active" data-card="1">%s</div>'
         % welcome_body(99).replace('<div class="pill">', '<div class="pill" data-go="2">')]
for sc in SCREENS:
    ah = round(230 * sc.get("scale", 1))
    cards.append('<div class="card" data-card="%d" style="--ah:%dpx">%s</div>'
                 % (sc["n"], ah, tutorial_card(sc)))

JS = """
(function(){
  var phone = document.getElementById('phone');
  var busy = false, current = 1;

  function card(n){ return phone.querySelector('.card[data-card="' + n + '"]'); }

  // Run fn on a later frame for a real transition, but never let correctness depend on it.
  // requestAnimationFrame does not fire in a hidden or throttled tab, so a timeout backs it up
  // and whichever arrives first wins. Without the fallback the page silently stops working the
  // moment it is not the frontmost tab.
  function soon(fn){
    var done = false;
    var run = function(){ if(!done){ done = true; fn(); } };
    requestAnimationFrame(function(){ requestAnimationFrame(run); });
    setTimeout(run, 60);
  }

  function paintProgress(el){
    var bar = el.querySelector('.prog');
    if(!bar) return;
    var step = +bar.dataset.step;
    var segs = [].slice.call(bar.querySelectorAll('span'));
    segs.forEach(function(s){ s.classList.remove('on'); });
    soon(function(){
      segs.forEach(function(s, i){ if(i < step) s.classList.add('on'); });
    });
  }

  function show(n){
    if(busy || n === current) return;
    busy = true;
    var forward = n > current;
    var from = card(current), to = card(n);

    // Logical state moves immediately. It used to be set inside the 340ms timeout, decoupled from
    // the frame callback that actually swapped the classes -- so if the callback was delayed the
    // two disagreed and the next tap was computed against the wrong card.
    current = n;
    document.getElementById('where').textContent = 'Screen ' + n + ' of 6';

    // Park the incoming card off to one side, then swap on a later frame so the transform has a
    // painted start position to animate from.
    //
    // The outgoing card keeps .active until the swap, so there is never a moment with no active
    // card -- otherwise there is a one-frame gap where the phone is blank.
    to.classList.add(forward ? 'from-right' : 'from-left');

    soon(function(){
      to.classList.add('active');
      to.classList.remove('from-right', 'from-left');
      from.classList.remove('active');
      from.classList.add(forward ? 'to-left' : 'to-right');
      paintProgress(to);
    });

    setTimeout(function(){
      from.classList.remove('to-left', 'to-right');
      busy = false;
    }, 380);
  }

  phone.addEventListener('click', function(e){
    var t = e.target.closest('[data-go]');
    if(t && !t.classList.contains('off')) show(+t.dataset.go);
  });

  var SIZES = {
    '375': ['375px', '667px', '20px', '0px',  '34px', true,  false],
    '390': ['390px', '844px', '54px', '28px', '42px', false, true ],
    '430': ['430px', '932px', '54px', '28px', '48px', false, true ]
  };
  function setSize(key, btn){
    var s = SIZES[key];
    phone.style.setProperty('--w',  s[0]);
    phone.style.setProperty('--h',  s[1]);
    phone.style.setProperty('--st', s[2]);
    phone.style.setProperty('--sbm', s[3]);
    phone.style.setProperty('--r',  s[4]);
    phone.querySelector('.sb').classList.toggle('sm', s[5]);
    phone.querySelector('.hi').style.display = s[6] ? 'block' : 'none';
    [].forEach.call(document.querySelectorAll('.controls button[data-size]'), function(b){
      b.setAttribute('aria-pressed', b === btn ? 'true' : 'false');
    });
  }
  [].forEach.call(document.querySelectorAll('.controls button[data-size]'), function(b){
    b.addEventListener('click', function(){ setSize(b.dataset.size, b); });
  });

  document.addEventListener('keydown', function(e){
    if(e.key === 'ArrowRight' && current < 6) show(current + 1);
    if(e.key === 'ArrowLeft'  && current > 1) show(current - 1);
  });

  paintProgress(card(1));
})();
"""

doc = [
    '<meta charset="utf-8"><title>ShowUp Tutorial Walkthrough</title>',
    '<style>%s%s%s</style>' % (FACES, CSS, INTERACTIVE_CSS),
    '<div class="page"><header>',
    '<p class="kicker">SHOWUP-117 &#183; 135 &#183; 136 &#183; 137 &#183; 138 &#183; 139</p>',
    '<h1>Walk the tutorial</h1>',
    '<p class="lede">The real flow, clickable. The buttons advance and go back exactly as they do '
    'in the app, with the same three motions: cards slide in the direction of travel, progress '
    'segments fill rather than snap, and the button dips on press. Switch device size at any point '
    '&#8212; the small frame is where these layouts are tightest. Arrow keys work too.</p>'
    '</header>',
    '<div class="stage">',
    '<div class="phone" id="phone" '
    'style="--w:390px;--h:844px;--st:54px;--sbm:28px;--r:42px;--ah:230px">',
    '<div class="sb"><span>4:20</span><span class="ic">%s</span></div>' % ICONS,
    "".join(cards),
    '<div class="hi"></div>',
    '</div>',
    '<div class="controls">',
    '<button data-size="375">375 &#215; 667</button>',
    '<button data-size="390" aria-pressed="true">390 &#215; 844</button>',
    '<button data-size="430">430 &#215; 932</button>',
    '</div>',
    '<p class="hint" id="where">Screen 1 of 6</p>',
    '</div></div>',
    '<script>%s</script>' % JS,
]

OUT = os.path.join(DESKTOP, "ShowUp - tutorial WALKTHROUGH.html")
io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(doc))
print("wrote: %s" % OUT)
print("size : %.1f MB" % (os.path.getsize(OUT) / 1024.0 / 1024.0))
