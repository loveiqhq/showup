# Date 02 — the Coordinate chat (SHOWUP-194): design

Written 7 Nov 2026. Source of truth: the Jira description of SHOWUP-194 (no comments on it). The ticket's
reference implementation (`design_handoff_showup/date/screen-chat-reference.jsx`, from the 6 Nov zip,
"Show Up Design System (22)") wins on numbers. The ticket wins on behaviour, scope and copy. The spec-sheet
PNG wins on nothing.

## 1. Intent

Ninety minutes before they meet, two people need a way to say "I'm on my way", "I can't spot you", "I'm
running late". The chat gives them exactly that, and nothing more: every message can start from a
structured reason (a chip) with an editable draft. What people type stays private; which reason they
picked, and whether a message was sent and answered, is the evidence flow 10 later uses to decide who
really showed up.

It is also the one honest exit when a date cannot happen: after either person has sent an "I can't make it
today" message, either of them can end the date from the chat. Nobody is reviewed, nobody's Show-up Rate
moves, and both can search again at once.

## 2. Goals, non-goals, anti-goals

**Goals**
- Every acceptance criterion in SHOWUP-194, on Android and iOS, in the same PR, with the server it needs.
- The eight frames (A tutorial · B start · C thread · D cancel picked · E end card · F end sheet · G you
  ended it · H the match's screen) pixel-faithful to the reference at 375 × 667, 390 × 844 and 430 × 932,
  and clean in the fit sweep on every phone size and type size the 193 device audit measures.
- Real data end to end: the thread, the tutorial flag and the ended-in-chat outcome come from the server.

**Non-goals** (other 07a tickets, per the ticket's "Out of scope")
- The Date screen and the Coordinate card (Date 01) beyond wiring its two entry points.
- The check-in gate, its sheets and the stored location (03); the arrival message (04).
- The time and place pickers, suggestion cards and binding note (05), including moving the change-flow
  chat artboards to this dock.
- Report a problem (06); Cancel this date and its penalty guard (07); flow 10.
- Push, SMS, WhatsApp and email copy. Moving a date to another day.

**Anti-goals**
- Message text, a draft, the guide text or the place in any analytics payload or log.
- A horizontal chip rail, a shortened chip label, or the old "Tap a chip…" heading.
- The guide or the End date card pinned over the dock; any absolute Y position; a keyboard height in code.
- A second copy of `CHAT_REASONS`, `REASON_TONES` or `ReasonBadge` (193 already draws the badge).
- `date_cancelled`, `arrival_*`, suggestion or flow-10 events fired from here; any Show-up Rate change.

## 3. Decisions

**From Esma, 7 Nov 2026**
- **The three chips owned by later tickets are hidden until those tickets ship** — `Change meet time`,
  `Change location` (Date 05) and `I arrived at the spot` (Date 03) — as epic rule 8 says ("hidden, not
  disabled"). B and the dock therefore show five chips, in `CHAT_REASONS` order: `I'm on my way` ·
  `I can't spot you` · `When will you arrive?` · `I'm running late` · `I can't make it today`. The
  acceptance criteria's "all eight chips" is the layout once 03 and 05 land; showing a chip is one flag per
  chip, so each later ticket turns its own on.

**From the ticket, restated because they shape the build**
- Tutorial: once per date and per user, two steps, `Open the chat` the only way forward; back returns to
  the Date screen and the tutorial shows again until `Open the chat` has been tapped once.
- Chips: always a 2-column grid; B large tiles, the dock compact chips 36 high; full labels; no heading.
- A chip tap selects it and **replaces** the field with its draft. Send is enabled whenever the field has
  text. A message with no chip is allowed and carries no reason. After sending, the selection clears and
  the field empties.
- `I can't make it today`: no draft; field and send locked until `Got it`; the guide is a thread item at the
  foot, local to this phone, never sent. `Got it` collapses it to one line and focuses the field. Another
  chip removes it; picking cancel again shows it again, locked.
- End date card: only once a `cant_make_it` message is in the thread (either side), for both people, at the
  foot; hidden while the cancel chip is selected; available until the date has ended (no cut-off at meet
  time). Card and sheet say `End date`; the sheet shows `Keep the date` first. Ending: no review, no flow
  10, no Show-up Rate change, Search / Likes / Instant open for both; the ender sees G, the match H.
- Layout: one column, one flexible element. Chat: header → thread (the only scroll) → dock (field, then
  chips) → the OS keyboard. Start: header → one scroll (guide, grid, privacy line), no dock. Tutorial:
  header → one scroll → pinned CTA. The keyboard is the OS's; the thread absorbs whatever it takes.
- The thread, a typed draft and a selected chip survive leaving and returning within one app session.

**My calls** (each listed for Philipp in §9 where product-visible)
- **New messages arrive by polling**, not a socket: while the chat is on screen it re-reads the thread and
  the date every 5 s (and at once after a send or a return to the foreground). The Date screen re-reads the
  date every 15 s while on screen, so the match lands on H within seconds when the app is open. No socket
  infrastructure exists, and push is out of scope.
- **The countdown** follows the copy literally — `in {h}h {m}m`, `in 0h 45m` under an hour — and is left
  out of the header line once the meet time has passed (the chat stays open until the date ends; the ticket
  gives no copy for after).
- **"Opened from a notification"** is supported as a route (`entry = notification`) but nothing in this
  ticket sends a chat push.
- **`I arrived at the spot` is accepted by the server** (Date 03 will send it), while the two change reasons
  are refused there: they never produce a message.
- **When both people sent a `cant_make_it` message**, `cant_make_it_from` counts the most recent one.
- **The developer menu** (both apps) gets the chat's frames on the States page and test actions — "match
  sends a message", "match says they can't make it", "match ends the date" — against new dev-only routes, as
  193 did for the Date screen. iOS also gets 193's Date actions it lacked (a 193 deferred minor).
- **The existing chat contract changes freely.** SHOWUP-115's `GET` / `POST /dates/:id/chat` are used by no
  app (193 left the chat unwired), so `reason` becomes optional, `body` required, and the reasons are renamed
  without a compatibility path. A legacy row with no body (SHOWUP-115 allowed reason-only messages) stays
  readable: `body` stays nullable in the response, and such a bubble shows its reason's label as its text.
  The SHOWUP-115 e2e tests move to the new contract.
- **Both outcome screens survive a crash.** Ending does not stamp the ender as having seen the outcome; each
  person stamps it (`POST /dates/:id/ended/seen`) once their screen has shown, so `GET /me/date` returns
  `ended` — `endedByYou: true` for the ender, `false` for the match — until then.
- **A date that ends while someone is mid-action** goes straight to that person's outcome screen: a poll that
  finds it ended, or a 409 from a second `End date`, closes any open sheet, drops the draft and shows G or H.

## 4. Server

**Migration `1717000033000-DateChat`**
- `date_chat_reason_enum` takes the registry's §40 values: `change_meet_time → change_time`,
  `change_location → change_place`, `cant_find_you → cant_find`, `cant_make_it_today → cant_make_it`, plus
  new `arrived` and `when_arrive`; existing rows are renamed in place. `date_chat_messages.reason` becomes
  nullable (a message sent with no chip).
- `dates_status_enum` gains `ended_in_chat`, terminal; the lifecycle allows `confirmed → ended_in_chat`.
- `dates` gains `ended_by_id`, `ended_at` (kept apart from the cancel columns: this is not a cancel),
  `a_chat_tutorial_at` / `b_chat_tutorial_at` (the tutorial-seen flag per person) and `a_end_seen_at` /
  `b_end_seen_at` (each person has seen the outcome screen).

**API** (OpenAPI regenerated; both clients generated from it)
- `GET /me/date` — `ActiveDateDto` gains `chatTutorialSeen` (the caller's); `unread.reason` becomes
  nullable. With no active date, the response carries `ended: { dateId, name, endedByYou }` while the caller
  has not yet seen an ended-in-chat outcome — that is how the match lands on H, and how the ender still sees
  G after a crash.
- `GET /dates/:id/chat` — messages oldest first: `id`, `reason` (§40 or null), `body`, `sentAt`, `mine`, and
  for the caller's own messages `read` (the other person's read stamp is at or after it).
- `POST /dates/:id/chat` — `{ reason?, body }`. `body` is required, trimmed, at most 1000 characters;
  `reason` optional, any §40 value except `change_time` / `change_place`. Refused unless the date is
  `confirmed`, the caller is in it, and the chat is open (meet − 1:30 of the latest agreed time).
- `POST /dates/:id/chat/read` — unchanged (193).
- `POST /dates/:id/chat/tutorial` — stamps the caller's tutorial flag. Idempotent.
- `POST /dates/:id/end-in-chat` — ends the date for both: refused (409) unless the date is `confirmed` and a
  `cant_make_it` message is in its thread. Sets `ended_in_chat`, `ended_by_id`, `ended_at`, emits the server
  event `date_ended_in_chat` once, and returns the outcome (`endedByYou: true`). Nothing touches the Show-up
  Rate, no review is opened, `date_cancelled` never fires.
- `POST /dates/:id/ended/seen` — stamps the caller's outcome as seen (H shown). Idempotent.
- The lock on Search, Likes and Instant is one status rule — `dateLocksUser` in
  `src/modules/dates/util/date-lock.ts`: only a `confirmed` date locks a person — used by search, matching,
  check-ins and safety. `ended_in_chat` therefore releases both people with no further change; a unit test on
  the rule and an e2e through search prove it. The coordinate-unlock job already skips non-`confirmed` dates.
- Dev-only routes (non-production): the match sends a message (with a reason), the match ends the date in
  the chat.

No logging of message bodies, drafts or names. Server tests cover the reason migration, the send rules, the
end rule (refused without a `cant_make_it` message), the unblock, and the event payload.

## 5. Apps (Android and iOS, same PR)

**Built once, read by 01, 03, 05:** `ChatReasons` (key, label, draft, icon, tone, and whether it is shown
yet), `ReasonTones`, `ReasonBadge`. 193's `chatReasonStyle` is folded into it and re-keyed to §40, so the
Date screen's unread card keeps working when the server's names change; an unread message with no reason
shows no badge.

**Screens and parts** (the reference's names): `ChatTutorialScreen` (A), `ChatScreen` (B and C–E: one
screen, one screen id), `EndDateSheet` (F, a modal), `DateEndedScreen` (G / H: the reference's
`ScreenPartnerCancelled` with the `viaChat` / `endedByYou` variants — Date 07 extends it, never forks it),
`ChatHeader`, `ChatBubble` (reason badge on chip messages, the avatar on the last of their run, the read
receipt on mine), `ThreadScroll` (the always-visible indicator when the thread overflows, and the reveal
rule: a new guide, collapsed line or card scrolls into view, to its top when taller than the visible
thread), `BigActionChip` (tile and compact), `CancelGuide`, `EndDateCard`.

**State:** one chat model per date, owned at app level for the session, so the thread, draft, selected chip
and guide survive leaving and returning. It loads and polls the thread (§3), marks it read on open and when
new messages arrive while open, sends, holds the local selection / draft / guide state, and runs the end.
Screens take values and return pixels, as everywhere else.

**Routing:** the Date screen's `Open chat` (entry `open_chat`) and `Read & reply` (`read_reply`) — no-ops
since 193 — open the tutorial when `chatTutorialSeen` is false, otherwise the chat. `Open the chat` stamps
the flag and continues into the chat (`tutorial`). Back returns to the Date screen. After ending, the ender
goes to G; the match goes to H when a date read returns `ended`. `Find your next date` opens Search and
marks the outcome seen. Search, Likes and Instant unlock because the date read no longer finds a date.

**Keyboard:** the OS keyboard, never a height in code: Android insets (`imePadding`) on the chat column,
iOS's keyboard avoidance. The field grows to 90 (the reference's `maxHeight`) and then scrolls.

**Motion:** the reference's transitions — chip tone fill and border, the field's border, the send button's
fill (160 ms), the indicator's fade (160 ms), the sheet rising over a scrim — from the `Motion` tokens, and
none when the user has reduced motion on.

**Accessibility:** every control labelled; the reason badge and the read receipt read as words; the field
reads its placeholder; usable at the largest type the 193 audit measures.

## 6. Tracking

Registry 1.4.33 / taxonomy 1.4.26, merged as in 193: the repo's file stays the base (it records what is
really implemented) and takes the zip's additions — the three screen rows, the two value sets (§39
`chat_entry`, §40 `chat_reason`), the three new events, family L respecified. Events, exactly as the
ticket's table: `screen_viewed` (`date_chat_tutorial`, `date_chat`, `date_ended_in_chat`),
`chat_tutorial_shown`, `chat_tutorial_acknowledged`, `chat_opened`, `action_chip_used` (every chip tap),
`message_sent`, `message_received`, `cancel_guide_acknowledged`, `end_date_sheet_opened`,
`date_ended_in_chat` (server), `chat_closed`. `prefill_edited` is null for `cant_make_it` and for a message
with no chip. `coordinate_viewed`, `date_status_sent` and `date_status_undone` are emitted nowhere (checked).
No text, draft, guide text or place in any payload; the wiring checker covers the new models.

## 7. Verification

- Server: unit tests and e2e for every route and rule above; OpenAPI and the contract test updated.
- Apps: model tests (selection, drafts, the guide lock, the end card's visibility, polling, the session
  survival, tracking payloads), routing tests, and a fit sweep for every frame — all 27 sizes of the 193
  audit, every type size, with its rules (touch areas, no two overlapping, whole words, nothing hidden
  behind the dock) — plus the 375 × 667 check that D's title and `Got it` are visible.
- `audit/verify-chat.py`: the copy against the ticket's strings, the five visible chips in order, no
  heading line, both platforms.
- Evidence: A–H at 375 × 667, 390 × 844 and 430 × 932 on both platforms (24 images each). C–E are rendered
  with a keyboard-sized inset to show the content height, since a screenshot harness has no OS keyboard.
  The recording (cancel → Got it → type → send → End date → G, and the match's phone on H) needs a phone or
  emulator — Esma's.

## 8. Delivery

Branch `feat/date-02-coordinate-chat` from development, one PR to development, both platforms and the
server together. The 193 device audit (PR #172) brings the stricter fit rules and the `audit/` checks this
ticket's sweep uses. The server and the screens are built first; #172 merges to development once its CI is
green, and this branch takes development in before the fit-sweep and evidence tasks.

## 9. For Philipp (not blocking)

- The three hidden chips appear as Date 03 and Date 05 ship (Esma's decision, epic rule 8).
- The countdown reads `in 0h 45m` under an hour and disappears after the meet time — no copy exists for
  either case.
- New messages show within about five seconds; true instant delivery needs push or a socket.
