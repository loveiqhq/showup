# Requirements conflict review — every screen built so far

27 August 2026, updated 30 August · SHOWUP-117, 135, 136, 137, 138, 139 (tutorial) and 140,
142, 143, 144, 145, 146 (welcome & sign-up)

Requested check: *"double check that there are no conflicts in the requirements … between ticket
description, ui spec png and zip file content."*

**Main source: the Jira ticket description.** Where the zip's ticket files or the reference `.jsx`
disagree with Jira, Jira is treated as authoritative and the disagreement is listed below rather
than silently resolved. Where Jira contradicts *itself* — or one Jira ticket contradicts another —
that is listed too, because no order of authority resolves it.

Verified against the code, not from memory. As of 30 August 2026, 809 automated checks pass across
the five verifiers (`verify-spec.py` 166, `verify-welcome.py` 282, `verify-connect.py` 307,
`check-country-data.py` 16, `check-tutorial-routing.py` 38), alongside 28 unit tests.

---

# A · Conflicts that need a decision

## A1 · SHOWUP-142 and SHOWUP-145 describe the same screen with opposite button rules

This is the significant one, because **both tickets are in PO Acceptance** and anyone testing the
build against 142 will mark it as failing.

| | Says |
|---|---|
| **142** | *"`lastUsed` selects the **sunset CTA** and its hint string."* · *"The other three methods render as **ghost buttons** in canonical order."* |
| **145** | *"provider spec wins, equal prominence, never restyle a mark, gradient reserved for our own CTAs … if our layout conflicts with a guideline the guideline ships"* — and *"the note has to overrule any other information"* |

Those cannot both hold. A ghost button is a restyled provider button, and a sunset gradient on an
Apple or Google button is exactly what the note forbids.

142 also still carries this as an unanswered open item: *"Provider brand guidelines may constrain
button label, icon, and ordering."* — which 145 answers.

**Built:** 145's rule. Phone keeps the sunset pill when it is the promoted method; Apple, Google
and Facebook each keep their own published livery in every position.

**Decision:** confirm 145 supersedes 142 on button styling, and update 142 so it does not fail
review on a rule that has since been reversed.

## A2 · SHOWUP-143 — the reserved height and the error string cannot both be met

| Source | Says |
|---|---|
| **Jira 143** | *"Reserved helper regions … **42** on C / D"* |
| Zip ticket copy | `Code doesn't match. Please check or request a new code.` |
| Reference `.jsx` | `minHeight: 62` — *"62 because the verify error box is 60"* |

The specified string wraps to two lines at every width. Two lines make a 60px box, which does not
fit a 42px reserve — and the whole point of the reserve is that the CTA never moves.

Jira says 42. The copy needs 62. One of them has to give.

**Built:** Jira's 42, with the string shortened to `That code didn't match. Try again.` — one line
at every width, so 42 holds and the CTA stays put. That was agreed in an earlier session, but it
was agreed *before* the reference file existed, so it is worth re-confirming now that the 62 is
visible and looks like a contradiction.

The rule underneath is `reserve = error box + 2`, and the box follows the copy:

| | Copy | Box | Reserve |
|---|---|---|---|
| reference | `Code doesn't match. Please check or request a new code.` | 10 + 18.9×2 + 10 + 2 = **60** | 62 |
| ours | `That code didn't match. Try again.` | 10 + 18.9 + 10 + 2 = **41** | 42 |

**Decision:** keep 42 + the short string, or restore the long string and move to 62. Both are
internally consistent; the current build is the first.

## A3 · SHOWUP-144 and 145 — the note contradicts the ticket body it sits above

Three places. The note says it wins, so this is self-resolving, but it changes **visible text**, so
it should not be silently absorbed.

1. **Button titles.** The ticket asks for `Try {Provider} again` on failure and
   `Connecting to {Provider}…` in flight. Apple, Google and Meta each publish a **closed list of
   permitted button titles**, and neither string is on any of the three. Built: the permitted title
   stays in every state; the spinner and disabled state carry "in flight", the banner carries "try
   again".
2. **The de-emphasised retry button.** *"The failed provider **loses its gradient** … bg
   `--liq-bg-elevated`, 1.5px border."* That sentence was written for the gradient design the same
   ticket removes. Built: the failed provider keeps its own livery; the failure lives in the banner.
3. **"One sunset, three ghost"** (145) — covered by A1.

**Decision:** confirm. All three are one flag (`PROVIDER_COMPLIANT_LABELS`) away from reverting.

## A4 · SHOWUP-145 — "exactly four buttons" vs "hidden, not disabled"

Both appear in the same acceptance criteria list:

- *"Exactly four method buttons render in every state. No duplicate and no missing method."*
- *"A provider whose credential sub-task on welcome 04 is not done is **hidden** here too, not
  disabled. The remaining methods still fill the stack without a gap."*

**Read as:** "exactly four" forbids collapsing methods into a *more options* sheet and forbids
reordering; "hidden" governs a provider that is **not shipping at all**. With everything configured
the default render is four. If Facebook is cut from v1 — an open question on 144 — it is three.

**Decision:** confirm that reading, since the two lines look contradictory to a reviewer.

## A5 · SHOWUP-138 — the copy contradicts itself on what 30 minutes means

- Headline: `Just thirty minutes.`
- Illustration: an **exactly 50%** arc and a `30 min` label — the ticket says an approximate sweep
  is a fail, because *"it reads as 'half an hour'"*.
- Rule row: `30 minutes up — stay if you're vibing.`

The first two say the date **is** thirty minutes. The third says thirty minutes is a **floor**.
The ticket flags this itself and it is still open: *"Confirm which the product does before this
ships as the user's mental model."*

**Decision:** product. This is the user's mental model of the core product mechanic, so it is worth
answering before the tutorial ships rather than after.

## A6 · The tutorial tickets disagree on how many cards there are

| Ticket | Title | Description says |
|---|---|---|
| 117 | 1st card | *"the 5-card onboarding tour"*, *"Show me how → onboarding card 01"* |
| 135 | 2nd card | *"2nd screen of the **6 step** App tutorial flow"* — but *"Back … invisible on **screen 01**"*, *"5 segments, segment 1 filled"* |
| 136 | 3rd card | *"Card 3 of the **6-cards** tutorial"* — but *"segments 1–2 filled"*, *"Next advances to card 03"* |
| 137 | 4th card | *"Card 03 of the **5-card** tutorial"*, segments 1–3 |
| 138 | 5th card | *"Card 04 of the **5-card** tutorial"*, segments 1–4 |
| 139 | 6th card | *"the last card of the **5-card** tutorial"*, all 5 segments |

Two numbering systems are in use at once — Jira's "1st…6th card" and the design handoff's
"welcome + 01–05" — and 135/136 additionally say "6-card tutorial" where 137/138/139 say "5-card".

**No build impact:** the segment counts are unambiguous and consistent, and the build matches them
(1, 2, 3, 4, 5 of 5). The real structure is **1 welcome card + 5 tour cards = 6 screens**, with the
progress bar counting only the 5.

**Decision:** none needed for the code — but the descriptions should be made to agree before QA
reads them, because "card 03" currently means two different screens depending on which ticket you
are holding.

## A7 · The same screen has two different analytics names

| Ticket | Screen name |
|---|---|
| **142** | `Signup - welcomeback` |
| **145** | `SSOLogin` |

Same screen. Epic 11's taxonomy needs one name, and this is the kind of thing that is very cheap to
fix now and expensive to fix after data starts landing.

**Decision:** pick one.

## A8 · SHOWUP-144 asks us a question in the tracking section

> *"Screenview (Screenname - ConnectSSO) — **is all one screen technically?**"*

**Answer: yes.** All ten states are one screen component driven by `state` + `provider` + `kind`;
the conflict is a modal over it, not a route. So the natural instrumentation is **one screenview
with a `state` property**, not ten screenviews.

**Decision:** confirm that is what he wants, because it changes what the funnel looks like.

---

## A9 · SHOWUP-146 does not say what happens when an account conflict is resolved

*Raised 30 August 2026. Decided the same day — recorded here so the decision is not lost, and so
the ticket can be updated to match.*

146 lists three ways to leave the Connect screen and says all three lead to the tutorial:

* skipped without trying
* connected successfully, then Continue
* tried, failed, then skipped

There is a **fourth** exit that 144 builds and 146 does not mention: the account conflict. The user
creates an account, taps Apple, and Apple is already attached to a different Show Up account. They
choose to continue as the owner of that older account.

That person is a **returning member** — they have used Show Up before and have already seen the
tour — so 146's returning-user rule covers them even though its list of exits does not.

**Built:** resolving a conflict is treated as a returning member. No tutorial.

**Decision:** taken 30 August 2026. Confirmed as above.

**Still open:** the ticket description does not carry this. Anyone reading SHOWUP-146 on its own
will not find the rule, and anyone testing against the description will not think to try it. The
description should gain a line, and the acceptance evidence should include this fifth path.

---

## A10 · The eyebrow pill: the spec text and the reference render disagree

*Raised 1 September 2026 by comparing the running app to the 04-thirty-minutes spec sheet. Decided
the same day. Recorded because it will be noticed again.*

The pill above each tutorial headline is a short hug-width chip in our build and a full-width band
in the design's reference render. The spec sheet flags the difference itself, in callout ③:

> "intended hug width (align-self flex-start) — reference render still shows the full-width
> stretch, as on card 01"

So the handoff states the intent (hug) and admits its own render does not match it (stretch).

**Built:** hug-width, following the spec text.

**Decision:** confirmed 1 September 2026 — the spec text wins over the render.

**Why it is worth writing down:** anyone who compares a screen to the reference sees a pill that
looks too short, and reasonably reports it as a bug. It has now been queried twice. The note is in
`TutorialShell.kt` beside the code as well as here.

**Still open, and one for the designer rather than us:** the handoff calls this an open question. It
would be worth having the reference render corrected, so the two sources stop disagreeing — right
now every future reviewer has to be told this same thing.

---

# B · Open questions the tickets themselves raise, still unanswered

These are his own "Open (not blocking)" items. None block the build; several block *shipping*.

## Repeated across five tickets — one answer closes all of them

**Eyebrow width.** 135, 136, 137, 138 and 139 each carry the same item: the reference render
stretches the eyebrow to the full content width, the design-system component is a hug-width pill,
and the fix belongs in the shell. **Built: hug-width** (`align-self: flex-start`), which is what
each ticket recommends. One confirmation retires the item from all five.

## Product facts that ship as claims to the user

| # | Ticket | Question |
|---|---|---|
| B1 | 136 | **"48h" in the dial.** Is the real matching window 48 hours? If it is configurable, the illustration needs a non-numeric treatment. |
| B2 | 137 | **"We pick the place."** Stated as an absolute before place-selection can guarantee a safe public spot halfway. |
| B3 | 138 | **Icebreakers.** Row 3 promises built-in prompts. Do they exist at tutorial launch, or is the row cut? |
| B4 | 139 | **92% ring.** Decorative, but reads as a real number to a user who has no rate yet. |
| B5 | 140 | **`234.000 Dates`.** Needs a real source, and the threshold at which the toggle turns it on. |

## Legal and content

| # | Ticket | Question |
|---|---|---|
| B6 | 137 | **"binding"** is a legally loaded word for a consumer app. Confirm with legal, or swap for "committed". The ticket notes the strength is deliberate. |
| B7 | 139 | **This card is effectively a terms acceptance.** Does the CTA tap need recording as consent, not just tutorial completion? |
| B8 | 139 | **"Without fair notice" is undefined.** Statement 5 imposes a 24-hour penalty on a threshold the user cannot see. Define it, or link the cancellation policy. |
| B9 | 140, 142 | **Legal document URLs.** The links are real and tappable on both platforms but currently open nothing. |

## Layout details

| # | Ticket | Question |
|---|---|---|
| B10 | 135 | Rule rows are specced `nowrap` at 13px and the longest is close to the content width — tight at 375. Allow wrap, or drop to 12.5px on narrow frames? |
| B11 | 135 | Gap between the rule list and the first body paragraph is documented as 16; confirm it should not match the 12 used on cards 02–05. |
| B12 | 117 | Heart → text gap documented as 20, coordinates imply 25. |

## Carried from the 144 audit

Private-relay Apple addresses · account enumeration (naming an email to whoever holds the phone) ·
the 8s linking cap being a guess · no support path out of the conflict modal · whether Facebook
ships in v1 at all.

---

# C · In Jira, not built yet

Listed so none of these tickets gets moved to Done on the strength of the screens looking right.
All of it is expected at this stage — these are layout builds for review, not the shipping app —
but it should be explicit.

## C1 · The five tutorial illustrations are placeholders

Every tour card specifies its illustration geometrically, and the acceptance criteria are precise:

- **136** dual dial — outer sunset arc r84, 50% sweep at −125°; inner violet arc r67, 44% at −45°;
  heart at the overlap; `48h` centre label; `WHEN YOU'RE BOTH FREE` caption
- **137** woven rings — two r46 rings, stroke 15, **woven via the 14r mask at (117,61)**; the ticket
  says a flattened export that stacks one ring over the other is a fail
- **138** coffee and arc — r84 track stroke 4, **exactly 50%** sweep stroke 6, cup, crema, two steam
  curls, `30 min`
- **139** ring — r82, stroke 9, 92% sweep, green `#00C46A → #0A9E5A`, paper disc + check seal

**What is there instead:** the radial glow (so the "no banding" criterion is still testable) and a
neutral block at the right size and scale, including 139's `0.62`.

**The welcome card's heart (117) is real** — drawn from its own curves in code.

Knock-on: 139's *"Ring green matches the Show-up Rate green on the profile — same token, not a
one-off hex"* cannot be met yet, because that shared token does not exist in the design system.

**Decision:** the tickets list the SVG exports as dependencies from design, but they also specify
the geometry completely enough to draw. Do we wait for design's exports, or build them from the
written spec?

## C2 · SHOWUP-143 has no real text input

There is **no text field on either platform** — the four states render from values passed in. So
these acceptance criteria are not met:

- validation on submit; CTA disabled until the value changes
- `Verify code` disabled until all six digits are present
- **OS SMS autofill**
- **pasting a 6-digit code fills the row in one action**
- the country pill **opens a country list**
- the country pill **defaults from device locale** — currently hard-coded to `+49`

What *is* built and correct: all four states, the reserved regions, the CTA not moving, the
480ms one-shot shake, `aria-live` on both helper regions, the
`Enter your 6-digit verification code` label, digits preserved on error, the drawn flag rects
(no emoji), and the resend cooldown released to 0 on mismatch.

## C3 · No analytics on the welcome and sign-up screens — CLOSED 6 September 2026

The five tutorial cards call a tracker. **140, 142, 143, 144 and 145 have none** — and each of
those tickets lists a full tracking section. 144 alone defines twelve events, five of which are
marked `Not built` in the taxonomy.

**Now implemented on both platforms**, transcribed from each ticket's Tracking section: 23 events
across the five screens, wired in `SignUpFlow` and `ConnectFlowHost` with no screen file touched,
`NoOp` by default so nothing is sent. `audit/check-analytics-parity.py` compares the two
catalogues. Three conflicts surfaced while transcribing, in section E below.

## C4 · Evidence screenshots are not attached to any ticket

Every ticket asks for captures at 375 × 667, 390 × 844 and 430 × 932 — 21 images for 144 alone,
plus three real-device provider captures which **cannot** be produced until the Apple Services ID
and Google OAuth client IDs exist.

The HTML sheets render every state at all three sizes from the same numbers, which is useful for
review, but a browser render is not a device screenshot and does not close the criterion.

---

# D · Checked and aligned

Everything not listed above lines up across all three sources. Specifically confirmed against the
code:

- Flex layout with no absolute Y positioning, on every screen, both platforms
- Progress segments 1–5 of 5 across the tour; all 5 filled on the terminal card
- 139's terminal CTA variant (`I'm ready to show up`, sunset circle + violet shadow) added to the
  shared shell rather than forking the nav row
- 139's statement list at gap 11 with the closing paragraph and bolded lead clause
- 140's headline 32 / 44 at 1.05, sub copy Manrope 600 19 in full ink at max-width 320
- **140's social-proof toggle** — the Jira-only requirement that the figure is dynamic and gated
  is implemented (`showSocialProof`), and it is absent from the zip ticket
- 142/145's hint row: 6px dot, 3px ring, Manrope 600 12, `--liq-fg-muted`, directly above the CTA
- Unknown `lastUsed` falls back to phone and hides the hint row
- One shared method list, used by both Connect and Welcome back
- `Show-up Rate` casing consistent everywhere; no `Show-Up Rate`
- No emoji anywhere, flags included
- Copy matches the specified strings exactly, with the single deliberate exception in A2

Two accessibility items were fixed rather than left open — 142 lists *"Legal line contrast should
be checked against WCAG AA"* as an open item. It was checked: `--liq-fg-subtle` measures 3.04:1
against a 4.5:1 requirement, and the legal lines now take `--liq-fg-muted` at 5.03:1. The input
outlines and the skip button's dashed border moved for the same reason under 1.4.11.


---

# E · Conflicts found while implementing tracking (6 September 2026)

Three things the tickets do not settle. All three are implemented one way and recorded here rather
than decided quietly.

## E1 · SHOWUP-142 and SHOWUP-145 name the same screen differently

142 asks for `Screenname: Signup - welcomeback`. 145 asks for `Screenname - SSOLogin`, and adds that
it "must be distinguishable from the first-run Startup screenview".

We have **one** `WelcomeBackScreen` / `WelcomeBackView`. The two tickets describe the same screen —
145 is "Re-login SSO", 142 is "Re Login", and their acceptance criteria are near-identical down to
the 120px gap.

**Implemented as `Signup - welcomeback`** (142), because 142 describes the screen we built. Both
constants exist in the catalogue; only that one is wired.

**Decision needed:** one screenview or two? If the product side wants SSO re-login counted
separately from phone re-login, that is a property on the existing screenview rather than a second
screen name — but it changes what the funnel looks like, so it is not ours to pick.

## E2 · SHOWUP-143's `reason` vocabulary does not cover what our validator produces

The ticket names three reasons: `too short`, `not a mobile`, `unsupported country`.

Our validator produces **seven** outcomes, because it asks the phone metadata rather than measuring
length: `empty`, `notANumber`, `tooShort`, `tooLong`, `invalidLength`, `unrecognised`, `notMobile`.

- **Two overlap**: `tooShort` and `notMobile`.
- **`unsupported country` is unreachable.** There is no supported-country list in the app, and
  libphonenumber accepts every region. Nothing can emit it.
- **Five have no bucket in the ticket**, including `empty` — which is the single most common
  failure, because it is what a user gets for tapping the CTA without typing anything.

**Implemented by reporting our outcome**, not the ticket's vocabulary. Reporting a reason the code
cannot produce, or collapsing five distinct failures into one, would make the data describe
something that did not happen.

**Decision needed:** adopt the seven, or define a mapping. If the three are wanted for a dashboard,
the mapping has to say what happens to the other five, and `empty` in particular should not
disappear.

## E3 · The legal links: one event with a property. DECIDED 7 September 2026

140, 142, 144 and 145 each list `Terms & Conditions`, `Privacy Policy` and `Legal Notice` as
**separate click events**.

**Implemented as one `legal_link_tapped` event with a `link` property**, plus `screen_name` —
because the same three links appear on four screens, and without the screen the taps are
indistinguishable. Three event names per screen would be twelve events for one behaviour.

This records exactly the same information and matches every other property-bearing event in the
catalogue.

**Confirmed by the product side on 7 September: keep the one event.** The reason it wins is that it
answers both questions from one place — "how many tapped Privacy Policy anywhere" and "how many on
the Connect screen" — whereas three event names record the document and lose the screen. Seven
links across three screens would have needed seven names to say less.

Not open any more. Do not re-litigate.
