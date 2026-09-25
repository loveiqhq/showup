# [Epic] 01 · Welcome & sign-up

**Type:** Epic · **Parent:** Show Up app · **Handoff folder:** `design_handoff_showup/welcome/`

## Goal

Everything a visitor sees before they have an account: the first-run pitch, re-login for known devices, the phone-number path that creates the account, and the optional account connection that follows it. It ends the moment an authenticated session exists — profile building is a separate epic.

Success is a visitor who understands what Show Up is in one screen and reaches a verified account without leaving the flow.

## Why it is one epic

The screens share a single visual space — the same three-layer backdrop, the same wordmark at 26, the same sunset-gradient CTA — and they route into each other. Built separately they drift; built together the transitions read as one room. The first-run/return split is a routing decision made once, at launch, and belongs here rather than in any one screen's ticket.

## Scope

| # | Screen | Status |
| --- | --- | --- |
| 01 | Startup — first run | speccd + ticketed |
| 02 | Welcome back — re-login (phone / Apple / Google / Facebook) | speccd + ticketed |
| 03 | Phone number entry → verify code (4 states) | speccd + ticketed |
| 04 | Connect an account — the full initial connection flow, Apple / Google / Facebook, 10 states incl. the already-exists conflict | speccd + ticketed |

Screen 04 is **one ticket for the whole initial connection flow**: every ending of one provider tap, including the already-exists conflict modal. The earlier 04 + 05 split is retired — the conflict is only detectable after a valid credential comes back, so it cannot be built or tested apart from the flow that produces it. Screen 02 is the **separate** re-login ticket: different entry, different device state, a session rather than a link. The per-provider work (Apple capability, Google OAuth clients, Facebook login review, the backend link endpoint) sits as dev-only sub-tasks under 04 — not as separate screens, and not duplicated on 02.

Also in scope: launch routing (first run vs. known device), the legal links (Terms & Conditions, Privacy Policy, Legal Notice) as real targets, and the acquisition-funnel tracking at the top of the flow.

## Out of scope

Profile creation and onboarding, the five tutorial cards, the legal documents' content, permission prompts, account recovery and deletion, and any A/B test of the headline or the social-proof figure.

## Standing rules for every screen in this epic

**Order of authority:** the reference file wins on numbers · the ticket wins on behaviour, scope and copy · the PNG wins on nothing.

- One reference `.jsx` per screen in `design_handoff_showup/welcome/`; read values from it, never measure the PNG.
- **No absolute Y positioning.** Nested flex columns, top-anchored copy and bottom-anchored actions.
- Device matrix 375 × 667 · 390 × 844 · 430 × 932 — all content visible, nothing clipped, no scroll, CTA and legal line always above the safe area. 375 × 667 is where it breaks; check it first.
- Background layers are full-bleed behind the status bar and home indicator; anchor real chrome to `env(safe-area-inset-*)`.
- Copy strings are taken character-for-character from each ticket's **Copy — final strings** section. Product terms capitalise exactly: **Show Up** · **Show-up Rate** · **Instant Mode** · **Match Mate** · **Check-In**.

## Decided — do not relitigate

**Provider sign-in buttons follow the providers' own specs, not ours. Decided 26 Aug 2026.** Compliance wins over the Show Up visual system wherever the two disagree: provider asset, provider mark, provider permitted fills, provider title text, provider minimum size — all providers at **equal prominence**, no gradient, no promotion by restyling. The sunset gradient stays on our own CTAs (phone, Continue, skip). "Last used" and "in flight" are expressed through the hint row, ordering, disabled state and a permitted spinner — never a fill swap on a provider button. Verify against each provider's guidelines as published at build time. The binding rule is in `design_handoff_showup/CLAUDE.md` under *Provider sign-in buttons*.

Consequences: on **02** a last-used *provider* can no longer be the sunset CTA — phone, being ours, still can. On **04** callouts ⑥, ⑦ and the conflict resolve CTA ㉛ are superseded by provider-standard buttons. Both spec sheets stay authoritative on every number except provider-button styling.

## Open across the epic

- `234.000 Dates` is a hard-coded marketing claim with a locale-dependent separator — live counter, maintained static figure, or cut?
- The startup backdrop hand-rolls values that `<Atmosphere variant="hero">` already owns. Promote a `startup` variant or move the screen onto `hero` before a second screen copies it.
- No language or region affordance before sign-up — confirm intended.
- The legal line is 12px at 46% opacity on the screen where consent is given. Needs a WCAG AA contrast check.
- **Is the connect screen worth showing at all?** The session already exists when 04 appears and the skip is prominent. Measure connect rate against drop-off; a settings-time offer may convert better.
- **Facebook in v1?** Heaviest review burden, lowest expected share — cutting it removes a button from 02 and 04 and one sub-task.

## Definition of done for the epic

All four screens built against their reference files, the routes between them working in both directions, the three legal links live, tracking firing distinguishable first-run and return screenviews, and the device-matrix screenshots attached on each child ticket.
