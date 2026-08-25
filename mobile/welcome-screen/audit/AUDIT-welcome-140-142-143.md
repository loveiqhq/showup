# Welcome & sign-up — SHOWUP-140 / 142 / 143

Audit against the handoff, in its own order of authority:

1. `welcome/screen-startup-reference.jsx`, `welcome/screen-login-reference.jsx` — win on numbers
2. the Jira tickets — win on behaviour, scope, copy
3. `welcome/spec-sheets/*.png` — win on nothing

**SHOWUP-143 has no reference `.jsx`.** The handoff lists screen 03 as *"not yet ticketed"*, so its
numbers come from the ticket's acceptance criteria and the annotated sheet — the only sources there
are. Everything for 143 is therefore held to the ticket rather than to a reference file.

---

## Findings

### 1 — The helper region reserve cannot hold the copy it must hold  · SHOWUP-143 · **spec conflict**

The sheet reserves **42** for the C/D helper region, and the ticket requires:

> *"The CTA does not move between the default and error state on either screen."*

The specified error string —
`Code doesn't match. Please check or request a new code.` at Manrope 500 13.5/1.4 —
**wraps to two lines at all three widths**, making the box **59px**, measured in a browser:

| width | error box | text lines | spec reserve |
|---|---|---|---|
| 375 | 59 | 2 | 42 |
| 390 | 59 | 2 | 42 |
| 430 | 59 | 2 | 42 |

Reserving 42 therefore lets the CTA jump **17px** the moment the error appears — the exact thing the
AC forbids. The two cannot both be satisfied.

**Resolved toward the ticket**, which wins on behaviour: the region is reserved at the height the
specified copy actually needs. **Design to confirm which one changes** — the 42, or the string. A
one-line error message would make both true at once.

### 2 — States C and D do not fit at 375 × 667  · SHOWUP-143 · **needs a design decision**

With every reduction the sheet permits already applied — eyebrow→headline margin collapsed first,
slot row down from 49 × 62 to 44 × 56 with gap 6, CTA never below 56, neither helper region hidden,
no scroll — state D still needs **418px** of content height against **383px** available. It is
**35px short**.

| element | height |
|---|---|
| eyebrow | 23 |
| headline, margins collapsed to 4 / 6 | 50 |
| sub copy (2 lines) | 44 |
| slot row 44 × 56, gap 6, mt 14 | 70 |
| helper region (see finding 1) | 69 |
| CTA — never below 56 | 68 |
| secondary actions | 94 |
| **total** | **418** |
| available above the keypad mock | 383 |
| **short** | **35** |

And the sheet's own numbers make it worse, not better. It states that at 375 × 667 *"the keyboard
leaves ≈250 of content"*. The artboard's keypad mock is 220 tall and leaves 383 — so against the
**real** keyboard the shortfall is roughly **168px**, not 35.

Three ways out, any one or two of which closes it:

- a **one-line error string** — saves 17
- a **one-line sub copy** on short frames — saves 22
- tightening the **secondary-actions block**, the largest single element at 94

Two of those three are enough. This is a product/design call, not an implementation one — the same
shape as the height-budget finding on SHOWUP-139.

### 3 — The underline wash was wrong on the tutorial screens · **already-shipped work**

`tokens/colors_and_type.css` was missing from the first handoff zip and is present in this one. It
defines `.su-underlined`, which the tutorial screens (SHOWUP-135–139) were built against by
inference. Now that the real thing is readable, five values were wrong:

| | authoritative | what shipped |
|---|---|---|
| shape | `radial-gradient(ellipse at 50% 100%)` | linear gradient |
| colour | orange `.55` → transparent at 70% | orange → pink `#E0567A` → transparent |
| width | the italic word, ±2px | fixed 150–210dp |
| height | `0.32em`, scales with type | fixed 10dp |
| offset | `bottom: -0.08em` | `+4dp` |
| italic weight | **500** | 700 |

The welcome screens are built to the authoritative version. **The tutorial screens need the same
correction** — tracked separately so this ticket's scope stays honest.

### 4 — Press scale and motion timings · **already-shipped work**

`components/shared.jsx` and `CLAUDE.md` both fix values the tutorial screens guessed at:

| | authoritative | what shipped |
|---|---|---|
| press | `scale(0.98)`, 180ms | 0.94 spring |
| easing | `cubic-bezier(.22,1,.36,1)` | platform spring / easeInOut |
| progress | 280ms | 320ms |

`CLAUDE.md` is explicit: *"No hover states — mobile-first. Press scales to `0.98`, snaps back."*

### 5 — The wordmark gradient · **already-shipped work**

`.su-wordmark .su-up` takes `--su-grad-wordmark`
(`linear-gradient(96deg, #812AEC 0%, #D05976 55%, #FE6839 100%)`) clipped to the text. The Welcome
screen shipped with a flat violet `Up`.

### 6 — The italic weight the tokens require is not in the font set

`tokens/colors_and_type.css` puts the `em` inside `.su-underlined` at **font-weight 500**, lighter
than the 700 around it. The bundled Lora set has two italic cuts only:

| file | weight |
|---|---|
| `Lora-Italic.ttf` | 400 |
| `Lora-BoldItalic.ttf` | 700 |

Neither is 500, and there is no variable Lora in the repo to instance one from — I checked; the
static cuts carry no `fvar` table. Both platforms therefore resolve to the nearest available cut
rather than synthesising a weight, which is the honest behaviour: a faux-bolded 400 would be worse
than a real 400.

It reads correctly against the surrounding 700. A genuine 500 italic would be closer still.
**Ask design for a Lora Medium Italic cut**, or for confirmation that 400 is acceptable.

---

## Verified correct

Measured in a real browser across all 23 frames:

- **The CTA does not move between default and error** — identical Y on all six pairs
  (A/B at 317 / 381 / 341, C/D at 336 / 410 / 410)
- Every CTA is exactly 56 tall; none shrinks
- Both helper regions are reserved, never conditional
- Startup headline wraps to **3 lines at 390**, which the ticket calls intended
- No overflow anywhere on SHOWUP-140 or SHOWUP-142, at any of the three sizes
- Startup and Welcome back share one backdrop, value for value, and one wordmark at 26
- Welcome back renders all five `lastUsed` cases: four methods plus unknown → phone with the hint
  row hidden; always exactly four buttons, canonical order, no duplicates
- No-name headline drops the italic span, as specified
- Backdrop layers are full-bleed behind the status bar and home indicator, with no seam
- Colour tokens, type scale and the sunset gradient's 38% midpoint all match `tokens/colors_and_type.css`

## Out of scope, per the tickets

Country list · SMS delivery and provider · server-side code validation and rate limits · account
creation after verify · auth provider SDKs · legal document content · lockout after N failed
attempts · the `NumericKeypad` mock, which is drawn in the sheet only so the artboards show the true
content height and **must not be built** — the platform keypad ships instead.
