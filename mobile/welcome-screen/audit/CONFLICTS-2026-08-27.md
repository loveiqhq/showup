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

## E4 · "The real you" step_index: the registry and the tickets disagree. CLOSED 16 September 2026

Found 15 September 2026, building SHOWUP-156 and SHOWUP-158.

`enums.json` §2, at registry **1.3.0**, gives:

| step_id | step_index | screen |
| --- | --- | --- |
| `photos` | 1 | The real you · step 1 of **4** · outside the Share-some-details progress bar |
| `prompts` | **10** | **Share some details · step 10** |

Both rows are stale, in different ways, and SHOWUP-158 says so about one of them: "the corrected
`step_index` for photos and media must be added before the ticket is picked up." It has not been.

- **`photos`** has the right index and a stale count. "Step 1 of 4" was true until verify profile
  was dropped from the MVP; the group is three steps now.
- **`prompts`** is in the wrong group entirely. It sat in "Share some details" at step 10 before
  the conversion pass moved it into "The real you" as step 2, and the row never followed.

**Implemented from the tickets**, which are authoritative on behaviour: `RealYouStep.stepIndex`
returns the position in THIS group — photos 1, prompts 2, media 3. Recorded here and in
`RealYouChrome.kt` / `RealYouChrome.swift` rather than silently resolved either way.

**Decision needed:** update §2's two rows. Until then a funnel joining `profile_step_viewed` to
the registry will read `prompts` as a step of a group it is not in.

## E5 · `profile_prompts` has no §11 row. OPEN

Found the same day. §11 (the screen registry) carries `profile_photos` / `ProfilePhotos` and stops.
SHOWUP-158's own tracking section flags it: "The registry row for this screen must be added before
the ticket is picked up."

**Implemented with the values the ticket quotes** — `profile_prompts` / `Profile - Prompts` — in
`ProfileAnalytics` on both platforms, marked as unregistered at the definition. If the design side
chooses differently, that is the one place to correct.

Note that `screen_name` there is the only one in the registry with spaces and a hyphen; every other
row is PascalCase. Worth settling when the row is added rather than after a funnel binds to it.

## E6 · `entry_point` for prompts has no value set. OPEN, and it is the measurement the ticket exists for

SHOWUP-158 calls it "the one measurement this revision exists to produce": whether a topic came
from a **suggestion card** or from **browse all**. It asks for a closed set — `suggestion` |
`browse` | `edit` — in `enums.json`, and there is none at 1.3.0.

**Not implemented, and deliberately not invented.** A free string is exactly what the tracking
rules forbid, and this property is the whole reason the suggestion cards were built. The events it
would hang off (`prompt_topic_selected`, `prompt_answered`) ARE registered and are emitted; only
the property is missing.

Also still missing, and also not invented: an **abandonment** event for a write sheet opened and
closed without saving, which is the precise drop-off the screen is designed against.

## E7 · The prompts ticket's tracking section is out of date. OVERTAKEN 16 September 2026

SHOWUP-158 says "the whole prompt-authoring funnel is unregistered… there is nothing for topic
chosen, write sheet opened, prompt saved, prompt edited, or prompt deleted."

That was true of registry 1.2.0. At **1.3.0** the family "Profile Attributes" carries
`prompt_topic_picker_opened`, `prompt_topic_selected`, `prompt_answered`, `prompt_edited` and
`prompt_removed`, with payloads. Following the ticket would have meant minting five names that
already exist.

**Implemented against the registry**, not the ticket. Recorded here because the next person to read
that section will reach the same wrong conclusion. The ticket also files these under family "E —
Profile Photos/Media"; they are in "Profile Attributes".

## E8 · "The real you" is three steps, not two. DECIDED 16 September 2026

Raised 15 September 2026, building SHOWUP-155/156/158: the group's progress bar has three
segments — photos, prompts, media — and **no media screen is designed or ticketed**. So the bar
shows a third step nothing can reach, which is either correct (a step that is coming) or a bar that
lies about how long the flow is.

**Confirmed by the product side on 16 September: media is planned and arrives in a later ticket.
Keep three segments.**

So `RealYouStep.COUNT` / `RealYouStep.count` stays 3 on both platforms and `Media` stays declared
and unbuilt. Nothing else changes: nothing routes to `Media` — only `Photos` and `Prompts` are ever
passed as the current step — and `resumePoint` has no `Media` case, so a user who finishes prompts
resumes at `Done` rather than at a screen that does not exist. When media is built it gains a
`ResumePoint` case before `Done`, and the bar is already the right length.

Do not "simplify" the count to 2 while the screen is missing. The third segment is the point: a
progress bar that only ever shows steps you have already reached is a counter.

Still open, and separate from this: which `step_id` a single media screen reports. §2 of the
registry carries `media_voice` and `media_video` as two steps, both outside this bar. That is a
question for the media ticket, not this one — see the note on `RealYouStep.stepId`.


## E4 and E7, resolved by registry 1.4.2 — 16 September 2026

Both entries above were written against registry 1.3.0 and are now settled, in opposite directions.
Recorded together because the pair is the lesson: one was the registry catching up with the code,
the other was the code having guessed.

**E4 — the code was right.** 1.3.0 gave `prompts` `step_index: 10`, under "Share some details ·
step 10", which is where prompts sat before it moved into "The real you". The clients shipped
`stepIndex = progressSegment` — photos 1, prompts 2, media 3 — and said so in a comment rather than
matching a number they believed to be stale. §2 at **1.4.2** now reads exactly that, "corrected in
registry 1.3.1 and re-verified against the current flow on 16 Sep 2026". Nothing to change.

**E7 — the code had guessed, and guessed wrong.** At 1.3.0 the only prompt rows that existed were
family F (`prompt_topic_picker_opened`, `prompt_topic_selected` with `topic_id` alone,
`prompt_answered`, `prompt_edited`, `prompt_removed`), so the screen used them as named rather than
minting the five the ticket asked for. 1.4.2 marks all four this screen fired as **SUPERSEDED — DO
NOT FIRE**, deletes the duplicate `prompt_topic_selected` row outright, and makes family E the only
vocabulary for written prompts. The screen was rewritten onto family E; `PromptsTrackingTest.kt` and
`PromptsTrackingTests.swift` fail if a family F name is ever emitted again.

**E9 · Six topic ids were ours and should have been the registry's. FIXED 16 September 2026.**

The reference file carries the fifteen DISPLAY STRINGS and no ids. §17 (`prompt_topic_id registry`),
which does carry them, did not exist at 1.3.0 — so ids were assigned locally, documented as
assigned, and six of the fifteen did not match when §17 arrived:

| ours | §17 |
|---|---|
| `first_date` | `first_date_usually` |
| `ideal_thirty` | `ideal_30_min` |
| `cross_town` | `cross_town_for` |
| `spontaneous` | `spontaneous_plan` |
| `thirty_feels` | `thirty_min_feels` |
| `real_life_more` | `in_real_life_more` |

These are the analytics join key AND what `profile_prompts` rows store, so the correction is a
database migration (`1717000018000-CanonicalPromptTopicIds.ts`) as well as a rename. §17 also
carries `topic_group`, which the clients did not have at all.

**The general lesson, and it is not "read the registry".** The registry did not contain the answer
at the time. It is that a value invented to fill a registry gap has to be recorded as a GAP, not as
a decision — E7 was filed as "not a conflict, a correction", which read as settled and made the
guess harder to find later than it should have been.

## E10 · The three SSO marks share a column. DECIDED 17 September 2026

`Button` in `components/shared.jsx` is `justifyContent: 'center'`, so a button centres its icon and
its label together as one group. The welcome-back and connect screens stack four of those, and the
labels differ in length — `Continue with Apple` against `Continue with Facebook` — so each group
centres to a different width and the provider marks sit a few points apart. Measured off the
design's own render of `02-welcome-back.png`: roughly x127, x118, x97 for the three SSO rows.

**This is not a port bug.** Both platforms matched the primitive exactly and the spec sheets show
the same.

**Product decision, 17 September 2026: line the three marks up.** A few points of drift reads as
sloppy rather than as three separately-centred buttons.

**The centring is kept.** What changed is that the three secondary rows give their labels ONE
shared width — the widest of them, measured in the real font — so three equal-width groups centre
to the same x. Left-aligning the content was tried first and rejected: it lines the marks up by
abandoning the centring the design specifies, which is a bigger deviation than the problem.

The primary row is deliberately excluded. It is the full-width sunset CTA and not one of the set;
padding it to the same width would push its own mark off centre to line up with three buttons it
does not belong to.

Implemented as `labelWidth`, null on every other button in the app. Undoing it is deleting one
argument at two call sites.

---

# SHOWUP-161 · the media step — 17 September 2026

Seven findings from building [Profile 08]. None blocked the build; five are resolved here with the
reasoning written down, and two need a decision from the product side.

## E11 · The 161 attachment ships an OLDER tracking registry than 158 did. RESOLVED

`Show Up Design System (3).zip` carries `tracking/enums.json` at **registry_version 1.4.1**. The zip
attached to SHOWUP-158 carries **1.4.2**, and that ticket's own comment says to pin it: *"Check you
are using the actual registry: enums.json → registry_version: 1.4.2."*

1.4.2 is strictly newer and the difference matters to this ticket specifically. It adds **§23 sheet
`dismiss_method`** — `close · backdrop · swipe · system_back`, canonical for every bottom sheet in
the product — and names this screen's event in the migration note: *"`media_prompt_list_dismissed`
(was cancel|…) are migrated; neither shipped."*

The tracking comment was written against 1.4.1 and therefore cites **§8** for `dismiss_method`.
**1.4.2 supersedes it and §23 is what shipped.** Not a judgement call: 1.4.2 names the event.

## E12 · The reference file has two stale COMMENTS. RESOLVED — the code and the ticket agree

Both are comments contradicting the code beside them, so nothing is actually in conflict, but both
would mislead a reader who trusted the prose:

| Comment says | Code says | Ticket says | Shipped |
|---|---|---|---|
| `RecordedVideoCard` renders "a 16:9 thumbnail" | `aspectRatio: '16 / 10'` | "16:10 thumbnail" | **16:10** |
| Continue "pops to the sunset gradient as a small reward" when both slots are filled | `color="orange"` unconditionally | "It stays the **orange** 52px NextButton; the sunset gradient is reserved for the sheet's commit CTA and the review primaries" | **always orange** |

## E13 · `media_deleted` and the `from` key. RESOLVED — the specification table wins

`enums.json` §8 documents `from` as being *"on media_retaken **and media_deleted**"*. The tracking
comment's payload table carries it on `media_retaken` only.

Shipped without it. Delete is reachable from exactly one surface — a filled card — so `from` would
be a constant on every row, and a field with one possible value measures nothing. The comment is the
specification (*"its event-name table IS the specification"*), so it wins; recorded here rather than
resolved silently.

## E14 · `stop_reason` has no value for an interruption. NEEDS A DECISION

§8 closes the set at two: `user_stop · max_length`. A take ended by a phone call is neither.

The ticket requires an interruption of two seconds or more to land on the review screen, which fires
`media_review_shown`, which requires `stop_reason`. **Shipped as `user_stop`**, because the one
measurement the field exists for is *"a cap that stops most takes is a cap that is too short"*, and
reporting `max_length` would corrupt it. `user_stop` overstates user intent and is the lesser error.

**A third value — `interrupted` — would make the field honest.** For the design side.

## E15 · `preview_source` is one field for two media that rank separately. NEEDS A DECISION

The tracking comment insists, correctly, that *"video and voice rank separately and will not agree
… never publish one number for both"*. But `media_screen_viewed` carries **one** `preview_source`
across both previewed prompts.

Shipped reading `ranked` only when BOTH prompts came from the ranking. A view where one card was a
fallback is not one the ranking can be credited for, and over-reporting `ranked` would corrupt the
exact comparison §22 exists to make; under-reporting only withholds attribution.

**A per-medium `preview_video_source` / `preview_voice_source` would be strictly better.** For the
design side.

## E16 · The media step cannot be a resume point. RECORDED

`resumePoint` returns the first *gap* in the flow. This step is optional, skipping it is a valid
ending, and the account holds no fact that tells a skip apart from a step never reached — so a user
who force-quits on the media screen and relaunches lands on Home, having never seen it again.

Exactly the reasoning that keeps the embrace bridge out of the resume table: *"there is no fact on
the account that says whether it was seen."* Closing it needs a server-side "media step decided"
flag, which is a product decision rather than a client one.

## E17 · "Level metering for the live waveform" is in the inventory and not in the design. RESOLVED

The build inventory asks for *"level metering for the live waveform"*. The reference file draws the
live waveform from a fixed, index-only formula — `useMemo` with no dependency on anything — and the
acceptance criteria require the filled card's 48 bars to be **deterministic**.

Shipped as the reference draws it, on both platforms and from the same arithmetic. Amplitude
reactivity would be inventing design, and it would make the thirty evidence screenshots
unreproducible. If the design side wants a live level, it needs a frame.

## E18 · The permission row still has no artboard. OPEN, as the ticket itself states

*"Ten states are drawn; the two permission modes are not."* Built to the ticket's own binding
recommendation — the reserved status region above the card's CTA, the quiet lilac row, the violet
lock glyph, a 30pt action pill — rather than inventing a third treatment. **Still needs one
spec-sheet frame.**

## E19 · The build inventory has no playback pipeline, and three controls need one. RESOLVED IN CODE, needs a ticket line

The inventory lists both capture pipelines -- *"video capture pipeline"*, *"audio capture pipeline"*
-- and no playback pipeline. The acceptance criteria assume one exists:

- *"Playback on review is **on demand**, repeatable, and the CTA row does not move between plays"*
- *"Voice card, filled: lilac waveform strip with the 44px violet play pip … the `0:08 / 0:14`
  tabular readout"* -- `0:08` is a played position out of a total, which only means anything if the
  card plays.

**What was actually built before this audit was a flag.** `playPressed` set `isPlaying = true`,
fired `media_preview_played`, and played nothing. `playbackFinished` was called by nobody on either
platform, so the flag never came back down. The filled card's play control was wired to that same
function, which guards on `state.take` -- and there is no take on the media screen -- so on Android
it was not passed at all and fell through to a default no-op, and on iOS it reached a guard that
rejected every press. `playedMs` was the literal `0`, so the readout every user saw was
`0:00 / 0:14`. `media3-exoplayer` had been a declared dependency for weeks with nothing referencing
it.

**The analytics half is the part for the design and data side to know about.**
`media_preview_played` was firing on the press rather than on a play, so it reported a preview for
every tap -- and every tap played nothing. That event feeds the same dataset the ticket nominates
for deciding whether 10 and 15 seconds are the right caps. It now fires only when a clip actually
starts.

**Shipped:** a player behind the same seam as the recorders, on both platforms. ExoPlayer and
AVPlayer, one instance per app; the local file first and the uploaded copy second; a real video
surface, because a player with nowhere to draw plays a clip's audio underneath a still picture.
Every press is a play and pressing again restarts -- the design draws one glyph and it is a play
triangle, and a toggle would halve `play_count`.

**Two decisions were taken here that the ticket does not state, and either could be reversed:**

1. **What the card's play button does.** The ticket draws it and never says. It plays the saved
   clip, and the `0:08 / 0:14` readout follows the playhead -- which is the only reading of that
   readout that makes it mean anything.
2. **What happens when there is nothing playable** -- an artefact uploaded and its local copy
   cleaned up, with no URL yet. Nothing happens and no event fires. The alternative, hiding the
   control, would contradict the artboard, which draws it on every filled card.

**There is no registry row for playing back a saved artefact, and the card fires nothing.**
`media_preview_played` is specified as *"one play **on review**, with a running count"*, and
`media_review_shown` is *"the denominator for the whole review screen: of the takes that reached it,
how many were played"*. Firing the review event from a filled card would count plays of a finished
artefact against a denominator of takes that reached review -- corrupting the exact ratio the event
exists to measure, which is the same class of error as firing it for a play that never happened.

So the card plays and reports nothing, and how often people replay their own saved media is
currently unmeasured. **A `media_artefact_played` row, with `type` and a play count, would close
it.** For the design and data side.

**For the design side: one line in the build inventory**, plus the row above. The pipeline exists
now; the gap is that a reader of the ticket would not know either was needed.

## E20 · The notifications ask does not fit every phone, and the agreed ladder does not close it. NEEDS A DECISION

SHOWUP-162 says `It does not scroll`, twice, and names the order of sacrifice if it ever does not
fit: *spacer → list `gap` 16 → 14 → row line to 13 → **then** come back and cut a row. Never shrink
the headline and never let it scroll.*

**Measured, not estimated.** `NotificationsFitTest.print what every device has` renders the screen
at all seventeen frames at three font scales and reports the distance the user must drag to reach
the end of the list. Zero means it fits.

| Frame | 1.0x | 1.3x | 2.0x |
| --- | --- | --- | --- |
| Galaxy Fold cover screen 320 x 638 | **169** | 417 | 1094 |
| small Android (HD) 360 x 592 | **98** | 327 | 992 |
| iPhone SE (3rd gen) 375 x 647 | **5** | 272 | 865 |
| Galaxy A / common Android 360 x 692 | 0 | 227 | 892 |
| iPhone 12 / 13 / 14 390 x 763 | 0 | 156 | 657 |
| iPhone 15/16 Pro Max 430 x 839 | 0 | 0 | 357 |
| the other eleven | 0 | 0–185 | 336–888 |

So at the **default font three of seventeen frames scroll**; at 1.3x, thirteen; at 2.0x, all of
them. The heights are safe-area heights, which is why 375 x 667 appears as 647 — an iPhone SE has
a 20pt status bar and a home BUTTON, not the 585 a notched phone would leave. The ticket's "check
375 x 667 first" is right, and 647 is the number to check against.

**The ladder is worth about 15dp before its last rung** — 8 from the four list gaps at 16 → 14, and
7 from the ten row lines at 13.5 → 13. That closes the iPhone SE's 5 and nothing else. Against 169
on the Fold it is not close, and the last rung is a content decision this ticket keeps for itself:
*then **come back** and cut a row*, with row 1 the reason the user is in the flow and row 5 the one
that protects their time.

**Shipped with the approved values intact, and the CTA pinned below the scroll.** That second half
is the fix for the thing that actually broke. `WelcomeScaffold(scrollWhenTight = true)` on its own
makes the button *reachable*; it does not make it *visible*, and on the Fold this screen opened
with the whole CTA below the fold and nothing on screen saying so. On a **permission ask**, whose
only control is that button, that reads as a broken app.

`WelcomeScaffold(footer =)` puts the button in a fixed band at the bottom of the same column — same
gutter, same 18 above the gesture bar — so the scroll viewport is the screen minus that band. On
the fourteen frames where the content fits, the layout is byte-for-byte what it was. On the three
that are short, the list scrolls under a button that never leaves.

Worth recording how it was caught: **the iOS sweep failed and the Android sweep passed.**
`ScreenFitTest`'s `BELOW THE FOLD` is only an advisory once a screen scrolls, so Android reported
the screen clean while the CTA was off the bottom of it. `NotificationsFitTest` now asserts the CTA
is inside the frame on all seventeen at all three font scales, and locks the overflow table above
so it cannot quietly grow.

**What is still the design side's.** The scroll on those three frames is a direct contradiction of
*never let it scroll*, and no rung short of cutting a row closes it. The real choice is:

1. accept that 320-class frames and every accessibility type size scroll the list, or
2. cut a row — four rows fit the Fold with room — or
3. spend rungs 2 and 3 of the ladder globally, which buys the SE and the 360 x 592 and changes the
   390 artboard by 8dp of list height for frames that never needed it.

This build is (1), because (2) is theirs to make and (3) pre-spends the ladder without finishing
the job.

## E28 · The recording clock is fixed on Android and still counts ticks on iOS. NEEDS FINISHING

A device found it: *"these seconds last much longer than real life seconds"*. Both platforms drove
the recording bar by counting — `delay(tickMs)` / `await tickWait(tickMs)` in a loop, adding
`tickMs` per pass. A sleep guarantees **at least** its argument and never exactly, and each pass
also wrote state that recomposed the capture screen and redrew a live waveform. On an Android
phone the loop ran around 70ms per 50ms tick: a ten-second video took about fourteen seconds, and
the recorder wrote fourteen seconds of footage while the bar said ten.

**Android reads a monotonic clock now**, with a regression test checked in both directions — it
fails against the counting build and passes against this one.

**iOS still counts.** The same change there turned most of `MediaRulesTests` red, and not by
failing an assertion: the suite injects a `tickWait` that returns immediately, so the ticker is a
HOT loop, and making each pass take two lock acquisitions plus a clock read starved the
cooperative executor the rest of the suite runs on. Takes were left mid-recording with no path,
and nineteen tests failed on consequences of that rather than on the clock.

Two attempts, both reverted:

- `MainActor.assumeIsolated` for the test's clock, copying this suite's `Recorder`. It crashed the
  test binary. `Recorder` is called synchronously from the model and never leaves the actor;
  `tickWait` is a non-isolated `@Sendable` closure, so awaiting it hops off the main actor and the
  assumption is false.
- `OSAllocatedUnfairLock`, which is correct and Sendable and still changed the suite's timing
  enough to break it.

**What it needs is a Mac.** Each attempt is a ten-minute CI round trip from here and the failure is
in the harness's interleaving rather than in the fix, which is the one thing a remote log is worst
at showing. The honest fix is probably to give that suite a virtual clock the way the Kotlin side
gets one from `runTest`, so `tickWait` and the recording clock advance together by construction
instead of by two injected closures agreeing.

**What ships meanwhile:** iOS recordings are long by roughly the same proportion Android's were.
The cap still stops them, the artefact still uploads, and the duration written is the counted one.
Nobody has reported it, because nobody has run the iOS app on a device at all.

## E21 · The kit writes letterSpacing two ways, and one of them renders as nothing. FOR THE KIT

`components/shared.jsx` and the profile references carry both spellings:

- bare numbers -- `letterSpacing: 0.08` (9 times), `0.02` (6), `0.01` (6), `0.06`, `0.04`, `0.07`
- em strings -- `'-0.015em'` (7), `'-0.018em'` (6), `'0.08em'` (3), `'-0.005em'` (2)

**`0.08` and `'0.08em'` are both present for the same value.** In React a bare number becomes
**px**, so `letterSpacing: 0.06` on a 10px uppercase label is 0.06 of a pixel -- which is to say
nothing at all. Every negative value is written as em; the positive ones are split.

A designer writing `0.01` cannot mean one hundredth of a pixel, so the bare positives are em that
lost their unit. **This project has read them as em since SHOWUP-161**, where the `REC` and
`0:14 RECORDED` chips ship the reference's bare `0.08` as `0.08em`, and 162's `Premium` tag follows
with `0.06em`.

The consequence worth knowing: **the artboards under-track every uppercase label**, because the
browser renders them literally. A pill measured off the PNG will be a few pixels narrower than the
app's. Nothing is broken; the kit should pick one spelling.

## E22 · Push registration cannot be verified in this build, and the criterion says "verified, not assumed". RECORDED

SHOWUP-162's build inventory names it as the one failure that looks exactly like success:

> **Push registration on grant** — APNs / FCM token acquisition and upload. Granting the permission
> and never registering the device is a silent failure that looks exactly like success on this
> screen.

and the acceptance criterion is *on grant, the device registers for push (APNs / FCM) and the token
reaches the backend — **verified, not assumed***.

**The upload is built; the token source is not, deliberately.** `PushRegistration` on both platforms
takes a `PushTokenSource` / `PushRegistering` seam, calls the generated
`NotificationsApi.registerPushToken`, handles the failure cases and never blocks the flow. What
ships today is `NoPushToken`, which returns nil.

The reason is a standing instruction on this project rather than an oversight: **third-party
providers stay off until they are explicitly turned on.** An FCM token needs `firebase-messaging`
and a `google-services.json`, which means a Firebase project; an APNs token needs the Push
Notifications capability and a paid developer account with a key. The backend already models the
same arrangement — `FcmPushSender` is selected only when service-account credentials are
configured, and `LogPushSender` stands in otherwise. This is the client half of it.

**So the criterion cannot be met by this build, and saying otherwise would be the exact silent
failure the ticket is warning about.** When the services are enabled it is one implementation of
one interface per platform and no other change. Until then the honest status is: upload path built
and tested against a fake source, real source absent, and nothing about the screen reveals the
difference — which is why it is written down here rather than left to a code comment.

## E23 · Rule 1 of the anti-stuck trio assumes a saved position this flow does not have. RECORDED

SHOWUP-162 names one hard stuck state and three lines that prevent it. Rules 2 and 3 are built —
the status guard before the screen is pushed, and the foreground re-read that advances a mounted
screen whose status became determined. Rule 1 is not, and cannot be:

> **Advance the saved flow position when the sheet is raised, not when it returns.**

**There is no per-screen saved flow position in this codebase.** Resume is derived from server
profile facts — what the account holds decides where the user lands — and this screen holds nothing
on the account, exactly like the two bridges and the media step. So there is no position to advance
early and no position that can point at screen 09 after the sheet has been answered.

**The stuck state the rule exists to prevent is therefore unreachable by construction.** A kill
mid-sheet relaunches to wherever the profile facts point, which is never this screen; the ticket's
own cost of rule 1 — "a user killed mid-sheet without answering lands on Stay reachable never
having been asked" — is what happens here too, and Stay reachable's notifications row is the
recovery for it either way.

Recorded rather than silently skipped because an acceptance criterion quotes rule 1 verbatim, and a
reviewer walking that list needs to find the reason here instead of concluding it was missed.


## E24 · The title row is `flex-wrap: wrap` and neither platform wraps. RECORDED

Spec sheet key ⑨: *title row: flex · align-items baseline · gap 8 · **flex-wrap wrap** (the tag
rides here)*. A Compose `Row` does not wrap and a SwiftUI `HStack` does not wrap; both share the
width instead.

**The dangerous half of that is fixed.** Sharing the width means the measure order decides who
loses, and an unweighted `Text` is measured first at the full width — so at 2.0x type on a 320
frame the title took everything and the `Premium` pill was left **25dp of the 123 it needed**, with
the word ellipsised inside a stub of a capsule. Nothing overflowed, so no fit sweep could see it;
`ScreenFitTest` reported the screen clean on all seventeen at 2.0x while that was true.

The title is weighted now and the pill keeps its natural width on every frame at every scale, which
`NotificationsFitTest.the premium pill is never squeezed` asserts.

**What still differs from the reference:** where CSS would drop the pill onto its own line, both
platforms keep it beside a title that wraps to two lines. Row 2 is the only row with a tag, and it
only reaches that width at 1.3x type and above. Nothing is clipped and nothing is unreachable.

Building the real thing means `FlowRow` (still `ExperimentalLayoutApi`) on Compose and a custom
`Layout` on SwiftUI — a shared wrapping primitive, on two platforms, for one tag on one row at
accessibility sizes. Recorded rather than built. If the tag is ever used on a longer title, or a
second row gains one, this stops being a detail.

## E25 · Android drew `--su-grad-lilac` at 135° where the token is 180°, on most of its surfaces. FIXED

    --su-grad-lilac: linear-gradient(180deg, #F1E6FF 0%, #E8DCF5 100%);

`Brush.linearGradient(colorStops)` defaults to `Offset.Zero -> Offset.Infinite`, which resolves to
the drawing area's **top-left to bottom-right**: a 135° diagonal. The token is 180°, which in
Compose is `verticalGradient`. iOS had it right on every surface — `startPoint: .top, endPoint:
.bottom` — so the two platforms have been painting the same token at different angles.

**What made it easy to get wrong:** `--su-grad-sunset` IS 135°, and the default spelling is
correct for it. Every sunset surface in the app reads right with `Brush.linearGradient(colorStops)`,
so carrying the same line over to lilac looks like consistency and is a defect.

**Android had spelled one token three ways**, which is how it ended up at two angles at once:

| Spelling | Angle | Where |
| --- | --- | --- |
| `LilacWash` (the shared brush) | 135°, wrong | media cards ×5, the prompt sheet, the notifications pip |
| `Brush.linearGradient(colorStops = LilacStops...)` | 135°, wrong | photos, ×2 |
| `Brush.verticalGradient(LilacStops...)` | 180°, right | date of birth, prompts |

So the age-confirmation card and a prompt card were correct, and the pip beside them was not —
inside the same flow, from the same token.

**Fixed as one definition rather than five call-site edits:** `LilacWash` is vertical now, and all
four inline copies are gone. The design-system rule that says a token has one definition is exactly
the rule that would have prevented this, and a duplicated *brush* is as much a duplicate as a
duplicated hex.

Touches SHOWUP-154, 156, 158 and 161 surfaces as well as 162's pip. Paint only — no geometry
changes, no fit findings, and the screenshots for those tickets are regenerated from the build.

## E26 · `text-wrap: pretty` is in three acceptance criteria and is implemented on neither platform. RECORDED

SHOWUP-162 asks for it on the lead paragraph and on every row line, and the reference sets
`textWrap: 'pretty'` on both. Neither Compose nor SwiftUI has it, and unlike `text-wrap: balance`
— which this project DID build, as a binary search for the narrowest width that preserves the line
count — nothing here implements it.

**The two are not the same size of problem.** `balance` was built because the ticket states the
outcome it wants: at 390 a greedy wrap gives `Never miss a date with` / `Notifications!` and the
artboard shows `Never miss a date` / `with Notifications!`. There is a right answer and greedy
wrapping gets it wrong, visibly, on the screen's largest type.

`pretty` states no outcome. It is a browser-defined hint that mostly avoids a one-word last line
and slightly reduces raggedness, on body copy of two to four lines. Building it means a second
bespoke wrap algorithm on two platforms, carrying its own copy of each block's width and style —
the kind of duplicate that drifts from the screen it is supposed to describe.

**And no orphan has been observed.** The lead and all five row lines were looked at in the rendered
evidence at 375 x 647, 390 x 763 and 430 x 839, and in the 320 x 2.0 tight frame. Every one breaks
on a phrase; none ends in a single word.

**What would change this:** an orphan at any frame, or a second screen wanting the same hint. Then
it is worth building once, in the shared type layer, rather than twice here.

## E27 · `mobile/welcome-screen/shared/` is a dead snapshot of live files, and it just diverged further. NEEDS A DECISION

Fifteen files under `shared/android/` and `shared/ios/`, last touched **2 September 2026** by a
SHOWUP-140 commit. Neither build references the directory — Gradle's source sets do not include
it and `gen_pbxproj.py` discovers from `ios-app/ShowUpWelcome` — so nothing in it ships, nothing
in it compiles, and nothing in it is tested. There is no README saying what it is for.

**It contains second copies of files that are still being changed.** `shared/ios/WelcomeShell.swift`
still declares `struct WelcomeScaffold<Content: View>`; the live one is
`WelcomeScaffold<Content: View, Footer: View>` as of this branch. `shared/android/WelcomeShell.kt`
has the scaffold without the `footer` slot. `PhoneVerificationScreen` is there twice as well.

**This is the exact shape of the failure this project has been bitten by three times** —
`PillButton`, `SunsetButton`, `StatusBadge` — and the reason the first rule in both CLAUDE.md
files is "search for an existing implementation before creating one". Someone grepping for
`WelcomeScaffold` today finds two definitions and one of them is three weeks stale.

**Not deleted here**, because removing fifteen files is a decision about what that directory was
for and this ticket is a notifications screen. Recorded so it is a decision someone takes rather
than a divergence that widens every time the shell changes. Either it is a handoff snapshot and
wants a README saying so and a date, or it is dead and wants deleting.

