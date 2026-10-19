# [Welcome 04] Connect an account — full flow: idle, in flight, OS handoff, linking, success, cancel, errors, conflict

**Parent:** 01 · Welcome & sign-up (epic) · **Type:** Story · **Priority:** High
**Attachments:** `welcome-04-connect-flow-spec-sheet.png`
**Blocked by:** Welcome 03 (phone verification) · the provider-credential sub-tasks below
**Supersedes:** the earlier split into *04 Connect an account* + *05 Account already exists*. One ticket now covers the whole initial connection flow including every outcome. Re-login with an already-linked provider stays separate — that is **Welcome 02**.

## Decided before build — provider buttons are theirs, not ours

**Compliance wins over our visual system. Decided 26 Aug 2026; settled, do not relitigate.** All three provider buttons — and the conflict modal's resolve CTA — are built to Apple's / Google's / Facebook's own current published specification: their asset, mark, permitted fills, title text, minimum size, corner radius, font, at **equal prominence**. **No sunset gradient on a provider button**, no promotion by restyling, no re-tinted marks. The gradient stays on our own CTAs (Continue on F, the skip).

The "tapped provider promoted to primary" behaviour below therefore expresses itself through **position and the disabled / spinner state in the provider's own permitted style**, never a fill swap.

Callouts ⑥, ⑦ and ㉛ on the attached sheet show the old gradient treatment and are **superseded**; the sheet remains authoritative on every other number. Standing rule: `design_handoff_showup/CLAUDE.md` → *Provider sign-in buttons*. Verify against the providers' guidelines as published at build time, not against this ticket.

## Description

The screen the user lands on the instant their phone number verifies. It offers to link a third-party identity so future sign-ins are one tap, and it offers an equally live way to skip. One screen, three providers, ten states — every branch of one attempt, from the tap to each of its four possible endings:

| | State | What it is |
| --- | --- | --- |
| **A** | Idle | three connect buttons + skip, nothing selected |
| **B** | Tapped — in flight | tapped provider promoted, spinner, siblings dimmed, skip still live |
| **C / D** | OS handoff — Apple / Google | **the system owns the foreground** — see "What is not ours" |
| **E** | Linking | token returned, our backend is linking the identity |
| **F** | Success | identity linked, forward CTA into profile creation |
| **G** | Cancelled | user dismissed the OS sheet — neutral, not an error |
| **H** | Error | `network` (never reached the provider) or `declined` (no valid credential) |
| **I** | Already exists — conflict | valid credential, but the identity belongs to an existing account: bottom sheet over A |
| **J** | Conflict resolve | the **owning** provider's sheet re-opened in sign-in mode — **not ours** |

**One ticket, not three, and not two.** A–J are one layout column plus one bottom sheet, driven by a `state` prop and a `provider` prop out of a single source file. The provider is a row in a list, not a screen; the conflict is an ending of the same attempt, not a separate journey — it is only detectable *after* a credential comes back, so it cannot be built or tested apart from the flow that produces it. Shipping A–H without I means shipping a flow with a silent dead branch. The provider-specific work that *is* real is credential and entitlement setup, split out as dev-only sub-tasks at the bottom.

**Routing.** In: successful code verify (welcome 03, state C) — the only entry point. Out: profile creation from **F**'s `Continue`, from the skip in any method-list state, and from an abandoned in-flight request; **the app as the existing account** from **I → J** on success. **There is no back.** The phone is already verified; there is nothing behind this screen. A returning user who signs in with a provider they already linked never sees this screen — that is welcome 02.

## What is not ours — read before estimating

The Apple ID confirmation sheet (**C**), the Google account chooser (**D**) and the conflict resolve sheet (**J**) are rendered by the OS / provider SDK. **We control nothing inside that rectangle:** not the copy, not the type, not the corner radius, not the height, not the animation, not the alerts it raises, not the "Hide My Email" or "Use another account" rows, not Google's data-sharing disclosure sentence. Facebook is not a native sheet at all — it is an app switch or a web auth session, equally not ours.

Consequences for the build:

- The sheets on the spec sheet are **stylized stand-ins** so the flow reads end to end. They are marked `OS-OWNED` in violet. Do not reimplement them and do not spec against their pixels.
- **Never assume a sheet height** — it differs per provider, per OS version, and per number of accounts on the device.
- On iOS the system usually draws its own dim. Ship our scrim only where the platform does not; a double scrim is a visible bug.
- We resume the moment the sheet dismisses, with one of exactly four outcomes: a credential that links (→ E → F), a credential that conflicts (→ E → I), a user cancel (→ G), or a failure (→ H). Anything else is a bug in our handling, not a new state.
- Our privacy promise ("We'll never post or message anyone on your behalf") lives on **F**, in our voice, *after* the sheet is gone. Never inside theirs, never as a retyped version of theirs.

## Behaviour — the part that isn't visual

**Tap (A → B).** The tapped provider is promoted to primary and keeps that position through B → C/D → G/H, so "try again" is always the same target under the thumb. Siblings disable and dim (0.45 / 0.5); the primary shows an 18px spinner and `Connecting to {Provider}…`.

**The skip is never disabled.** Not while a request is in flight, not while a sheet is open, not on error. Connecting an account is optional, so this exit must survive a hung provider — it is the timeout escape. Tapped mid-flight: abandon the request and route to profile creation.

**Linking (E) must not become a dead end.** It has no exit by design, so cap it — **8s** — and fall out to `H · error · network` rather than spinning on.

**Cancel (G) is not an error.** Neutral notice, `--liq-bg-raised`, no red, no shake, no alarm glyph. The user chose this.

**Error (H).** Two kinds, one layout, different string. The failed provider loses its gradient but keeps first position: bg `--liq-bg-elevated`, 1.5px border, label `Try {Provider} again`. **Never auto-retry.** The other two providers and the skip stay fully live — a broken provider must never be the only path forward.

**Conflict (I) is a modal, not a route.** The connect screen stays mounted and unchanged underneath — same idle method list, same order. Dismissing must not reload, re-fetch or reorder anything, and must not add a back-stack entry.

**The resolve CTA names the account's owning provider, not the one the user just tapped.** Tapping Apple on a Google-owned account offers `Continue with Google`. Getting this backwards sends the user round a loop.

**Both conflict strings are interpolated** — the email address and the owning provider. Naming the account *is* the modal's job. If the backend cannot return an address, fall back to the provider alone; never render an empty bold span or a masked placeholder.

**Two exits from the conflict, both explicit.** No "X", **no tap-to-dismiss on the scrim.** An accidental dismiss drops the user into a list where every provider they own raises this same modal again. No danger colours: nothing failed, the account simply exists.

**Resolve (J) turns sign-up into sign-in.** On success the user lands in the app as their existing self — existing profile, matches and Show-up Rate intact. **The phone number just verified is discarded** if it does not match the existing account's. No merge, no second verification, no re-run of profile creation. Cancel inside the sheet returns to **I** with nothing lost; a failure inside it lands on **H** with the modal dismissed.

**Success (F).** The first name comes from the provider. If the provider returned no name (Apple's "Hide My Email" users often share nothing), fall back to `You're **in**.` — never `You're in, null.` One linked identity is enough: no "connect another account" affordance.

**Idempotency.** Double-tapping a provider, or backgrounding the app mid-sheet and returning, must not create two link requests or two accounts. The verified phone number is the account key; the provider identity is attached to it.

**No state in this flow is a dead end.** That is the rule the AC checks.

## Layout — read this before building

⚠ **Layout is flex, not absolute.** Status bar (54, fixed) → content (`flex: 1`, padding `20 24 0`) → home indicator (28, fixed). **No AppHeader, no back button.** Backdrop layers absolute at `z 0`, content `z 1`, scrim `z 2`, OS sheet and conflict sheet `z 3` — all in the *same* relative wrapper, which is what keeps the sheets above the scrim without a portal.

Content column: wordmark → **96** → headline block (top-anchored) → **one `flex: 1` spacer** → notice or error banner → method list → legal line (bottom-anchored).

**The banner and the notice are inserted above the method list, never overlaid.** They consume the spacer, so the primary button's distance from the bottom is identical in A, B, G and H. Verify by toggling states with a ruler on the CTA.

**The conflict sheet is bottom-anchored and content-sized** (`justify-content: flex-end` on a full-inset flex column, margin 8, radius 28). Never give it a height, never centre it: a longer email address grows it upward. Do not clip it, do not scroll inside it, do not truncate the address. Both its buttons are full width and stacked; the secondary has no border so the pair never reads as two equal choices.

**The backdrop is the Startup / Welcome back one** — orange orb top-right, violet orb bottom-left, peach wash top, same values. Not the phone screens' two-layer variant: there is no keypad here, so the bottom glow comes back. Same component, same values, one implementation.

E and F replace the header block with a centred hero block in the same column — same screen, different content, not new routes.

**Short frames.** At 375 × 667 the wordmark → headline 96 collapses first, then the headline steps 40 → 34. Never shrink a button below 56 (skip: 52), never drop a provider, never hide the skip, never scroll. The conflict sheet is ~430 tall and still fits; a longer body grows it upward.

📎 All specs are on the attached **welcome-04-connect-flow-spec-sheet.png**, keyed ①–㉝ across the ten states (red numbers are the failure states, **violet numbers are the parts we do not own**). The design system has colour and type tokens only — there is no spacing scale, so the raw px values are intentional, not an oversight.

> **Reference implementation:** `design_handoff_showup/welcome/screen-connect-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. **States: `<ScreenSocialAuth/>` · `state="tapped"` · `state="sheet"` · `state="linking"` · `state="success"` · `state="cancelled"` · `state="error" kind="network"|"declined"` · `state="conflict"`, each with `provider="apple"|"google"|"facebook"`.**
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## Copy — final strings

Headline: `Welcome to **Show Up**` — "Show Up" italic with the orange underline wash, heart-filled 30 on the baseline
Sub (two lines, hard break): `Connect an account for easier future sign-ins.` / `Or continue and start creating your profile.`
Buttons: `Continue with Apple` · `Continue with Google` · `Continue with Facebook`
Skip: `Skip and continue to profile`
In flight: `Connecting to Apple…` · `Connecting to Google…` · `Connecting to Facebook…`
Legal: `By continuing you agree to our **Terms** and **Privacy Policy**.`
Linking headline: `Signing you **in**…` · Linking sub: `Verifying your {Apple ID|Google account} and setting things up. This takes a second.` · Linking foot: `Don't close the app.`
Success eyebrow: `Apple connected` / `Google connected` / `Facebook connected`
Success headline: `You're **in**, {FirstName}.` — no name returned: `You're **in**.`
Success sub: `We'll never post or message anyone on your behalf. Let's finish your profile in 90 seconds.`
Success CTA: `Continue`
Cancelled: `**Sign-in cancelled.** You closed the {Apple|Google|Facebook} sheet before we could finish.`
Error, network: `We couldn't reach {Apple|Google|Facebook}. Check your connection and try again.`
Error, declined: `{Apple|Google|Facebook} didn't return a valid sign-in. Try again, or use a different method.`
Error CTA: `Try {Apple|Google|Facebook} again`
Conflict headline: `You already have an account.`
Conflict body: `**{email}** is already on Show Up — you signed up with {Provider}. Continue with {Provider} to pick up where you left off.`
Conflict body, no address available: `That account is already on Show Up — you signed up with {Provider}. Continue with {Provider} to pick up where you left off.`
Conflict rule line: `One person, one account. Show-up Rates only work if you can't start over.`
Conflict primary CTA: `Continue with {Provider}` — the **owning** provider
Conflict secondary: `Use a different account`

The conflict rule line is the product reason, not boilerplate — it is why no "create a second account" path is offered. Keep it verbatim. Error voice is informative, never cautionary — no "Wrong", "Failed", "Error", "Invalid". Product terms capitalise exactly: **Show Up** · **Show-up Rate**, never "Show-Up Rate".

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

- [ ] All ten states render from **one** screen component driven by `state` + `provider` + `kind` — no per-provider screen, no per-state route, and the conflict as a modal over the mounted screen rather than a route of its own
- [ ] The provider list is a single ordered data source (`Apple → Google → Facebook`) shared with Welcome back (welcome 02) — **one method-list component, two hosts**; phone is absent here
- [ ] **The primary button sits at the same distance from the bottom in A, B, G and H** — the notice and the error banner consume the top spacer, never push the buttons. Verify by toggling states
- [ ] `Skip and continue to profile` is present, enabled and full-opacity in **every** method-list state including in flight and both failures; tapping it mid-flight abandons the request and routes to profile creation
- [ ] The tapped provider is promoted to primary and stays in first position through in-flight, cancel and error — the list does not shuffle between those states
- [ ] E is capped at **8s** and falls out to `error · network`; it is never reachable as a permanent state
- [ ] G is rendered neutral (`--liq-bg-raised`, no danger colour, no shake); H is rendered with the danger banner and a de-emphasised retry button
- [ ] No auto-retry anywhere; the two non-failed providers and the skip are live in both failure states
- [ ] F falls back to `You're **in**.` when the provider returns no name — verified with an Apple account that shares neither name nor email
- [ ] A conflict response renders **I** and never lands on F; the method list behind is byte-identical before and after a dismiss (no reload, no re-fetch, no reorder) and **no back-stack entry is added**
- [ ] The conflict sheet is content-sized and bottom-anchored; a 40-character email address grows it upward with no clipping, no internal scroll, no truncation and no type shrink — test at 375 × 667
- [ ] The conflict primary CTA names the **owning** provider, verified with a cross-provider case (tap Apple on a Google-owned account → `Continue with Google`)
- [ ] Both the address and the provider are interpolated from the conflict response; the no-address fallback string renders with no empty bold span
- [ ] `Use a different account` is the only dismiss — **the scrim is not tappable and there is no close icon** — and no danger token appears anywhere in the conflict modal
- [ ] Resolve success routes to the app as the **existing** account — no new account row, no second phone verification, no profile-creation re-run; the just-verified number is discarded when it does not match. Cancel inside the resolve sheet returns to **I** with nothing lost; a failure inside it lands on **H** with the modal dismissed
- [ ] The OS sheets are **not implemented** — no reproduction of Apple's or Google's UI anywhere in the codebase, no restyling attempt, no hard-coded sheet height
- [ ] Our scrim is suppressed on platforms that draw their own — screenshot proof of exactly one dim layer on iOS and on Android, in both the OS-handoff and the conflict states
- [ ] Backgrounding the app mid-sheet and returning resumes into the correct state; double-tapping a provider creates exactly one link request
- [ ] The backdrop is the shared Startup / Welcome back component at the same values, not a fourth copy of the layer stack
- [ ] No absolute Y positioning anywhere in the layout
- [ ] Copy matches the strings above exactly, including the two-line sub with its hard break
- [ ] **Provider buttons are provider-compliant, not PNG-compliant** — each of the three, plus the conflict resolve CTA, uses that provider's own published button spec and asset, all at equal prominence: no gradient fill, no restyled or re-tinted mark, none larger or more shadowed than another. Name the guideline version used for each provider in the ticket
- [ ] **The promoted provider is signalled without restyling its button** — position, disabled state and a permitted spinner only
- [ ] **Evidence of done:** attach screenshots at **375 × 667, 390 × 844 and 430 × 932** for states **A, B, E, F, G, H, I** (21 images), plus one conflict capture with a deliberately long email address, plus one real-device capture each of the Apple handoff, the Google handoff and the conflict resolve handoff. Each must show all content visible, nothing clipped or truncated, no scroll, and the skip (or, on I, both buttons and the rule line) fully visible. On 375 × 667, state what collapsed to make room.

## Sub-tasks — dev-only, no design (this is the per-provider split)

Split by provider **here**, not in the screen. Each is credential and entitlement work that blocks state B for that one button, and none of them changes a pixel:

- **Apple** — Sign in with Apple capability, Services ID, key + team config, private-relay email handling, and the "no name returned" case
- **Google** — OAuth client IDs per platform, Android SHA-1/SHA-256 fingerprints per build flavour, consent screen configuration
- **Facebook** — app registration, Login review submission, data-deletion callback endpoint, and a decision on whether Facebook ships in v1 at all
- **Backend** — link-identity endpoint (attach provider identity to a phone-verified account), **conflict detection returning the owning provider and, where available, the account's email address**, and idempotency on double submit

A provider whose sub-task is not done ships **hidden**, not disabled — a dead button is worse than an absent one.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json`, family **B — Auth**. Use them exactly — do not invent names.

⚠ **The whole family was renamed.** What this ticket previously called `social_auth_*` ships as `connect_*`, and the code's names won (decision 20, 7 Sep 2026). The old names in any linked doc are stale.

**One screenview, not ten.** All ten states are one component driven by state, provider and error kind, and the conflict is a modal over it — so it is one `screen_viewed` carrying `state`, not ten screenviews. Note that `state` is currently always `idle`, because the screenview fires on appearance and the state has not moved yet: **either re-fire on each state change, or drop the property.** Open, and the only item on this screen still needing a decision.

| What | Event | Payload |
|---|---|---|
| Screenview | `screen_viewed` | `screen_id: "connect_sso"`, `screen_name: "ConnectSSO"`, `referrer_screen_id`, `state` |
| Provider tapped | `connect_provider_tapped` | `provider` |
| OS sheet shown | `social_auth_os_sheet_shown` | `provider` — **not built** |
| Sheet dismissed | `connect_sheet_dismissed` | `provider`, `stage` — **`stage` missing from the build**; cancel-rate per stage is what tells us whether the offer is worth making |
| Link succeeded | `connect_link_succeeded` | `provider`, `is_new_user` — **`is_new_user` missing** |
| Link failed | `connect_link_failed` | `provider`, `kind: "network"\|"declined"` — the two kinds state H renders. `kind` is the code's name for what this ticket called `reason` |
| Linking timeout | `connect_linking_timeout` | `provider`, `elapsed_ms` — **missing**; the 8s cap is a guess and this is what measures it |
| Conflict raised | `connect_conflict_raised` | `provider`, `attempted`, `existing` — **the mismatch pair is missing**, which is the reason the event exists |
| Conflict resolve tapped | `connect_conflict_resolve_tapped` | `provider`, `existing`, `result: "succeeded"\|"cancelled"\|"failed"` — one event per attempt; **`existing` and `result` missing** |
| Conflict `Use a different account` | `connect_conflict_different_account_tapped` | `existing`, `next_action: "provider"\|"skip"\|"abandoned"`, `next_provider` — **ships with no payload at all** |
| Repeat conflicts in one session | `connect_conflict_repeated` | `count`, `provider`, `providers_attempted` — **`providers_attempted` missing**. Diagnostic only: it triggers no UI |
| Skip tapped | `connect_skip_tapped` | `from_state` (required), `provider` — **ships with no payload**; skip-from-idle and skip-after-error are different signals |
| Legal links | `legal_link_tapped` | `link`, `screen_id` — this screen shows two of the three documents |

**Success and failure now fire from both sides.** `connect_link_succeeded` and `connect_link_failed` are emitted client-side *and* server-side, and **the server row is the one counted** (decision 24) — a client-fired success is lost when the app dies mid-callback, which is exactly when a linking failure matters most. The client rows stay for funnel continuity. Server emission is **not built**.

## Out of scope

The OS sheets themselves, profile creation, the re-login screen (welcome 02 — including the case of a returning user signing in with an already-linked provider), account merging, account recovery, unlinking a provider later from settings, changing the phone number on an existing account, email/password as a method, and any A/B test of the provider order or of whether this screen appears at all.

## Dependencies

- Welcome 03 — the only entry point
- The four sub-tasks above
- Backend link-identity + conflict-detection endpoints
- Profile creation's entry route (the destination of both the skip and F's CTA)
- Welcome 02's post-login destination (where a resolved conflict lands)
- Lora + Manrope in the app font set

## Open (none blocking)

- **Is this screen worth showing at all?** Every provider here is optional, the skip is prominent, and the user already has an authenticated session. Measure the connect rate against the drop-off before defending it — a settings-time offer may convert better than an onboarding-time one.
- **Facebook in v1?** It carries the heaviest review burden and the lowest expected share. Cutting it removes a sub-task and a button.
- **No "connect later" reminder is designed.** A user who skips is never asked again. Decide whether that is intentional.
- **The 8s linking cap is a guess.** Instrument it before fixing it.
- **No support path out of the conflict.** A user who genuinely cannot access the owning account has two dead ends. A third action — "I can't access that account" → recovery or support — is missing, and repeat-conflict tracking will show how badly it is needed.
- **Apple private-relay addresses** show as `…@privaterelay.appleid.com`, which the user will not recognise as theirs. Either suppress the address for relay accounts and use the no-address string, or explain the relay in one clause. Decide before build; it is the last open provider question.
- **Do we reveal that an account exists at all?** Naming an email address to whoever holds the phone is an account-enumeration surface. Security should confirm the trade-off before launch; the no-address fallback string is the mitigation if they say no.
- **Phone-number collision is a different conflict** — the verified number already belonging to an account, detected earlier, in welcome 03. Not designed, not ticketed, and it needs its own decision.
