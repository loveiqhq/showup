# [Date 02] Coordinate chat — tutorial, chat, and ending the date from it

**Parent:** [Epic] 07a Match & date confirmation (`date/tickets/00-epic.md`) · **Type:** Story · **Priority:** High
**Attachments:** `date-02-coordinate-chat-attachment.zip` (the handoff folder, `design_handoff_showup/`) · `date-02-coordinate-chat-spec-sheet.png`
**Blocks:** Date 03 Arrival check-in (the "I arrived at the spot" chip) · Date 05 Change meet time and place (the two change chips) · Date 07 Cancel this date (extends `ScreenPartnerCancelled`)
**Blocked by:** Date 01 Date screen (`CoordinateModule`, the entry points)

## Description

The limited chat both people use to coordinate the last stretch before they meet. It opens at **meet − 1:30** from the Coordinate card on the Date screen (Date 01). Every message starts from an **action chip** — a structured reason we can log — with an editable draft. What people type is private; which reason they picked, and whether a message was sent and answered, is evidence for the Show-up Rate decision in flow 10.

This ticket also builds the **only** way to end a date from the chat: after one person has sent an **"I can't make it today"** message, either of them can end the date. No review follows and both can search again.

**Eight frames, one ticket:**

| | Frame | When | Reference |
|---|---|---|---|
| A | Tutorial | before the first open of this date's chat, per user | `ScreenChatTutorial` |
| B | Start | chat open, no message in the thread yet | `ScreenChatEntry` |
| C | Thread, keyboard up | any message exists, or a chip was picked on B | `ScreenChatOpen` |
| D | "I can't make it today" picked | the `cancel` chip is selected | `ScreenChatOpen initialKey="cancel"` |
| E | End the date card | a `cancel` message is in the thread (either side) | `ScreenChatOpen initialKey={null} seed={…}` |
| F | End the date sheet | End the date tapped on E | `… initialEndSheet` |
| G | You ended the date | after F, the person who ended it | `ScreenPartnerCancelled viaChat endedByYou` |
| H | What the match sees | after F, the other person | `ScreenPartnerCancelled viaChat` |

## Decided before build

- **The tutorial is always shown before the first open**, per date and per user. It has **two steps**. Step 3 ("It's how we keep it fair") was **removed** on 5 Nov 2026. The intro paragraph sits in a violet box. (5 Nov 2026)
- **Chips are always a 2-column grid of every chip, never a horizontal rail.** B uses large tiles; the dock in C uses compact chips (min-h 36). Labels are the **full** text and are never shortened. The "Tap a chip to let us know…" heading above the dock chips is **removed**. ⚠ *The kit's change-flow chat artboards (Change 1–4, 9) still show the old one-row scrolling rail. That is a leftover, not a variant — Date 05 moves them to this dock.* (5 Nov 2026)
- **"I can't make it today" has no prefill.** Picking it shows the cancel guide in the thread; the field and send stay **locked until Got it**; then the user writes the message themselves. (5 Nov 2026)
- **No move to another day in the app (MVP).** The time can only move later the same day (Date 05). If both want another day, they agree on it in the chat and one of them ends the date. The guide says so. (5 Nov 2026)
- **End the date in the chat exists ONLY after an "I can't make it today" message has been sent.** From either person. No other chip, message or state shows it. (5 Nov 2026)
- **The chat's buttons say `End date`** — the same label as the Date screen's End date, to keep the copy small. Where the user taps it decides what happens: in the chat (after a cant_make_it message) no review; on the Date screen the wrap-up and review (Date 06). (5 Nov 2026)
- **Ending in the chat costs nobody Show-up Rate** — not the person who ends it, not the match. Both outcome screens say so. (5 Nov 2026)
- **The End date card stays available until the date has ended** — no cut-off at the meet time. (5 Nov 2026)
- **Ending in the chat → no review.** The date did not happen, so flow 10 is **not** entered. Both people can search again immediately. The ender sees G, the match sees H. (5 Nov 2026)
- **The chips that open something else** — Change meet time, Change location (Date 05) and I arrived at the spot before check-in (Date 03) — are routed from here and built there. Until that ticket ships, its chips are **hidden** (epic rule 8).

## Layout — read this before building

⚠ **One column, one flexible element.** Chat (C–E): StatusBar → `ChatHeader` → **thread (`flex: 1`, the only scroll)** → dock (`flex: none`: field, then chips) → OS keyboard. Start (B): `ChatHeader` → one scroll (guide, grid, privacy line); no dock. Tutorial (A): header → one scroll → pinned CTA.

⚠ **The guide, its collapsed line and the End the date card are thread items**, at the foot after the messages. They are never pinned over the dock. When one appears, the thread scrolls to it; if it is taller than the visible thread, it scrolls to the item's **top** so the title is never cut.

⚠ **No absolute Y positioning.** The dock sits under the thread in the column. The keyboard is the OS's; the thread absorbs whatever height it takes.

📎 All specs are on the attached **date-02-coordinate-chat-spec-sheet.png** — type, colour, sizing, spacing and layout rules, keyed ①–⑯ across eight frames. Black callouts are built here; **violet callouts open another ticket's UI or are not ours**. The design system has colour and type tokens only, with no spacing scale, so the raw px values are intentional.

> **Reference implementation:** `design_handoff_showup/date/screen-chat-reference.jsx` — the code that renders the attached spec sheet. Read values from it rather than measuring the PNG. Frame props are in the table above.
> Shell components: `design_handoff_showup/components/shared.jsx` · Tokens: `design_handoff_showup/tokens/colors_and_type.css`
> **Order of authority:** reference file wins on numbers · ticket wins on behaviour, scope and copy · PNG wins on nothing.

## What is not ours

- **The keyboard.** The reference file draws a mock (`FauxKeyboard`) only to show the true content height. Ship the OS keyboard; **never spec its height**. Suggestion strip, emoji and dictation chrome are the platform's.
- **Status bar and home indicator** are mocks. Anchor to the safe-area insets.

## Copy — final strings

`{name}` is the match's first name. `{day}`, `{time}`, `{place}` are the latest **agreed** values.

**A · Tutorial**
- Header: `Coordinate with {name}`
- Headline: `Must read — how messaging works` (`Must read` italic)
- Intro box: `How you use the chat is a major part of how we can determine if one of you later says the other never showed while they insist they did.` (bold: `How you use the chat`, `determine if one of you later says the other never showed while they insist they did.`)
- Step 1 `Use our action chips` — `Make use of our action chips whenever possible. It allows us to analyze the situation of your date and understand who played real and who didn't.` · `Examples:` `I arrived` · `Running late`
- Step 2 `We never read your words` — `What you type stays private between you and your date. All we can see is which signal you picked and whether a message was sent and replied to — never the message itself.`
- CTA `Open the chat` · `Your messages are private. Only the signals are logged.`

**Chat header:** `{name}` · `DATE ON` · `{day} · {time} · {place}` · countdown `in {h}h {m}m`

**B · Start:** `Start with a reason` (`reason` italic) · `Tap one that fits best to start the chat.` / `Make use of our action chips as much as possible to allow us to understand who played real and who didn't.` · `We log which reason you pick — never what you type.`

**Chips** — label · draft it puts in the field (`CHAT_REASONS`, order fixed):

| Chip | Draft |
|---|---|
| `I'm on my way` | `On my way now — see you in a few!` |
| `I arrived at the spot` | `I'm here at the meeting spot — see you soon!` |
| `I can't spot you` | `I'm here but can't spot you — where are you sitting?` |
| `When will you arrive?` | `When do you think you'll get here?` |
| `Change meet time` | — opens the Date 05 picker |
| `Change location` | — opens the Date 05 picker |
| `I'm running late` | `Running about 10 min late — sorry! Almost there.` |
| `I can't make it today` | **none** — the user writes it |

**C · Field placeholder:** `Type a message, or tap a chip below`

**D · Cancel guide**
- Title: `Life happens — handle it with respect`
- `Show Up is for reliable, committed people who want to meet in real life, quickly. But sometimes life gets in the way. If there's really no way you can make it, explain why and say sorry. {name} has freed up time for you and is looking forward to meeting you.` (bold: `explain why and say sorry`)
- `Better still: move it to later today or to another place instead of cancelling. A different day can't be set in the app — agree on one here, then one of you ends the date so you can both search again.` (bold: `move it to later today or to another place`)
- Button `Got it`
- Field placeholder while locked: `Read the note above, then tap Got it`
- After Got it: `Explain why, say sorry — in your own words.` · placeholder `Explain why and say sorry to {name}…`

**E · Card:** `Not meeting today?` (bold) `Once you've talked it through, one of you ends the date so you can both search again.` · button `End date`

**F · Sheet:** `End the date with {name}?` · `The date won't happen, so there's no review. You can both search again straight away.` · `Keep the date` · `End date`

**G · You ended it:** `You ended the date` (`ended` italic) · `We hope you and {name} found another day to meet. Search is open again — go and find your next date.` · row `You told {name} yourself, so your Show-up Rate stays untouched. There's no review, as the date didn't happen.` · CTA `Find your next date`

**H · The match:** `{name} had to cancel the date` (`cancel` italic) · `We hope you two found another day to meet. Search is open again — go and find your next date.` · row `Your Show-up Rate stays untouched. There's no review, as the date didn't happen.` · CTA `Find your next date`

Product terms capitalise exactly: **Show Up · Show-up Rate · Check-In**.

## Behaviour

**Opening**

- Entry points: `Open chat` on the Coordinate card (Date 01 state B), `Read & reply` on the hoisted unread card (state C), a message notification. All land on the tutorial if this user has not seen it for this date, otherwise on B (no message yet) or C (thread).
- The chat opens at **meet − 1:30** of the latest agreed time. Before that the Coordinate card is locked (Date 01) and nothing here is reachable.
- **Tutorial:** shown once per date and per user. `Open the chat` is the only way forward; back returns to the Date screen and the tutorial shows again next time until `Open the chat` has been tapped once.

**Chips and sending**

- **B:** a chip tap opens C with that chip selected and its draft in the field. `I can't make it today` opens C in state D.
- **C:** a chip tap selects it (tone fill + border) and **replaces** the field's content with its draft. The draft is editable. Send is enabled whenever the field has text.
- A message is sent with the selected chip's reason. After sending, the selection clears and the field empties; the chips stay.
- A message with **no chip selected** (the user typed without picking one) is allowed and is sent with no reason.
- `Change meet time` / `Change location` never send anything from here — they open the Date 05 picker. `I arrived at the spot` runs the Date 03 gate until the user has checked in, then behaves like any chip.
- The thread shows messages oldest → newest, with the reason badge on chip messages. It scrolls to the newest item when one arrives.

**"I can't make it today"**

- Selecting it clears the field (no draft), **disables the field and send**, and shows the cancel guide at the foot of the thread.
- `Got it` collapses the guide to one line, enables the field and focuses it.
- Picking another chip removes the guide. Picking `I can't make it today` again shows it again, locked.
- The guide is local to this phone. It is never sent and the match never sees it.

**Ending the date**

- **The End the date card exists only once a message with the `cancel` reason is in the thread** — sent by either person. It appears for **both** people, at the foot of the thread. It is hidden while `I can't make it today` is the selected chip (the guide takes the slot).
- `End date` on the card opens the sheet. `Keep the date` or a scrim tap closes it; nothing changes.
- `End date` on the sheet ends the date for both: the date state becomes ended-in-chat, the Coordinate chat closes, **flow 10 is not entered**, and **Search, Likes and Instant open again** for both.
- The person who ended it lands on **G**. The other person lands on **H** the next time they open the app, or immediately if it is open. `Find your next date` opens Search on both.

**Leaving**

- Back returns to the Date screen. The thread, a typed draft and a selected chip survive leaving and returning within the same app session.

## Build inventory

- **Client:** `ScreenChatTutorial`, `ScreenChatEntry`, `ScreenChatOpen`, `ChatHeader`, `ChatBubble`, `ThreadScroll` (with the item-reveal rule), `BigActionChip` (`tile` and `compact`), `CancelGuide`, `EndDateCard`, `EndDateSheet`, `ScreenPartnerCancelled` with the **`viaChat`** and **`endedByYou`** variants. `CHAT_REASONS` + `REASON_TONES` + `ReasonBadge` are **built here once** (epic) and read by Date 01, 03 and 05.
- **Server:** the chat thread (both people, messages with reason + timestamp + read state), the tutorial-seen flag per date and user, the `ended_in_chat` date state with its side effects (unblock Search / Likes / Instant for both, no flow 10), the H screen delivered to the other person.
- **Not built here:** the pickers (Date 05), the check-in gate and its sheets (Date 03), the default `ScreenPartnerCancelled` variant for Cancel this date (Date 07 extends this component, never forks it), push copy.

## Acceptance criteria

- [ ] The tutorial shows before the first open of each date's chat, once per user; it has **two steps** and no third
- [ ] B shows all eight chips in a **2-column grid**, full labels, nothing scrolls horizontally
- [ ] The dock in C shows all eight chips in a 2-column grid, chips **36 high**, **no heading line** above them, full labels
- [ ] A chip tap puts its draft in the field, editable; send sends it with that chip's reason; the selection then clears
- [ ] `Change meet time` and `Change location` send nothing and open the Date 05 picker (hidden until Date 05 ships)
- [ ] `I can't make it today`: the field is **empty and disabled**, send is disabled, the guide shows in the thread with `Got it`
- [ ] `Got it` collapses the guide to one line, enables and focuses the field; the user's own text is what gets sent
- [ ] The guide's title and `Got it` are fully visible when it appears at 375 × 667 — the thread scrolls to it
- [ ] **The End the date card appears only after a `cancel`-reason message is in the thread**, for both people — never after any other message, and never before one is sent
- [ ] The card and sheet buttons read `End date`; the sheet shows `Keep the date` first and `End date` second; Keep and the scrim change nothing
- [ ] The card stays available until the date has ended — no cut-off at the meet time
- [ ] Ending it: no Show-up Rate change for either person, no review screen for either person, Search / Likes / Instant are enabled for both, the ender sees G, the match sees H
- [ ] The chat header always shows the latest **agreed** time and place
- [ ] The thread is the only scroll in C–E; no absolute Y positioning; the keyboard is the OS's
- [ ] No message text, draft or guide text appears in any tracking payload
- [ ] Copy matches the strings above exactly
- [ ] **Evidence of done:** screenshots of **A–H** at **375 × 667, 390 × 844 and 430 × 932** — 24 images. On 375 × 667, state how D fits (guide title and Got it visible). Plus one recording: pick `I can't make it today` → Got it → type → send → End date → G, and the match's phone landing on H.

## Tracking

Event names come from `design_handoff_showup/tracking/events.json` (family **L — Chat Metadata**, `screen_viewed` in family A). Use them exactly — do not invent names, and do not restate a payload the registry already defines. **Registry 1.4.33 / taxonomy 1.4.26** — three screen rows, two value sets and three events are **NEW**, and family L is **respecified** with this ticket.

| What | Event | Payload |
|---|---|---|
| Tutorial screenview | `screen_viewed` | `screen_id: "date_chat_tutorial"`, `screen_name: "Date - Chat tutorial"` — §11, **NEW row** |
| Tutorial shown | `chat_tutorial_shown` | `date_id` — **respecified**, `variant` removed |
| Open the chat | `chat_tutorial_acknowledged` | `date_id`, `read_time_s` |
| Chat screenview | `screen_viewed` | `screen_id: "date_chat"`, `screen_name: "Date - Coordinate chat"` — §11, **NEW row**; B and C are one screen |
| Chat shown | `chat_opened` | `date_id`, **`entry`** (§39 — **NEW**), **`is_first`**, **`minutes_before_start`** — **respecified**, `trigger` and `layout` removed |
| Chip tap | `action_chip_used` | `date_id`, **`reason`** (§40 — **NEW**), **`surface`** (`entry` / `dock`), `minutes_before_start` — **respecified**, `chip_id` removed |
| Message sent | `message_sent` | `date_id`, `reason` (§40 or null), `char_count`, **`prefill_edited`**, `minutes_before_start`, `is_first` — **respecified** |
| Message shown | `message_received` | `date_id`, `reason`, `char_count` |
| Got it | `cancel_guide_acknowledged` | **NEW event** · `date_id`, `read_time_s`, `minutes_before_start` |
| End the date tapped | `end_date_sheet_opened` | **NEW event** · `date_id`, `cant_make_it_from` (`self` / `match`), `minutes_before_start` |
| Date ended | `date_ended_in_chat` | **NEW event, server** · `date_id`, `cant_make_it_from`, `minutes_before_start` |
| Outcome screenview | `screen_viewed` | `screen_id: "date_ended_in_chat"`, `screen_name: "Date - Ended in chat"` — §11, **NEW row**, both G and H |
| Leaving the chat | `chat_closed` | `date_id`, `messages_sent`, `duration_s` |

**Rules for this screen**

- ⚠ **Renamed and removed values — check any emitter already written.** `message_sent.chip_seeded` → `reason` + `prefill_edited`; `minutes_before_date` → `minutes_before_start`. `action_chip_used.chip_id` → `reason`. `chat_opened.trigger` and `layout` → `entry`. Old code still compiles and silently splits the funnel.
- **Retired with this ticket, do not emit or reuse:** `coordinate_viewed`, `date_status_sent`, `date_status_undone`. A chip message is `message_sent` with a `reason`.
- `action_chip_used` fires **on every chip tap**, including the ones that open a picker or the check-in gate. Whatever they open fires its own ticket's events.
- `prefill_edited` is **null** for `cant_make_it` (no draft) and for a message sent with no chip.
- **Keeping the date** is `end_date_sheet_opened` with no `date_ended_in_chat` after it. No event for Keep.
- `date_ended_in_chat` is **not** `date_cancelled` (Date 07). Never fire both for one ending.
- **Never in a payload:** message text, the draft, the guide text, the place.
- **Must NOT fire from this ticket:** `date_cancelled` (07), `arrival_*` (03), suggestion events (05), any flow 10 review event.

**Tracking acceptance criteria**

- [ ] Every family L event carries `date_id`
- [ ] `reason` uses only §40 values; `entry` only §39
- [ ] `message_sent` never carries text; `prefill_edited` is null for `cant_make_it`
- [ ] Ending in the chat fires `date_ended_in_chat` once and never `date_cancelled`
- [ ] `coordinate_viewed`, `date_status_sent`, `date_status_undone` are not emitted anywhere

**Currently in the build: none of it.** Every row above is `Not built`.

## Out of scope

The Date screen and the Coordinate card (Date 01). The check-in gate, its sheets and the stored location (Date 03). The arrival message in the unread slot (Date 04). The time and place pickers, suggestion cards and binding note (Date 05) — including moving the change-flow chat artboards to this dock. Report a problem (Date 06). Cancel this date and its penalty guard (Date 07). Flow 10. Push, SMS, WhatsApp and email copy. Moving a date to another day.

## Dependencies

- **Date 01:** `CoordinateModule` and the Date screen entry points
- **Search 01 / shared:** `Avatar`, `Button`, `ModalShell`, `Atmosphere`
- **Server:** the chat store, the tutorial-seen flag, the ended-in-chat state and its unblock
- **Tracking registry 1.4.33 / taxonomy 1.4.26**

## Open before the sprint

1. **Change chips after meet − 0:30.** A suggestion sent then can never be agreed. Hide, disable, or leave. Decided with Date 05; this ticket only hides them while Date 05 is not shipped.
2. **Show-up Rate display format** — still open project-wide (`JIRA-HANDOFF.md` → *Open decisions every ticket must carry*). G and H only say the rate "stays untouched", with no value, so they are unaffected by the format.

**Settled 5 Nov 2026** (were open in the first version of this ticket): no Show-up Rate drop for ending in the chat · the card stays until the date has ended · the button label is `End date`, same as the Date screen.
