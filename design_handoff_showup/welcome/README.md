# Welcome & sign-up flow — handoff

The screens a visitor sees before they have an account. Point Claude Code at **this folder**.

```
welcome/
  EPIC.md                              ← flow-level scope, standing rules, open questions
  screen-startup-reference.jsx         ← design reference, screen 01. Read values from here.
  screen-login-reference.jsx           ← design reference, screen 02
  screen-phone-reference.jsx           ← design reference, screen 03 (4 states)
  screen-connect-reference.jsx         ← design reference, screen 04 (10 states, incl. the conflict modal)
  tickets/01-startup-first-run.md      ← scope, copy strings, AC
  tickets/02-welcome-back.md
  tickets/03-phone-verification.md
  tickets/04-connect-flow.md
  spec-sheets/01-startup-first-run.png ← annotated visual spec, keyed ①–⑨
  spec-sheets/02-welcome-back.png
  spec-sheets/03-phone-verification.png
  spec-sheets/04-connect-flow.png
  ../components/shared.jsx             ← StatusBar, Wordmark, Button, Icon, HomeIndicator
  ../tokens/colors_and_type.css        ← authoritative token values
```

## Order of authority

1. **`screen-<name>-reference.jsx`** — real values, wins on numbers
2. **`tickets/*.md`** — wins on behaviour, scope and copy strings
3. **`spec-sheets/*.png`** — wins on nothing; it is the human-readable summary of 1 and 2

Never measure the PNG. Every number in it is in the reference file.

## Screens in this flow

| # | Screen | Status |
| --- | --- | --- |
| 01 | Startup — first run | speccd + ticketed |
| 02 | Welcome back — re-login | speccd + ticketed |
| 03 | Phone verification — enter number · invalid number · enter code · code mismatch | speccd + ticketed |
| 04 | Connect an account — the whole initial connection flow: idle · in flight · OS handoff (Apple / Google) · linking · success · cancelled · error · already-exists conflict · conflict resolve | speccd + ticketed |

Startup and Welcome back must read as the same space: same three background layers, same wordmark at 26. Build them against each other, not separately.

## The two rules that break everything if ignored

**1. No absolute Y positioning.** Status bar / content / home indicator is one flex column; the content column is top-anchored at the top, bottom-anchored at the bottom, with **two** `flex: 1` spacers around the floating middle element (the social-proof row on Startup, the button stack on Welcome back).

**2. The wordmark → headline gap is the only flexible element** — 132 on Startup, 120 on Welcome back. On short frames it shrinks first. Never the type, never a button height, never the CTA's safe-area margin, never a scroll.

**3. Failure states reserve their space and preserve their input.** On screen 03 both helper regions are reserved (min-height 20 / 42) so the CTA never jumps, and neither a rejected number nor a mismatched code is ever cleared for the user.

**4. Startup and Welcome back share one backdrop and one wordmark.** Both reference files carry the same three layers at the same values. Build it once as a shared component; two copies drift. Screen 04 uses that same backdrop — it is three copies waiting to happen.

**5. Screens 02 and 04 render the same method list.** Apple → Google → Facebook, same order, same button geometry; 02 adds phone, 04 omits it (the number was just verified). One component, two hosts.

**6. Where the OS takes over, we stop.** The Apple and Google sheets on screen 04, and the conflict's resolve step, are stylized stand-ins in the reference file — `SAOSHandoff`, `SAAppleSheetBody`, `SAGoogleSheetBody`. Do not build them, do not restyle them, do not assume their height. Provider brand rules for the trigger buttons override our spec sheets; see the blocking open question in ticket 04.

## Ticket split in this flow

**Screen 04 is one ticket covering the whole initial connection attempt** — every ending of one tap, including the already-exists conflict. It was briefly split into 04 (connect) + 05 (conflict); that split is retired. The conflict is only detectable after a credential comes back, so it cannot be built or tested apart from the flow that produces it.

**Re-login is the separate ticket** — screen 02, where a returning member signs in with a provider they already linked. Different entry (launch, not verification), different device state (`lastUsed`), different outcome (a session, not a link).

Shared provider work — Apple capability, Google OAuth clients, Facebook review, the backend link endpoint — sits as dev-only sub-tasks under 04 and is not duplicated on 02.

## Device matrix

375 × 667 · 390 × 844 · 430 × 932. All content visible, nothing clipped, no scroll, CTA and legal line always fully above the safe area. 375 × 667 is where it gets tight; check it first.

## Copy

Each ticket carries a **Copy — final strings** section; treat it as the source. Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**.
