# Tracking events — the implemented catalogue

**7 September 2026.** Every analytics event that exists in the code today, extracted from the
source rather than from the tickets.

This document is the reconciliation sheet, not the specification. Where it disagrees with a Jira
ticket, that disagreement is listed in [What the code and the tickets disagree
about](#what-the-code-and-the-tickets-disagree-about) rather than silently resolved. Nine such
disagreements exist; five are still open.

`audit/check-tracking-doc.py` fails if this file and the code stop listing the same event names, so
it cannot drift without someone being told.

## Totals

| | Events | Where |
|---|---|---|
| Sign-up and re-login | **23** | `SignUpAnalytics` · Android + iOS |
| Tutorial | **4** | `TutorialAnalytics` · Android + iOS |
| Server | **8** | `src/modules/analytics/events/` |
| | **35** | |

Plus 6 `screen_name` values (one defined and not wired) and 3 `link` values.

## Nothing is being sent yet — and the two halves differ

**Mobile sends nothing at all.** `NoOpAnalytics` is the default tracker on every screen, so all 27
client events are defined, wired, unit-tested and inert. Switching them on is one object in one
place; no screen changes.

**The server is wired to a real sink but gated three ways.** Events route through
`AnalyticsService.track()`, which drops anything without consent (`isAnalyticsAllowed`), drops
anything with no id to attribute it to, and sends to the PostHog EU adapter **only** when
`posthog.enabled` is true and an API key is present — otherwise to the log sink.

So "implemented" and "flowing" are different questions, and the answer differs per half. Nobody
should expect data in a dashboard from either today.

## Conventions

`snake_case`, past tense, on both events and properties, matching the taxonomy the server already
uses (`date_confirmed`, `like_sent`, `account_created`). One taxonomy across client and server, or
the funnel has to be reassembled by hand in the warehouse.

**`screen_name` values are verbatim from the tickets**, capitalisation and spacing included.
`Signup - welcomeback` is lower-case in SHOWUP-142 and stays that way. They look inconsistent
because they are inconsistent in the tickets; normalising them here would make the code disagree
with its own specification. This is the first thing worth fixing in Jira.

---

## Screenviews

One event with a `screen_name` property, not one event per screen. That is what makes a funnel
query possible without naming every screen.

| `screen_name` | Ticket | Screen | Extra properties |
|---|---|---|---|
| `Signup - CreateAccount` | SHOWUP-140 | Startup | — |
| `Signup - welcomeback` | SHOWUP-142 | Welcome back | `last_used` (`phone` / `apple` / `google` / `facebook` / `unknown`) |
| `SSOLogin` | SHOWUP-145 | — | **defined, not wired** — see disagreement 1 |
| `Signup - Phonenumber` | SHOWUP-143 A/B | Phone entry | — |
| `Signup - Codeentry` | SHOWUP-143 C/D | Code entry | — |
| `ConnectSSO` | SHOWUP-144 | Connect account | `state` (currently always `idle`) |

Event name: `screen_viewed`. Fires when the screen becomes visible, and again on return — keyed on
the flow's `step`, which is the behaviour a funnel needs.

## SHOWUP-140 · Startup

| Event | Trigger | Properties |
|---|---|---|
| `signup_create_account_tapped` | "Create free account" | — |
| `signup_log_in_tapped` | "Already have an account? Log in" | — |
| `legal_link_tapped` | any of the three legal links | `link`, `screen_name` |

## SHOWUP-142 / 145 · Welcome back

| Event | Trigger | Properties |
|---|---|---|
| `signup_auth_method_tapped` | any of the four method buttons | `method`, `is_last_used` |
| `signup_get_help_tapped` | "Get help" | — |
| `signup_use_different_account_tapped` | "Use a different account" | — |

`signup_auth_method_tapped` fires for all four methods including the three providers that go
nowhere yet — the intent to use them is exactly what the funnel needs to know.

## SHOWUP-143 · Phone and code

| Event | Trigger | Properties |
|---|---|---|
| `signup_phone_submitted` | "Send me the code" | — |
| `signup_phone_validation_failed` | submitted number rejected | `reason`, `country` |
| `signup_code_submitted` | "Verify code" | — |
| `signup_code_verify_failed` | wrong code | `attempt` |
| `signup_resend_requested` | "Resend" | `seconds_waited`, `after_mismatch` |
| `signup_edit_phone_tapped` | "Edit phone number" | — |

`seconds_waited` is the cooldown's full value minus what remains, because the counter runs down —
and after a mismatch the cooldown is released to 0, which would otherwise read as a full wait.

## SHOWUP-144 · Connect account

| Event | Trigger | Properties |
|---|---|---|
| `connect_provider_tapped` | a provider button | `provider` |
| `connect_skip_tapped` | "Skip and continue to profile" | — |
| `connect_sheet_dismissed` | provider sheet closed before finishing | `provider` |
| `connect_link_succeeded` | link completed | `provider` |
| `connect_link_failed` | link failed | `provider`, `kind` (`network` / `declined`) |
| `connect_linking_timeout` | the 8-second cap in the acceptance criteria | `provider` |
| `connect_conflict_raised` | account already exists | `provider` |
| `connect_conflict_resolve_tapped` | "Continue with `<owner>`" | `provider` |
| `connect_conflict_different_account_tapped` | "Use a different account" | — |
| `connect_conflict_repeated` | a second or later conflict in one session | `count`, `provider` |

`connect_conflict_repeated` carries the running count so the second and third are distinguishable
from the first without the warehouse having to sessionise.

## Tutorial · SHOWUP-117 / 135 / 136 / 137 / 138 / 139

Every event carries `card` (1–6) and `card_name`.

| Event | Trigger | Fires on |
|---|---|---|
| `tutorial_card_viewed` | card becomes visible | all 6 cards |
| `tutorial_cta_tapped` | the forward CTA | cards 1–5 |
| `tutorial_back_tapped` | Back | cards 3–6 (card 1 has none; card 2 hides it) |
| `tutorial_completed` | the final card's CTA | card 6 only |

| `card` | `card_name` | Ticket |
|---|---|---|
| 1 | `welcome` | SHOWUP-117 |
| 2 | `meet` | SHOWUP-135 |
| 3 | `match` | SHOWUP-136 |
| 4 | `match_means_meet` | SHOWUP-137 |
| 5 | `thirty_minutes` | SHOWUP-138 |
| 6 | `show_up_rate` | SHOWUP-139 |

Card 6 fires `tutorial_completed` **instead of** `tutorial_cta_tapped`, so a completion is not
double-counted as a card advance.

## Legal links — 7 wirings, and that is all of them

One event, `legal_link_tapped`, carrying `link` and `screen_name`. Every legal link that exists on
any screen is tracked:

| Screen | Links shown | Tracked |
|---|---|---|
| `Signup - CreateAccount` | Terms & Conditions · Privacy Policy · Legal Notice | 3 / 3 |
| `Signup - welcomeback` | Privacy Policy · Legal Notice | 2 / 2 |
| `ConnectSSO` | Terms · Privacy Policy | 2 / 2 |
| Phone entry, code entry, all 6 tutorial cards | none | n/a |

`link` values: `terms_and_conditions`, `privacy_policy`, `legal_notice`.

## Server events

Emitted by the backend, not the app. All eight route through the same consent-gated path.

| Event | Emitted by | Properties |
|---|---|---|
| `account_created` | `auth.service` — new phone user, and new social user | `auth_method` |
| `like_sent` | `matching.service` | `has_message` |
| `match_created` | `matching.service` | `match_id` |
| `check_in_created` | `check-ins.service` | `has_location` |
| `date_confirmed` | `dates.service` | `date_id`, `venue_attached` |
| `date_completed` | `dates.service` | `date_id` |
| `date_cancelled` | `dates.service` | `date_id`, `had_reason` |
| `notification_sent` | `notification-dispatch.service` — push and email | `channel`, `notification_type` |

No property carries a phone number, an email, a verification code or a token. `date_id` and
`match_id` are internal ids; user ids are hashed (`identity/hash-user-id.ts`) and properties pass
through `events/privacy.ts` before leaving.

---

## What the code and the tickets disagree about

This is the part that matters for a single source of truth. **Four are settled, five are open.**

### 1. Two names for one screen — OPEN

SHOWUP-142 calls it `Signup - welcomeback`. SHOWUP-145 calls it `SSOLogin` and says it "must be
distinguishable from the first-run Startup screenview". **There is one screen.** Both names are
defined in the code; `Signup - welcomeback` is the one wired, on the grounds that 142 describes the
screen that was built.

**Needs:** one name, in one ticket. The other declaration then gets deleted.

### 2. The phone-validation `reason` vocabulary does not exist — OPEN

SHOWUP-143 names three reasons: `too short`, `not a mobile`, `unsupported country`. The validator
produces seven, because it asks libphonenumber rather than measuring length: `empty`,
`notANumber`, `tooShort`, `tooLong`, `invalidLength`, `unrecognised`, `notMobile`.

**Only two overlap.** `unsupported country` is unreachable — there is no supported-country list in
the app and libphonenumber accepts every region. Five of ours have no bucket in the ticket.

The code reports **its own** outcome. Reporting a value the code cannot produce, or collapsing five
distinct failures into one, would make the data describe something that did not happen.

**Needs:** the ticket updated to the seven real outcomes, or a stated mapping.

### 3. Three legal-link events, or one with a property — SETTLED 7 Sep 2026

The tickets list three separate click events. The code ships **one** event with a `link` property.
Confirmed by the product side: keep the one. It answers both questions from one place — which
document, and which screen — whereas three event names record the document and lose the screen.

**Needs:** the three ticket entries replaced by the one event, so Jira stops describing a shape
that was rejected.

### 4. Connect: one screenview or ten — SETTLED

SHOWUP-144 asks, in the ticket text, "is all one screen technically?". Yes: all ten states are one
component driven by `state` + `provider` + `kind`, and the conflict is a modal over it rather than
a route. So it is **one** screenview carrying a `state` property, not ten.

**Needs:** the question removed from the ticket and the answer written into it.

### 5. `state` on the Connect screenview is always `idle` — OPEN

The property exists and is populated, but only ever with `idle`, because the screenview fires when
the step becomes visible and the state has not moved yet. Either it should re-fire on state change,
or the property should be dropped. It currently looks like data and is not.

### 6. Screen names are inconsistent — OPEN

`Signup - CreateAccount`, `Signup - welcomeback`, `Signup - Phonenumber`, `Signup - Codeentry`,
`ConnectSSO`, `SSOLogin`. Three casing styles, two prefix styles. All verbatim from the tickets.

**Needs:** one convention, applied across the five tickets. The code follows Jira here on purpose,
so it changes the moment Jira does.

### 7. Tutorial card 1 is the welcome screen — SETTLED

Its spec sheet is `00-welcome-spec-sheet`, which reads like a card 0, but SHOWUP-117's tracking
section names the screen "Tutorial 1 - Welcome". It is card 1, and the tour's five segments are
cards 2–6.

### 8. Card 6 does not fire a CTA event — SETTLED, worth writing down

It fires `tutorial_completed` instead, so a completion is not also counted as an advance. Any
funnel comparing `tutorial_cta_tapped` counts across cards will see five, not six, and that is
correct.

### 9. No client event has a consent gate yet — OPEN

The server has `isAnalyticsAllowed`. The client has `NoOpAnalytics`, which is not a consent
mechanism — it is the absence of a sink. When a real tracker is passed in, the client needs the
same gate, and consent capture is not built on either platform.

**Needs:** a ticket. This is the one item on this list that is a build task rather than a wording
fix.

---

## Both platforms agree, and that is checked

`audit/check-analytics-parity.py` compares the two catalogues: **26 `snake_case` constants** (the 23
events plus the 3 `link` values) and 6 screen names, byte-identical on Android and iOS.

Beyond the catalogue, every event is also **wired at the same number of call sites on both
platforms** — 36 symbols compared, no differences. So the two apps do not merely define the same
names, they fire them in the same places.

What no checker can do is tell you whether either matches the tickets. That is a reading of prose,
which is what the list above is for.
