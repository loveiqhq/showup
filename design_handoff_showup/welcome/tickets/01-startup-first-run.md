# [Welcome 01] Startup — first-run entry screen

**Parent:** Welcome & sign-up flow (SHOWUP-TBD — epic not yet created) · **Type:** Story · **Priority:** High
**Attachments:** `welcome-01-startup-first-run-spec-sheet.png`

## Description

The screen a new visitor sees when the app opens for the first time. It has one job: state what Show Up is and get the visitor into sign-up. No carousel, no auth buttons, no skip — a wordmark, a two-line headline, one line of sub copy, one line of social proof, the legal line, one CTA, and a log-in link for people who landed here by mistake.

**Routing — this screen is first run only.** Once an account exists on the device, launch opens **Welcome back** (re-login) instead, which is where phone / Apple / Google / Facebook live. Both screens share the same backdrop and wordmark, so the transition between them must read as the same space. This ticket covers the first-run screen and the two exits from it:

- **CTA "Create free account"** → sign-up (phone number entry)
- **"Log in"** → Welcome back

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → content (`flex: 1`, padding `20 24 0`) → home indicator (28, fixed). Inside the content column: wordmark → headline block, top-anchored; legal line → CTA → log-in link, bottom-anchored.

**Two `flex: 1` spacers, not one.** The social-proof row sits between them, so it stays optically centred in the empty band instead of being pinned to the copy or to the CTA. At 390 × 844 they resolve to ≈86px each.

**The 132px wordmark → headline gap is the flexible element.** At 375 × 667 the spacers collapse to ≈20px each and the copy wraps one line longer — it still fits. Below that, shrink the 132 (toward ~64) before anything else moves. Never shrink the type, never let the CTA or the legal line leave the safe area, never introduce scroll.

**The background is full-bleed.** Three layers at `z 0` — orange orb top-right, violet orb bottom-left, peach wash across the top 360px — running *behind* the status bar and the home indicator. Chrome sits above at `z 2`. Anchor real chrome to `env(safe-area-inset-*)`, not to the mock's 54 / 28.

📎 All specs are on the attached **welcome-01-startup-first-run-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑨ to the screen. Note the design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/welcome/screen-startup-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Copy — final strings

Headline line 1: `Stop texting for days.`
Headline line 2: `Start meeting today.` — "meeting" italic, with the orange underline wash
Sub copy: `Your availability. Your intent. Your date — today or tomorrow.`
Social proof: `**234.000 Dates** already organized`
Legal: `By creating an account, you agree to our **Terms & Conditions** and acknowledge that you have read our **Privacy Policy**. See our **Legal Notice**.`
CTA: `Create free account`
Log-in link: `Already have an account? **Log in**`

The headline wraps to three lines at 390 (line 2 breaks after "meeting") — that wrap is intended. `234.000` uses the European thousands separator as drawn; confirm the locale rule before shipping (see Open). Product terms capitalise exactly: **Show Up**.

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

- [ ] Renders on first launch only; a device with an existing account opens Welcome back instead
- [ ] Wordmark at 26 — "Up" italic in the wordmark gradient, orange dot — matching Welcome back exactly
- [ ] Headline: Lora 700, line 1 at 32 / line 2 at 44, both 1.05, "meeting" italic with the orange underline wash
- [ ] Sub copy is Manrope 600 19 in full ink `--liq-fg`, not muted, max-width 320
- [ ] Social-proof row sits between two equal `flex: 1` spacers and stays centred in the band on all three sizes
- [ ] CTA is the sunset-gradient pill (`--su-grad-sunset` + `--liq-shadow-violet`), h56, full content width, and the only button on the screen
- [ ] **Terms & Conditions, Privacy Policy and Legal Notice are real tappable links**, each with its own hit area, opening the current documents
- [ ] "Log in" hit area is at least 44px tall without changing the 14px type, and routes to Welcome back
- [ ] All three background layers are full-bleed behind the status bar and home indicator; no seam at the status-bar edge and no visible banding on device
- [ ] No absolute Y positioning anywhere in the layout
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932**. Each must show all content visible, nothing clipped or truncated, no scroll, and the CTA plus the legal line fully visible above the safe area. On 375 × 667, state what the 132px gap collapsed to.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "signup_create_account"`, `screen_name: "Signup - CreateAccount"`, `referrer_screen_id` — **`screen_id` and `referrer_screen_id` missing from the current build**; the label already ships |
| Create free account | `signup_create_account_tapped` | `entry_point` — **missing from the current build**; without it the top of the acquisition funnel has no attribution |
| Already have an account? Log in | `signup_log_in_tapped` | — |
| Any of the three legal links | `legal_link_tapped` | `link: "terms"\|"privacy"\|"legal_notice"`, `screen_id` |

**One legal event, not three.** The three separate click events this ticket previously described were rejected (decision 21): one event answers both questions — which document and which screen — whereas three names record the document and lose the screen.

**Two screen properties, on purpose** (decision 27). `screen_name` is the human label — the thing you search for in the analytics tool — and it ships correctly today; it is never renamed by a developer. `screen_id` is the stable snake_case key that saved funnels bind to, and it is added alongside. Both come from the `tracking/enums.json` §11 registry row; neither is typed at the call site.

## Out of scope

Sign-up itself, Welcome back / re-login, the auth providers, the legal documents' content, first-run permission prompts, and any A/B test of the headline or the social-proof figure.

## Dependencies

- Welcome back (re-login) screen — the destination of the log-in link, and the screen this one must match visually
- Sign-up phone-number screen — the destination of the CTA
- Current URLs for Terms & Conditions, Privacy Policy and Legal Notice
- A real source for the dates figure if it is to stay a number (see Open)
- Lora + Manrope in the app font set

## Open (not blocking)

- **The backdrop duplicates `<Atmosphere variant="hero">` with different values** — this screen hand-rolls orange .32 / violet .28 plus a peach top wash; the token component is .26 / .22 with no wash. Either promote this recipe to a named variant (`startup`) or move the screen onto `hero`. As it stands, two screens claiming the same atmosphere will drift.
- **`234.000 Dates` is a hard-coded claim.** Decide whether it is a live counter, a periodically updated static figure, or copy that should be removed. A stale marketing number on the first screen is a legal and trust risk, and the separator (`.` vs `,`) needs a locale rule.
- **No language or region affordance on the first screen** — a first-run visitor cannot change locale before signing up. Confirm that is intended.
- **The legal line carries three links in 12px type at 46% opacity** — that is the smallest, lowest-contrast text in the product, on the screen where consent is given. Worth a contrast check against WCAG AA before it ships.
- **Wordmark is decorative here.** Confirm it needs no accessible label beyond "Show Up" for screen readers.
