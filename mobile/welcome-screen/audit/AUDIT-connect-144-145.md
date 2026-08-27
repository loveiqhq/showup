# Audit — Connect an account (SHOWUP-144) and Re-login SSO (SHOWUP-145)

27 August 2026 · both platforms · against the design system zip *Show Up Design System (2)*

Sources, in the order of authority both tickets state:

| | Source | Wins on |
|---|---|---|
| 0 | The epic's **provider sign-in note**, repeated at the top of both Jira tickets | everything below it |
| 1 | `welcome/screen-connect-reference.jsx` | numbers |
| 2 | The Jira tickets | behaviour, scope, copy |
| 3 | The spec-sheet PNGs | nothing |

The note is not a tiebreaker of last resort — it says so itself: *"There might be information left
in the ticket / the UI spec png or the Zip file, that do not match the note — the note has to
overrule any other information."* Three of the findings below are that rule being applied.

---

## Findings

### 1 · Two of the ticket's strings cannot legally sit on a provider button — **changed**

The ticket asks for `Try {Provider} again` in the error state and `Connecting to {Provider}…` while
a request is in flight, both rendered as the label of the provider's own button.

Apple, Google and Meta each publish a **closed list of permitted button titles**. Apple allows only
*Sign in with Apple*, *Sign up with Apple* and *Continue with Apple*; Google and Meta are equally
restrictive. Neither replacement string appears on any of the three lists, and the note is explicit
that *"if our layout conflicts with a guideline the guideline ships."*

**Implemented:** the permitted title stays on the button in every state. The two things the ticket
wants communicated are moved to where they are allowed to be said:

- **in flight** — the spinner replaces the mark and the button goes to its disabled state, which
  every one of the three guidelines permits;
- **after a failure** — the danger banner above the list carries *"…try again."*

Both replaced strings survive in the source as the false branch of one flag,
`PROVIDER_COMPLIANT_LABELS`, so the decision is one line from being reversed if the guidelines are
re-read and disagree. `verify-connect.py` asserts they are unreachable while the flag is true.

### 2 · The error de-emphasis is a leftover from the gradient design — **not implemented**

> *"The failed provider **loses its gradient** but keeps first position: bg `--liq-bg-elevated`,
> 1.5px border."*

That sentence presupposes the provider button had a gradient, which the same ticket removes. Under
the note, restyling a provider button to our own neutral fill is exactly what *"never restyle a
mark, equal prominence"* forbids — and it would apply in a state the guidelines carve out no
exception for.

**Implemented:** the failed provider keeps its own livery and its first position. The failure is
carried entirely by the banner above the list. Nothing about the button changes.

This is the one finding a reviewer is most likely to read as "not done". It is done — differently,
and on the ticket's own instruction.

### 3 · "One sunset, three ghost" cannot survive the note either — **changed**

SHOWUP-145 asks for *"Exactly four method buttons … one sunset, three ghost"*, and separately for
`lastUsed` to select the sunset CTA. The note reserves the gradient for our own CTAs.

**Implemented:** phone is our own method, so it takes the sunset pill **when it is the promoted
one** and drops to ghost otherwise. A provider never takes the gradient in any position. The stack
therefore has a gradient primary in the phone case and none in the other three — which is precisely
what the zip's own ticket 02 spells out as the consequence. The suggestion is carried by the hint
row and by first position in all four cases.

Still exactly four buttons by default; see finding 4.

### 4 · "Exactly four buttons" vs "hidden, not disabled" — **both, and they do not conflict**

SHOWUP-145 asks for exactly four buttons in every state, *and* for a provider whose credential
sub-task is unfinished to be hidden rather than disabled. Those read as contradictory.

They are not. "Exactly four" forbids collapsing methods into a *more options* sheet and forbids
reordering; "hidden, not disabled" governs a provider that is **not shipping**. The default
`configured` set is all four, so the default render is four buttons. Facebook being cut from v1 —
an open question on 144 — yields three, with the stack still filling without a gap.

Rendered at two, three and four buttons in the preview sheet.

### 5 · The 143 helper reserve: 62 vs our 42 — **no change, and no conflict**

`screen-phone-reference.jsx` was not in the previous zip. It reserves **62** and says why:
*"62 because the verify error box is 60."* Our code reserves 42, which looks like a contradiction.

It is the same rule applied to different copy. The reserve is `error box + 2`, and the box height
follows from the string:

| | Copy | Box | Reserve |
|---|---|---|---|
| reference | `Code doesn't match. Please check or request a new code.` | wraps to two lines → 10 + 18.9×2 + 10 + 2 = **60** | 62 |
| ours | `That code didn't match. Try again.` | one line at every width → 10 + 18.9 + 10 + 2 = **41** | 42 |

Reserving 62 for a 41 box would pad 21dp of dead space into every state and push the CTA down for
no reason. The reserve exists so the CTA does not move, not to hit a particular number. The
derivation is now written into `PhoneVerificationScreen.kt` so the next reader does not re-open it.
If the longer string is ever restored, both numbers move together.

### 6 · Success badge was flush to the corner — **fixed**

The reference puts the connected badge at `right: 4 / bottom: 4` inside the 120 box. Both platforms
had it flush at 0. Android now offsets `(-4, -4)` from `BottomEnd`; iOS offsets 38 from centre
(60 + 38 + 18 = 116, i.e. 4 from 120).

### 7 · The skip button's dashed border failed contrast — **fixed**

The reference draws the skip with `1.5px dashed var(--liq-border)`. That border is the only thing
identifying the control: its `--liq-bg-elevated` fill against `--liq-bg` measures **1.03:1**, so the
button is otherwise invisible. `--liq-border` at 12% ink gives **1.28:1** against WCAG 1.4.11's 3:1
for a non-text UI component.

Moved to `--liq-fg-subtle` (46% ink, **3.04:1**), which is the same substitution already made for
the input and slot outlines on 143. Dash lengths are not specified anywhere in the handoff; 6/4 was
chosen because it reads as dashed at every density.

### 8 · The legal line repeats the 140/142/143 contrast fix — **applied**

`--liq-fg-subtle` at **3.04:1** fails 1.4.3's 4.5:1 for body text. The legal line takes
`--liq-fg-muted` (**5.03:1**), consistent with audit finding 7 on the earlier three screens.

### 9 · A new Swift file would have silently never compiled — **fixed**

`gen_pbxproj.py` carried a **hardcoded list** of source files. A `.swift` file added to the folder
but not to that list is absent from the Xcode target, and the symptom on the Mac is *"cannot find X
in scope"* for a type that plainly exists in the project navigator — a confusing failure to debug
remotely.

The generator now **scans the directory**. It has also moved out of a scratch folder into
`ios-app/gen_pbxproj.py`, so it is in the repo rather than on one machine.

### 10 · `#Preview` is *not* an availability violation — **checker corrected**

Adding `#Preview` to `check-ios-availability.py` flagged 35 pre-existing uses against the iOS 16.0
deployment target. That is a false positive: the macro is declared `@available(iOS 17)` but its
expansion carries that attribute itself, so it compiles against a lower target. The evidence is
direct — those 35 uses were in the build whose log reported the `scrollBounceBehavior` error and
nothing else.

The entry was removed and a comment left in its place so it is not "fixed" back in.

### 11 · The conformance checker had a hole, found by mutating the source — **closed**

Three deliberate regressions were introduced to test `verify-connect.py`:

| Mutation | Caught? |
|---|---|
| Give the conflict sheet a fixed height | yes |
| Make the iOS scrim tappable | yes |
| **Restyle the failed provider button to Ghost** | **no** |

The third is the single most important rule in both tickets, and the checker walked straight past
it — it was only asserting the happy path. It now asserts that the row's variant comes from
`providerVariant()` and that no `PillVariant` literal appears inside the row at all. Re-running the
mutation now fails the run.

### 12 · Exactly one dim layer, and the right owner in each state

The AC asks for our scrim to be suppressed "on platforms that draw their own". That is two
different answers in the two states, so it is two decisions, not one:

- **OS handoff (C / D / J)** — the platform dims. iOS draws its own behind
  `ASAuthorizationController` and the Google sheet; on Android, Credential Manager's sheet dims and
  a Custom Tab covers the screen entirely. Ours is suppressed — `PLATFORM_DIMS_HANDOFF`.
- **Conflict (I)** — the modal is ours, so the scrim is ours, on both platforms.

### 13 · There is no back — implemented as two different things

The ticket says *"There is no back."* On the base screen this is the platform default: the phone is
already verified, nothing is behind the screen, and back leaves the app. A handler that silently
swallowed the gesture would read as a broken app rather than as an absent route.

The **conflict** is the exception: it has exactly two exits and back is neither, so back is
consumed there and only there. No back-stack entry is created, because the conflict is state on a
mounted screen rather than a route.

---

## What was verified, and how

| Check | Result |
|---|---|
| `verify-connect.py` — 144 + 145 conformance | **294 checks, all pass** |
| `verify-welcome.py` — 140/142/143, repointed at the shared list | **257 checks, all pass** |
| `verify-spec.py` — tutorial | 166 checks, all pass |
| `check-swift-structure.py` | 18 files balance |
| `check-swift-arg-order.py` | 30 structs, 59 call sites, all in declaration order |
| `check-ios-availability.py` | no ungated API newer than iOS 16.0 |
| Android `:app:assembleDebug` | **BUILD SUCCESSFUL** |
| Preview sheet | 32 frames across 3 device sizes |

`verify-welcome.py` failed 25 checks immediately after the shared-list refactor, which is the
outcome it exists for: the copy and the canonical order had genuinely moved out of the two screen
files. It was repointed at `AuthMethodList`, not weakened.

**What none of this proves:** these are text and structure checks plus an Android compile. Whether
the Swift renders correctly, and whether the types line up, only Xcode on a Mac can answer.

---

## Evidence still outstanding

The ticket asks for 21 screenshots plus three real-device captures. The HTML sheet renders every
state at all three sizes and is a faithful reproduction of the same numbers, but it is **not** a
device screenshot and does not close that criterion. The three real-device handoff captures cannot
be produced at all until the provider credential sub-tasks are done — Apple's sheet needs a Services
ID, Google's needs OAuth client IDs and SHA fingerprints.

On 375 × 667, what collapses is: the wordmark → headline gap 96 → 44, and the headline 40 → 34.
Nothing else moves, no button drops below 56 (skip: 52), no provider is dropped and there is no
scroll.

---

## Open — for the boss

1. **Confirm the provider-title decision** (findings 1 and 2). This is the only place the build
   knowingly departs from the ticket's literal copy, and it does so on the ticket's own instruction.
   One flag reverses it.
2. **Facebook in v1?** The layout is built for it either way. Cutting it removes a sub-task and a
   Login review submission.
3. **Apple private-relay addresses** — `…@privaterelay.appleid.com` will not be recognised by the
   user as theirs. The no-address fallback string is already implemented and is the cheaper answer.
   Still needs a decision.
4. **Account enumeration** — naming an email address to whoever is holding the phone. Security
   should confirm the trade-off; the no-address string is the mitigation if they say no.
5. **The 8s linking cap is a guess**, per the ticket. It is a named constant and the timeout fires a
   distinct tracking event, so it can be tuned from data rather than argued about.
6. **No support path out of the conflict.** A user who genuinely cannot reach the owning account has
   two dead ends. The skip is not available inside the modal.
7. **Legal document URLs** — the Terms and Privacy links are wired and tappable but currently have
   no destination.
