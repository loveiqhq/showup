# [Profile 10 — later] Stay reachable — additional channels

**Type:** Story · **Status:** Backlog — **not in the MVP** · **Linked:** Profile 10 Stay reachable — MVP (`profile/tickets/10-reachability.md`)

## Why this exists

The MVP ships push plus a demand test (AI call, WhatsApp, SMS as interest checkboxes). This ticket holds the **full** design so none of it is lost. It is picked up **per channel**, once `channel_interest_changed` / `reachability_saved.interest` shows enough demand. The decision rule is set after launch.

**Reference:** `design_handoff_showup/profile/screen-reachability-reference.jsx`, **`scope="full"`** — the kit artboards labelled *Reachability FULL (not in MVP)*.

## What it contains

- **AI concierge call** — consent card, inline phone-number field, and the phone book screen (11) that saves the concierge number to contacts
- **Add dates to your calendar** — replaced by a calendar prompt at the date-confirmed moment (a separate future ticket, not here)
- **WhatsApp and SMS** as live channels, sharing one phone number
- **Email** — dropped from this screen for good (25 Sep 2026); listed here only so nobody re-adds it

## Known issues to resolve before it is built

Carried from the 25 Sep 2026 review of the full screen:

- **Phone ≠ consent.** Decide where OTP verification happens, pre-fill from phone login, and use a country picker with E.164 normalisation.
- **WhatsApp and SMS need the number too.** Move the field from the call card to the screen level.
- **WhatsApp** needs Business API onboarding and templates, respects the 24-hour window, and falls back to SMS when delivery fails.
- **Withdrawal must be as easy as consent** (GDPR Art. 7(3)). Review the confirm dialog for the non-push channels.
- **A legal-basis line per consent**, and consent records that store the version of the text shown.
- **Silence Unknown Callers / Focus / DND** can defeat the call. Needs a "we couldn't reach you" grace rule.
- **Tracking:** `consent_changed` with the `channel` values reserved in `enums.json` §8. Interest (§25) becomes consent only when the user is asked again. **Interest is never migrated into consent.**
