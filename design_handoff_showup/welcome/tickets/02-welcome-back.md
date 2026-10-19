# [Welcome 02] Welcome back — re-login

**Parent:** 01 · Welcome & sign-up (epic) · **Type:** Story · **Priority:** High
**Attachments:** `welcome-02-welcome-back-spec-sheet.png`
**Pairs with:** Welcome 04 (connect an account — the full first-time connection flow). Those two are the flow's **two provider tickets**, split by entry point, not by container: **04 links** a provider to a just-verified number, **this one signs in** with a provider the member already linked. Same buttons, different job, different device state. The shared provider-credential and backend work lives as sub-tasks on 04 and is not repeated here.

## Decided before build — provider buttons are theirs, not ours

**Compliance wins over our visual system. Decided 26 Aug 2026; settled, do not relitigate.** The three provider buttons are built to Apple's / Google's / Facebook's own current published specification — asset, mark, permitted fills, title text, minimum size, corner radius, font — at **equal prominence**. **No sunset gradient on a provider button**, no promotion by restyling, no re-tinted marks.

**Consequence for this screen: when `lastUsed` is a provider, that button does not become the sunset CTA.** The suggestion is carried by the hint row and by lifting the method to first position — nothing else. Phone is *our* method, so when `lastUsed` is phone it keeps the sunset pill. So the stack has a gradient primary in the phone case and none in the three provider cases; the hint row is what makes the suggestion in all four.

The attached sheet shows the old gradient-on-provider treatment and is **indicative on provider-button styling only**; every other number on it still holds. Standing rule: `design_handoff_showup/CLAUDE.md` → *Provider sign-in buttons*. Verify against the providers' guidelines as published at build time.

## Description

The screen a returning member sees on launch. A device that already has an account skips the marketing pitch entirely and lands here: the wordmark, a named welcome, and the four auth methods with the one this device used last time raised to the primary CTA.

This screen and Startup are **one visual space** — same backdrop values, same wordmark at the same size and position. Whichever one launch resolves to, the first frame should look like the same room. Build the backdrop once and share it.

**Routing — this screen is the return path.** No account on the device → Startup (first run). Account present → this screen. The exits are:

- **Primary CTA (last-used method)** → that provider's auth, then the app
- **Any of the three ghost buttons** → that provider's auth, then the app
- **"Get help"** → support / account recovery
- **"Use a different account"** → clears the remembered account and returns to Startup

## Behaviour — the part that isn't visual

`lastUsed` is **device state**, not a user setting: which provider completed auth on this device most recently. It selects the primary CTA and the hint string above it.

- Canonical order is **phone → Apple → Google → Facebook**. The primary is lifted out of that list; the remaining three stay in canonical order below.
- **Always exactly four buttons** — one sunset, three ghost. Never a duplicate, never a missing method, never a reordering beyond lifting the primary.
- `lastUsed` unknown or unsupported → fall back to **phone** as primary, and **hide the hint row entirely** rather than claiming a method the device didn't use.
- The name in the headline is dynamic. No name available → headline is `Welcome back` with no italic span.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → content (`flex: 1`, padding `20 24 0`) → home indicator (28, fixed). Inside the content column: wordmark → headline block, top-anchored; help line → legal line, bottom-anchored; the button stack floats between **two equal `flex: 1` spacers** (≈65 each at 390 × 844) so it sits optically centred in the lower half instead of pinned to the bottom.

**The button stack is the tall element**: 4 × 56 + 3 × 10 gaps + the hint row ≈ 274px. At 375 × 667 the spacers collapse to 0 and **the 120px wordmark → headline gap is what yields** (down toward 48). Never shrink a button below 56, never drop or collapse a method into a "more options" sheet, never introduce scroll.

**A long name adds a headline line.** Budget for three headline lines at 375 × 667 rather than truncating the name.

**The background is full-bleed.** Three layers at `z 0` — orange orb top-right, violet orb bottom-left, peach wash across the top 360px — running *behind* the status bar and the home indicator. Chrome sits above at `z 2`. Anchor real chrome to `env(safe-area-inset-*)`, not to the mock's 54 / 28.

📎 All specs are on the attached **welcome-02-welcome-back-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑨ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/welcome/screen-login-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Copy — final strings

Headline: `Welcome back {name}` — the name italic, with the orange underline wash
Sub copy: `Sign back in to check your availability and see who's free today.`
Hint: `Last login was via phone` / `Last login was via Apple` / `Last login was via Google` / `Last login was via Facebook`
Buttons: `Continue with phone number` · `Continue with Apple` · `Continue with Google` · `Continue with Facebook`
Help line: `Trouble signing in? **Get help** or **Use a different account**`
Legal line: `**Legal Notice** · **Privacy Policy**`

No Terms & Conditions line on this screen — consent was given at sign-up. Product terms capitalise exactly: **Show Up**.

## Type — the italic emphasis in headlines

The italicised word in a headline does **not** inherit the headline's weight. It is a separate spec, and it is the same on every screen in the product:

| | Value |
|---|---|
| Family | `--liq-font-serif` (Lora), italic |
| Weight | **500** — deliberately lighter than the headline's 700 |
| Size / line-height / tracking | inherited from the headline, never overridden |
| Colour | inherited (`--liq-fg`), except where the whole headline is coloured |
| Wrapping | `white-space: nowrap` — an emphasis phrase never breaks across lines |

The wash underneath is a **radial glow, not an underline**: no border, no text-decoration, no solid highlighter bar.

| | Value |
|---|---|
| Shape | `radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)` |
| Height | `0.32em` (scales with the headline) |
| Position | `left: -2px · right: -2px · bottom: -0.08em` |
| Stacking | behind the glyphs — `z-index: -1` on the pseudo-element, `isolation: isolate` on the span |

**Do not re-derive these.** Implement the two rules once as `.su-underlined em` and reuse — they are in `design_handoff_showup/tokens/colors_and_type.css` (search `.su-underlined`), which is the authority. Markup is `<h1 class="su-underlined">Welcome to <em>Show Up</em></h1>`; one emphasis span per headline, never two.

## Acceptance criteria

- [ ] Opens on launch whenever an account exists on the device; a device with no account opens Startup instead
- [ ] Backdrop, wordmark size (26) and wordmark position are **pixel-identical to Startup** — verify by switching between the two, nothing shifts
- [ ] Headline: Lora 700 44 / 1.05, name italic with the orange underline wash; renders correctly for a 2-character name, a 24-character name, and no name
- [ ] Sub copy is Manrope 500 17 / 1.45 in `--liq-neutral-200` (muted, unlike Startup's full-ink sub), max-width 280
- [ ] `lastUsed` selects the hint string and lifts that method to first position; **the sunset fill appears only when `lastUsed` is phone** — a last-used *provider* keeps its own compliant styling
- [ ] All three provider buttons use that provider's own published spec and asset at **equal prominence** — no gradient fill, no restyled or re-tinted mark, none larger or more shadowed than another. Name the guideline version used for each
- [ ] The suggestion still reads in the provider cases — verify by showing the screen to someone cold and asking which method the device used last
- [ ] Exactly four method buttons on every state — no duplicate, no missing method
- [ ] The method list renders from **the same component and the same ordered data source as welcome 04** — this screen adds phone, 04 omits it; button geometry, icon size, label type and gap are identical on both. Two hosts, one component
- [ ] A provider whose credential sub-task (on welcome 04) is not done is **hidden here too**, not disabled — and the remaining methods still fill the stack without a gap
- [ ] Unknown `lastUsed` falls back to phone **and** hides the hint row
- [ ] Hint row: 6px orange dot with the 3px ring, Manrope 600 12 in `--liq-fg-muted`, directly above the CTA
- [ ] Button stack sits between two equal `flex: 1` spacers and stays centred in the lower band on all three sizes
- [ ] **Get help, Use a different account, Legal Notice and Privacy Policy are real tappable links**, each with its own hit area; "Use a different account" clears the remembered account and returns to Startup
- [ ] All three background layers are full-bleed behind the status bar and home indicator; no seam at the status-bar edge
- [ ] No absolute Y positioning anywhere in the layout
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932**, for **each of the four `lastUsed` values plus the unknown fallback**. Each must show all content visible, nothing clipped or truncated, no scroll, and the four buttons plus the legal line fully above the safe area. On 375 × 667, state what the 120px gap collapsed to.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "signup_welcome_back"`, `screen_name: "Signup - welcomeback"`, `referrer_screen_id`, `last_used` — including `unknown` |
| Any of the four method buttons | `signup_auth_method_tapped` | `method: "phone"\|"apple"\|"google"\|"facebook"`, `is_last_used` |
| Get help | `signup_get_help_tapped` | — |
| Use a different account | `signup_use_different_account_tapped` | — |
| Legal links | `legal_link_tapped` | `link`, `screen_id` — this screen shows two of the three documents |

`is_last_used` is the whole point of the screen: it splits "took the suggestion" from "chose another method". It ships correctly today.

**SHOWUP-145 describes this same screen** under a second name, `SSOLogin`, and asks for it to be distinguishable from the first-run Startup screenview. There is one screen and `Signup - welcomeback` is the wired name; the `SSOLogin` declaration and that ticket line should both be deleted (`enums.json` §11).

## Out of scope

**First-time provider connection is welcome 04** — the connect screen after phone verification, its in-flight, linking, success, cancel and error states, and the already-exists conflict modal. Nothing on this screen links a new identity; it only signs in with one already linked.

Also out: the auth providers' own flows and SDK integration (sub-tasks on 04), phone-number entry and code verification (welcome 03), account recovery content behind "Get help", the Startup screen, session expiry and token refresh, and biometric / passkey sign-in.

## Dependencies

- Startup screen (welcome 01) — shares the backdrop and wordmark; "Use a different account" returns to it
- Phone number → verify code screen (welcome 03) — destination of the phone method
- Apple / Google / Facebook SDK integration — the credential and entitlement sub-tasks on **welcome 04**; a provider whose sub-task is not done ships **hidden** here too, not disabled
- The shared method-list component (built with welcome 04, consumed by both)
- The provider-button compliance rule (decided 26 Aug 2026) — `CLAUDE.md` → *Provider sign-in buttons*; it governs this screen and 04 alike
- The member's display name available on the device before auth completes
- Lora + Manrope in the app font set

## Open (not blocking)

- **The backdrop is duplicated between this screen and Startup** (and neither matches `<Atmosphere variant="hero">`, which is .26 / .22 with no peach wash). Promote one shared `startup` atmosphere before a third screen copies the values.
- **The name is shown before authentication.** Anyone holding the device sees the member's first name on the lock-to-launch path. Confirm that is acceptable, or gate the name behind a successful auth.
- **"Use a different account" is destructive-ish** — it clears the remembered account. Decide whether it needs a confirmation step.
- **Four full-width buttons is a lot of primary surface.** Worth measuring whether the three ghost buttons are used at all after the last-used CTA exists, and collapsing them behind "Other ways to sign in" in a follow-up if not.
- **Legal line contrast**: 11.5px at 46% opacity is the smallest, lowest-contrast text on the screen. Check against WCAG AA.
