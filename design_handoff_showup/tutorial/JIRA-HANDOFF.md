# Jira handoff — process and ready-to-paste blocks

How tutorial (and future) screens go from this project to a built feature. Copy from here; nothing needs to be remembered.

## The process

1. **Spec sheet + ticket** are produced in this project and exported to `exports/`.
2. **The handoff folder is committed into the app repo** as `design_handoff_showup/` — reference code, tokens, shell components, spec sheets, tickets. Claude Code reads it from there; a Jira attachment does not work for this.
3. **The Jira ticket points at the reference file** (block A below) and asks for screenshot evidence (block B).
4. **Standing rules live in `design_handoff_showup/CLAUDE.md`**, at the repo root — read on every task, unlike a ticket which is read once.
5. **The ticket's Tracking section cites event names** from `design_handoff_showup/tracking/events.json` — never prose (see below).
6. **After the sprint / before merge**, diff the built screens against the reference: token use, spacing, copy strings character-for-character.

## Order of authority — put this in every ticket

Reference file wins on **numbers** · ticket wins on **behaviour, scope and copy** · PNG wins on **nothing**.

Without this line, three sources of truth become three interpretations.

## Block A — paste under the 📎 line in every ticket

> **Reference implementation:** `design_handoff_showup/tutorial/screen-onboarding-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. **Card index for this ticket: N.**
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

Card index per ticket — the position in the `ONBOARD_CARDS` list inside the reference file (programmers count from 0):

| Ticket | Card index | Tracking `card` | `ONBOARD_CARDS` tag |
| --- | --- | --- | --- |
| 01 Meet in real life | 0 | 2 | Meet PEOPLE in real life |
| 02 Match on availability | 1 | 3 | Match on availability |
| 03 Match means meet | 2 | 4 | Match means meet |
| 04 30 minutes | 3 | 5 | 30 minutes, no pressure |
| 05 Show up, every time | 4 | 6 | Show up, every time |

⚠ **Three numberings, one screen.** The `ONBOARD_CARDS` index counts from 0, the ticket number counts from 01, and the tracking `card` property counts the Welcome screen as 1 — so it runs 2–6. Never carry a number between the three columns without this table. Settled 7 Sep 2026; the mapping also lives in `tracking/enums.json` §14.

Prefer words to numbers? Replace the index line with: *"This ticket is card 01 of 05 — the entry tagged 'Meet PEOPLE in real life' in `ONBOARD_CARDS`."*

## Tracking sections in tickets

**Cite event names; never describe them.** Writing *"Screenview (card index 1 of 5)"* or *"CTA click"* is what caused the September 2026 reconciliation: 35 events shipped under invented names because the tickets never pointed at the taxonomy, and only one name out of 35 matched it. The tickets were readable and the code was correct; they were describing different taxonomies.

The format, established across the tutorial and welcome tickets:

> ## Tracking
>
> Event names come from `design_handoff_showup/tracking/events.json`. Use them exactly — do not invent names, and do not restate a payload here that the registry already defines.
>
> | What | Event | Payload |
> |---|---|---|
> | Screenview | `screen_viewed` | `screen_id: "…"`, `referrer_screen_id` |

Rules:

- **One row per user-visible act,** naming the event and only the payload that needs saying on this screen. The registry owns the types.
- **Mark what is missing from the current build in bold** — *"`dwell_ms` — **missing from the current build**"*. That is what turns a spec into a task.
- **A closed value set goes in `enums.json`, not in the ticket.** Cite the section number (`enums.json` §12) so there is one list, not two. Never type a value list into a ticket: the seven phone-validation reasons became three in a ticket and neither side noticed for a month.
- **If an event does not exist yet, it gets defined in `events.json` first.** A ticket asking for tracking that has no definition produces an invented name, every time.
- **Never run an AI "improve description" over a pasted ticket.** Jira's rewrite makes the prose read more smoothly and **drops the load-bearing parts**: the event-name tables, the file citations, the bold *missing from the current build* markers, the §-references into `enums.json`. What survives is a readable paragraph that no longer says which event to fire. Paste the markdown 1:1 — the tables are the specification, not decoration. (Noticed 7 Sep 2026, after it had been running on the sign-up and tutorial tickets.)
- **A screen gets a registry row before it gets a ticket.** `tracking/enums.json` §11 holds one row per screen: a `screen_id` (stable snake_case key, assigned once, never edited, never re-used) and a `screen_name` (the human label, in this ticket's words). Add the row, then write the ticket citing both. A screen with no row is how an event name gets invented.
- **Screen names are for humans and ids are for machines — carry both.** `screen_name: "Signup - CreateAccount"` is what someone searches for in the analytics tool a year from now; `signup_create_account` is what a saved funnel binds to so a rename doesn't break it. Never collapse them into one property, and never ask a developer to "tidy up" a label: renaming a screen changes the label and leaves the id alone. Naming rule: `<Flow> - <Screen>`, flow prefix from the epic (decision 27).
- **Reconciliation direction:** for a flow that is built, the code is the truth and `events.json` is regenerated to match it. For an unbuilt flow, `events.json` is the truth. Decided 7 Sep 2026 — full log in `tracking/RECONCILIATION.md` and `requirements.json` → `decision_log` 20–26.
- **Sections written ahead of their ticket live in `exports/`.** ✅ **Used 16 Sep 2026** — the Media section below was pasted into `exports/profile-08-media-ticket.md` verbatim, with three registry points added under an *Added when the ticket was written* heading (the three §11 screen rows, the unreachable `caption_*` events, and the missing permission events). When a pre-written section is consumed, paste it 1:1 and put anything new in a marked block below it, so the two files never drift into two versions of one section. The Media screens' Tracking section is `exports/profile-08-media-tracking-section.md` — taxonomy v1.4, 16 Sep 2026: four new family-E events for the prompt list and the post-Stop review screen, `media_inspiration_opened` respecified as `media_prompt_list_opened`, `prompt_id` renamed `media_prompt_id` against the new §20 registry, and `screen_id` made required on `profile_step_skipped`. Paste it as-is when Profile 08 is written.

## When a ticket is longer than Jira allows

**A Jira description is capped at 32,767 characters.** Profile 08 hit it at 36,266 and could not be pasted at all. The split, and the shape to reuse:

- **Split at `## Tracking`** — it is the one section that is self-contained, already written as a standalone block, and already has its own file in `exports/`. Never split mid-behaviour or mid-AC.
- **The Tracking section becomes the ticket's first comment**, pasted 1:1 and pinned, exported as `exports/<flow>-NN-<slug>-tracking-comment.md` and committed beside the ticket as `<flow>/tickets/NN-<slug>-tracking-comment.md`.
- **The description keeps a `## Tracking` heading** that says where the section is, states it is a split by length and not by authority, and repeats the two rules most likely to be broken in a hurry (here: `type` on every event, and never run "improve description").
- **Both files are updated together.** A tracking change that lands in one is a ticket with two taxonomies, which is the failure this whole process exists to prevent.
- Check the length before handing a ticket over: `exports/profile-08-media-ticket.md` is 27.8k with the split, and the comment is 10.1k.

## Multi-state screens

A screen with failure or empty states is **one ticket and one spec sheet**, not one per state: the states share a layout column, and splitting them is how a CTA ends up at a different Y in the error state. Put the states side by side on the sheet (A / B / C / D captions, danger-red callout numbers for the failure states), name the reference file's props for each state in block A, and require a screenshot per state in block B. Screen 03 of the welcome flow is the worked example.

## When one screen becomes more than one ticket

The split is by **layout and behaviour**, never by data:

- **Variants of one column are one ticket.** Failure states, empty states, and a list whose rows differ only by which provider they name (Apple / Google / Facebook) are `state` and `provider` props on one component. Splitting them is how the same CTA ends up at three different Y positions.
- **A different container is *not* automatically a different ticket.** What decides it is whether the two can be built and tested apart. The connect screen and the already-exists conflict modal over it were briefly split (welcome 04 + 05); they are now **one ticket**, because the conflict is only detectable after a valid credential comes back — there is no way to build the flow without it, and a ticket that ships A–H leaves a silent dead branch. Fold a modal into its host ticket when it is an *ending of the same attempt*; give it its own ticket when it has its own entry point.
- **Split by entry point, not by container.** Welcome 02 (re-login with an already-linked provider) is a separate ticket from welcome 04 (first-time connection) because it enters from launch rather than from verification, reads device state (`lastUsed`) that 04 does not, and ends in a session rather than a link. Same provider buttons, different job.
- **Per-provider work that is real goes in the ticket's build inventory, not in sub-tasks.** Credential and entitlement setup — Apple capability + Services ID, Google OAuth clients + SHA fingerprints, Facebook login review — blocks one button each and changes no pixels. List it in the ticket under the screen it gates. A path whose item is not done ships **hidden**, not disabled.
- Two tickets sharing one reference file is fine; say so in block A and name each ticket's state props.

## Screens with OS-owned UI

When the flow hands off to an Apple / Google / Facebook sheet, the ticket has to say loudly where our surface stops. The convention, established on welcome 04:

- The mock draws a **stylized stand-in**, never a reproduction of the provider's UI, and the artboard is framed in violet with an `OS-OWNED` tag.
- Callouts for those parts are **violet-numbered** (alongside black for ours and danger-red for failure states) and say what we do not control: copy, type, radius, height, animation, the alerts it raises.
- The ticket carries a **"What is not ours"** section before the behaviour section, and an AC that the sheet is *not* implemented.
- Never spec a sheet height. It differs per provider, per OS version and per account count.
- **Provider brand rules override our spec sheet — settled 26 Aug 2026.** Provider buttons are built to the provider's own published spec at equal prominence; our gradient is reserved for our own CTAs.
- **A permission surface is not one boolean — and the first question is whether the permission is needed at all. Settled 9 Sep 2026 on profile 06.** Before designing any permission state, check what the platform's *modern* API needs: iOS `PHPickerViewController` and the Android 13+ photo picker run out-of-process over the whole library and need **no permission**, so a screen that uses them has **no library permission states to design** — no access card, and no iOS limited-access case. Profile 06 originally shipped a limited-access banner; it was **deleted, not redesigned**, because it explained a state the screen cannot enter. Ask "do we need this permission?" before "what does denial look like?", and a whole column of states disappears.
  **Ask where the flow actually runs before you spec a permission matrix — settled 21 Sep 2026 on profile 09.** The notifications ask was first written with five status rows; profile creation only runs **once, on a fresh install**, which clears any prior grant on both platforms, so four of them could not occur and the matrix collapsed to one real path. What survives is a **guard, not a design**: the platform with no runtime permission to ask for (Android ≤ 12), and the already-determined case (restore-from-backup, or killed mid-sheet) — because the system dialog is shown **once per install** and a screen whose only button raises a dialog that will not appear is a dead end. Decide **before the screen is pushed**, skip silently (no flash, no toast), put the recovery on the screen that owns the setting afterwards, and phrase the AC as a prohibition — "skipped" is invisible in a screenshot. **Label a guard as a guard**, so nobody designs states for it.
  For the permissions that genuinely remain (camera, always; old-Android library), the app **always knows its exact status** and **never gets to choose the toggle the user lands on** — neither platform exposes a deep link to a single permission row. So: *can still ask* → the button **re-prompts in-app**, no Settings trip, name no toggle. *Blocked* → the button opens Settings and the copy **names the platform's own row**. Never one label for both behaviours.
  Two more rules that cost a sprint each when missed: **re-read the status on every foreground** (the classic bug is a user who granted access in Settings returning to the blocked card), and **scale the treatment to what is actually blocked** — a denied camera on a screen whose library path still works blocks *nothing*, so it gets a quiet row in the sheet the user just opened, not a grid replacement. Put the explanation where the user asked the question. The ticket carries the full status → behaviour matrix as a table; prose cannot hold seven rows.
- **A standing decision goes in `design_handoff_showup/CLAUDE.md`, not only in a ticket.** A ticket is read once, at build time, by one person; CLAUDE.md is read on every task by everyone including Claude Code. The pattern used for this one, and to reuse: the binding rule in CLAUDE.md → the decision with its date and consequences in the flow's EPIC → a *Decided before build* block at the top of each affected ticket naming the callouts it supersedes → an acceptance criterion that makes it checkable.
- When a spec sheet is overruled by an external guideline, say so **in the ticket, against the callout numbers**, and mark the sheet indicative *for that property only* — never let "the PNG is wrong here" become "the PNG is unreliable".

## What the developer actually receives

**This project's handoff is ticket + attachments, and no Jira sub-tasks.** One developer builds the screen with Claude Code reading the ticket content, so everything a task would have carried is written **in the ticket** — as a *Build inventory* section naming the pixel-less work (pipelines, permission readers, entitlements, server jobs), with the standing rule that a path whose item is not done ships **hidden**, not disabled. Never split work into sub-tasks nobody opens.

**Every ticket gets two attachments:** the handoff folder as a **zip**, and the screen's **spec-sheet PNG**. The zip is what makes the file citations resolve for Claude Code; the PNG is what makes the ticket reviewable by eye. The zip is not a substitute for the ticket citing files by path — that citation is what gets it opened — and the PNG still wins on nothing. If the sheet is not exported yet, attach the zip, say so on the Attachments line, and attach the PNG when it lands.

Three things reach the developer, and only the first is a file transfer:

1. **The handoff folder**, zipped or committed — `design_handoff_showup/`. It has always contained `tracking/` (`events.json`, `properties.json`, `enums.json`, `requirements.json`, `RECONCILIATION.md`, `README.md`). **The taxonomy was never missing from the bundle** — it was missing from the tickets, which described tracking in prose instead of citing it, so nobody opened it. A zip does not get read; a ticket line does.
2. **The ticket**, whose Tracking section cites event names from that folder.
3. **`tracking/RECONCILIATION.md`** when there is a code delta — the per-event task list, generated from `events.json`.

Nothing needs to come back the other way as a routine step. When the code changes tracking, the useful return is either the repo recorded here (then the diff is automatic) or a regenerated catalogue of what the code emits, which can be re-diffed against `events.json`.

## Screens outside the tutorial

The tutorial is five payloads through one shell, so its five tickets share one reference file and are told apart by a card index. Every other screen is its own screen: **one reference file per screen, and no card-index line in block A** — delete it rather than leaving it at 0.

Each flow gets a sibling of `tutorial/` with the same three parts and the same order of authority:

```
design_handoff_showup/<flow>/
  screen-<name>-reference.jsx     ← extracted from ui_kits/show-up/, header comment says what matters
  tickets/NN-<name>.md
  spec-sheets/NN-<name>.png
  README.md                       ← flow-level rules + screen status table
```

| Flow | Folder | Screens ticketed so far |
| --- | --- | --- |
| Tutorial (5 cards) | `tutorial/` | 01–05 |
| Welcome & sign-up | `welcome/` | 01 Startup — first run · 02 Welcome back — re-login · 03 Phone verification (4 states) · 04 Connect an account — full flow (10 states, incl. the already-exists conflict) |
| Profile creation | `profile/` | 01 Name (3 states) · 02 Email (3 states) · 03 Verify email (2 states) · 04 Date of birth (4 states, incl. the inline age confirmation) · 05 Embrace — build your profile (1 state, the bridge) · 06 Photos (7 states, incl. the source sheet and the two library permission modes) · 07 Prompts (8 states, incl. both sheets) · 08 Media (10 states, incl. both capture views and both review screens) · 09 Notifications permission ask (1 state) |

Naming in `exports/`: `<flow>-NN-<screen-slug>-spec-sheet.html` / `.png` / `-ticket.md`. The tutorial's five keep their original `NN-<slug>-…` names.

**Epics.** A flow with more than a handful of screens gets a parent epic description at `exports/<flow>-00-epic.md`, copied to `<flow>/tickets/00-epic.md`. It carries the flow shape, the story list with state counts, the **built-once inventory** (the shared shell, scaffold, error card, state machine — a second copy of any of them is a bug), the flow-level rules every story inherits, the device matrix, dependencies, an epic-level definition of done, and the open decisions that change the epic's shape. It does **not** repeat per-screen values. Written once per flow, updated when a flow-level rule changes. `profile/tickets/00-epic.md` is the model.

**A ticket may ship before its spec sheet, but it has to say so.** Profile 08 was written that way and the sheet followed the same day: while the PNG was missing, the Attachments line named it **and marked it not-yet-exported**, and the ticket said nothing in it depended on the PNG. That is honest and buildable; a ticket citing an attachment that does not exist is neither. When the sheet lands, update the Attachments line and the 📎 paragraph in the same pass — a ticket that still says *pending* after the PNG is attached is worse than one that never said it.

**Sheet width follows the frame count, and ten frames still fit 1800** — four per row at 392 + 46 gap. Beyond four per row the value rows lose the line breaks they were written with, so wrap rather than widen.

Making one: copy the nearest `ui_kits/show-up/_spec-*.html` to `_spec-<flow-code>NN-<slug>.html`, point it at the screen, re-key the callouts, export the PNG at 1280 wide, then write the ticket from the sheet. The kit is the source of truth — change copy or layout there first, then re-export, then update the ticket. Never the other way round.

**Sheet width follows the frame count**, so the value rows keep the line breaks they were written with: 1280 for one frame, **1560 for two**, 1800 for four. Export by snapshotting `#sheet` at 2×, not by screenshotting the viewport.

## Bridge screens — a screen that is not a step

A transition screen between two groups (profile 05 is the worked example) is **one ticket like any other**, with three things that are easy to get wrong:

- **No header and no progress bar, and the ticket has to say so loudly** — as an acceptance criterion, not a note. "Belongs to neither progress bar" is not something a developer will infer from a PNG; the absence reads as an omission. Both ACs are phrased as prohibitions ("There is no `AppHeader` on this screen").
- **It gets a `screen_id` row in §11 and deliberately gets no `step_id` row in §2.** A bridge is a screen but not a step. The ticket lists the events that must NOT fire — `profile_step_viewed`, `profile_step_completed`, and any second `profile_build_started` — because a phantom step in the completion funnel is invisible until someone reads the funnel.
- **No CTA event.** The tap is the screen's only act and its only exit, so the next screen's `screen_viewed` + `referrer_screen_id` already measures it. Say that in the ticket, so nobody invents `cta_tapped` to fill the gap.

When a bridge breaks its own flow's rules — 05 carries the ambient backdrop and a sunset CTA, against profile README rules 5 and 7 — use the standing-decision pattern: the exception written against the rule in the flow README → a **Decided before build** block at the top of the ticket naming the callouts → violet callout numbers on the sheet for the exception, alongside black for the ordinary values → an AC that makes each one checkable.

## Screens whose keyboard is open

Every screen in `profile/`'s "The basics" group renders a **mocked** keyboard in its reference file, for one reason: the artboard has to show the true content height above it. The conventions:

- The ticket carries a **"What is not ours"** section naming the keyboard, and an AC that the mock is *not* implemented.
- **Never spec a keyboard height.** State the mock's height (286 + 28 home indicator at 390 × 844) as a budget note only, and say which element absorbs the difference — on these screens, the single `flex: 1` spacer.
- Suggestion strip, emoji/dictation chrome and the action-key label are the OS's. We request the action (`Go` / `Done`) and the capitalisation hint; we style none of it.

## When a decision changes a ticket that is already written

A decision taken while writing ticket N often invalidates a number in ticket N−1 — the prompts ticket cutting the group's progress bar from 4 segments to 3 is the worked example. **Do not silently edit the older ticket and hope.** The pattern:

- **The new ticket carries the decision** in its *Decided before build* block, and says explicitly which other tickets change and how.
- **The older ticket gets a ⚠ superseded line** at the point the number appears, naming the date and the ticket that overrode it — never a quiet find-and-replace, because a developer who read it last week remembers the old number.
- **The affected acceptance criterion is rewritten in place**, with the supersession in the text: *"StepProgress is `steps={3} current={1}` (superseded from `steps={4}` on 13 Sep 2026…)"*. A criterion that silently changes cannot be argued with in review.
- **The flow README records it as a dated decision**, because that is the file read on every task.
- **If the change has a shape** — here, a shared shell whose segment count is a prop — say so, so the next change of the same kind costs one prop instead of three screens.

## When the developer's questions change the registry

A question asked mid-build ("should we distinguish X from Y?") is a taxonomy change request, and it arrives after the ticket was pasted. **Worked example: the Prompts questions, 16 Sep 2026** — seven funnel questions that turned into one new event, three new properties, one scoped-down event and one unified value set. The order, which is the same order as everything else here and is the part that gets skipped under time pressure:

1. **Answer from the registry first.** Two of the three questions asked for something already defined — `entry_point` (§18) already separated a suggestion card from the browse sheet, and `prompt_editor_dismissed` already recorded an abandoned write sheet. Say so, and cite the section. Adding a second event for a question the registry already answers is how two names for one act get into the warehouse.
2. **Then change `events.json` / `enums.json` / `properties.json`** — registry before ticket, always, and bump `registry_version` (vocabulary) and `taxonomy_version` (spec) independently. A new value set gets its own § section rather than a fourth row in §8.
3. **Then rewrite the ticket's Tracking section in place**, marking each change **NEW** against the row it lands on, and add the tracking acceptance criteria — a payload with no AC does not get built.
4. **Then write the change note as a ticket comment**, `exports/<flow>-NN-<slug>-tracking-update-comment.md`, committed beside the ticket. It answers the questions in the developer's own words, lists what moved, and says what will break if they already wrote the old version. **This is not a split by length** (see above) — the description still carries the full specification; the comment is the diff. Say that in its first line, or the next reader implements from the comment and misses the rest.
5. **Name the value changes loudly.** The one thing in the Prompts pass that could silently break a half-written emitter was `dismiss_method` losing `scrim` and `back`. A renamed value is worse than a new property: the code keeps compiling and the funnel quietly splits in two.

**Check the description length after the rewrite.** The Prompts ticket went 21.8k → 25.7k and still fits the 32,767 cap, so it stays one description; past the cap, split at `## Tracking` per the section above and the update comment sits beside the tracking comment, not inside it.

## Block B — paste as the last acceptance criterion in every ticket

> **Evidence of done:** attach screenshots of this card at **375 × 667, 390 × 844 and 430 × 932**. Each must show all content visible, nothing clipped or truncated, no scroll, and the primary action fully visible. On 375 × 667, state what the illustration did to make room.

Three screenshots × five cards = 15 images, and they make almost every acceptance criterion checkable by eye. 375 × 667 is where cards fail — check it first, not last.

## Block C — ticket 01 only

> **Shell evidence:** attach the shell component's file path and confirm cards 02–05 render from it with content props only — no duplicated progress bar, nav row, or headline styling.

## Build order

Card 01 builds the shell; 02–05 consume it. Mark 01 as blocking. Ticket 05 adds two variants to the shell (terminal CTA label + sunset gradient) — a variant on the shared nav row, not a fork.

## Repo status (as of Aug 2026)

`loveiqhq/showup` is **backend-only** (NestJS + PostGIS) — no visual check is possible against it. When the app UI lands in a repo, record it here; the post-sprint diff runs against that one.

**Still true as of 7 Sep 2026,** and it now costs something: 27 of the 35 shipped tracking events live in the two apps, which have no repo recorded in this project. The only read we have of them is the developer's own catalogue. Getting that repo recorded is what makes both the tracking diff and the visual adherence check possible.
