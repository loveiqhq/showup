# Date 08 — what the other side sees: design

SHOWUP-199 (Date 08, Jira In Progress, Esma), requested by Esma on 22 Nov 2026. Parent epic 07a. When A ends the date
from Date 06, B is moved at once to one full screen that says how A ended it, with one way out: `Proceed to review`.
Built on one branch, `feat/date-08-partner-end`, on top of Date 06 / 07 (PR #191, on staging).

## 1. Sources and authority

- **Behaviour, scope and copy:** the Jira description. SHOWUP-199 has **no comments**.
- **Numbers:** `design_handoff_showup/date/screen-partner-end-reference.jsx`. Order of authority: reference wins on
  numbers, ticket on behaviour / scope / copy, PNG on nothing.
- **Handoff version.** Esma gave `Show Up Design System (5).zip`. Its `08-partner-end.md` and reference file are
  byte-identical to the repo's `design_handoff_showup/`; its `events.json` differs from the repo's only in the
  build-status fields the export resets (0 definition differences), and the three §11 rows are already in the repo's
  `enums.json` (1.4.39 / taxonomy 1.4.31). (5) adds spec-sheet PNGs and another ticket's `location/` folder only.
  **No handoff sync is needed**, which also avoids the 197 registry-sync trap.
- **Spec sheet vs ticket:** callout ① says "no scroll"; the ticket and the sheet's own Layout box say the column
  scrolls on 375 × 667 with the CTA as the last item. The ticket wins.
- **C's badge is `#0A7A47`.** The reference writes `var(--liq-success-fg, #00AB55)`; the token is defined
  (`colors_and_type.css` line 78, and the spec-sheet HTML's own CSS), so `#00AB55` is a fallback that never renders.
  The sheet's swatch legend shows the fallback; the frames draw the token.

## 2. Scope

**Built (server + both apps):**
- Frames **A** `you_no_showed` (end_reason `partner_no_show`), **B** `date_ended_discomfort` (`uncomfortable`),
  **C** `date_closed_positive` (`met`): one `PartnerEndedScreen`, three payloads.
- B's routing from the ended outcome to the right payload, presented over anything, with no exit but the CTA.
- The server push to B after a Date 06 end commits (placeholder copy).
- `screen_viewed` with the three §11 ids.
- Dev: the three frames LIVE on the in-memory date server (States page), and a "B is in the chat when A ends" path.

**Not built (other tickets):** Date 06's sheets and end endpoint (done, #191); flow 10 (`ScreenRating`, its
`claimedNoShow` headline, the automated no-show check); push copy; the parked *unsafe* route.

**Already in place (no change):** the server's ended outcome already carries `reason` and, for the match of a Date 06
end, `partnerScreen` from `end_reason` alone (`active-date.ts` `endedOutcomeOf`); both apps parse `PartnerScreen`.
B's *ended, review pending* state is the ended outcome while B's end-seen stamp is unset.

## 3. Rulings (my calls; each says what it rests on and what it costs if wrong)

- **R1 — `Proceed to review` lands in one slot, `startReview(outcome)`, per platform.** Flow 10 is not built. The
  ticket's dependency clause would hide Date 06's end CTAs until it is; Esma overruled that on 22 Nov 2026 (never
  hide End date / Report / Cancel, even before flow 10). So the slot stands in for flow 10: it marks B's end seen and
  lands B on Search, exactly as `landAfterEnd` does for A. **Until B taps it, every app open returns to this screen**
  (the outcome stays unseen), which is the ticket's rule with "review sent" read as "Proceed tapped" for now.
  - When flow 10 exists, the slot opens `ScreenRating` (`claimedNoShow` for A's payload) and the mark-seen moves to
    the review's send. Nothing else changes.
  - Costs if wrong: one function per platform.
- **R2 — "At once" means the reads that already exist, plus the push.** There is no realtime socket in the code base:
  the chat polls every 5 s, the Date screen every 15 s, a Date 06 request that loses the race gets a 409 and re-reads,
  a cold start reads, and the new push opens the app. Every one of them already calls `routeEnded`; 199 changes what
  that decision returns for B. Building a socket for one screen is out of proportion.
  - Costs if wrong: up to 15 s on the Date screen; a socket later plugs into the same `routeEnded`.
- **R3 — The push: kind `date_partner_ended`, to B only, after the end commits, placeholder copy.** Same shape as
  Date 07's `date_cancelled`: A's first name only, never the reason, a place or a time (a lock screen is public).
  EN title `{name} ended your date` (no name: `Your date was ended`), body `Open Show Up to see what happens next.`;
  DE title `{name} hat euer Date beendet` (no name: `Dein Date wurde beendet`), body
  `Öffne Show Up, um zu sehen, wie es weitergeht.` (Date 07's body, written once). Category `engagement`, like every other date push; Esma is asking Philipp whether
  the cancel push should always arrive, and that answer applies to this one too. A failed push never fails the end.
  - Costs if wrong: one catalog entry and one category.
- **R4 — One new `PartnerEndedScreen`, not Date 02 / 07's `DateEndedScreen`.** Rule one says reuse first; the two
  differ in nearly every number (avatar 76 vs 72, badge 30 vs 28, headline 27 vs 26, tile 36 / 12 vs 34 / 11, row text
  13.5 / 1.45 vs 13 / 1.42, hero padding 30 24 vs 26 24 24) and in the CTA (199's scrolls as the last item; H's is
  pinned). Parametrising every number would be "a number with extra steps", and changing H risks Date 02 / 07. So
  199 builds its own screen **from the shared primitives** (`WelcomeBackdrop` / Atmosphere, `MatchPhoto`, `IconTile`,
  `pillShadows`, `WashHeadline`, `prettyWrap`, `cssLineBox`, `PrimaryButton`) and says why at the top of the file.
- **R5 — Layout: one scroll whose content is at least the viewport tall.** Hero → rows → flexible space (min 14) → CTA
  (bottom padding 6). On a tall phone the CTA sits at the foot; on 375 × 667 (and at the largest type) the column
  scrolls and the CTA is the last item, never overlapping the rows. No absolute Y.
- **R6 — No exit.** Android: a `BackHandler` that consumes back, predictive back included. iOS: no back button, no
  swipe-back, no swipe-down (a full-screen route, not a sheet). No TabBar. `Proceed to review` is the only control.
  Navigating there pops the Date screen, the chat and every sheet; the open sheets' restored drafts (Date 05 / 06 /
  07, iOS `@SceneStorage`, Android saved state) are cleared so none comes back after process death.
- **R7 — Tracking.** `screen_viewed` each time the screen shows: `date_partner_ended_no_show` /
  `_uncomfortable` / `_met` with their §11 names; `referrer_screen_id` is where B was (`date_screen`, the chat), absent
  on a cold start, through the same `outcomeFrom` H uses. `Proceed to review` fires nothing. No `date_ended` from B.
- **R8 — The avatar is A's photo** through `MatchPhoto` (the outcome's `photoUrl`, initials when absent), as H draws
  it. C has no photo: the white 72 % disc with the violet heart.
- **R9 — Copy is the ticket's, verbatim**, read at run time by a new `verify-partner-end.py` that quotes the ticket
  (as `verify-end.py` does). A's sub breaks after "after waiting for you." (the reference's `<br/>`).
- **R10 — Open item 1 stands:** B's row 2 promises "tell us what happened from your point of view" though the normal
  review has no such field; the flow 10 ticket decides. The copy ships as written.

## 4. The screen, from the reference

Shared: atmosphere `hero`; status bar inset; column padding 8 20 0; hero radius 28, centred; avatar block 76 with the
badge 30 (white 3 ring, shadow md) at right −3 / bottom −3; headline Lora 700 27 / 1.15, −0.01em, balanced, margin
bottom 12, the italic run washed (`su-underlined`: italic 500, no wrap inside the run); sub Manrope 14 / 1.55 muted,
pretty, bold runs fg; rows gap 10, each white, 1 px `border-soft`, radius 14, padding 14 **inside** the border (CSS
counts the border in the box, so Compose / SwiftUI pad 15), gap 12, tile 36 radius 12 with an 18 / 2 icon, the tile at
the row's top and only the text centred on it (no `alignItems`), text Manrope 13.5 / 1.45 fg, bold runs fg 700;
flexible space min 14; `Proceed to review`, sunset lg 56, full width, padding-bottom 6.

| | A no-show | B uncomfortable | C met |
|---|---|---|---|
| Hero fill | `su-grad-blush` | `su-grad-blush` | `su-grad-lilac` |
| Hero padding | 30 24 | **28 22** | 30 24 |
| Avatar → headline | 18 | **16** | 18 |
| Avatar | A's photo | A's photo | white 72 % disc, 1 px `rgba(129,42,236,.16)`, shadow `0 2 10 rgba(46,1,71,.08)`, `heart-filled` 34 violet |
| Badge | orange 500, `clock` 16 / 2.6 | orange 500, `hand` 15 / 2.6 | `#0A7A47`, `check` 15 / 3 |
| Rows margin-top | 18 | **16** | 18 |
| Rows | 3: `user-x`, `message-circle`, `shield-check`, orange 500 on orange 12 % | 3: `hand`, `message-circle`, `shield-check`, orange | 2: `heart`, `search`, violet 500 on violet 12 % |

## 5. Review focus (most likely to bite, with the test that pins each)

1. **A's end lands while B has a sheet open** (Date 06 / 07 / 05): B sees A's screen, the sheet is gone, and after a
   process death it does not come back. Pinned by a routing test per platform and the iOS draft-clear test.
2. **Two reads see the same end** (the chat's poll and the Date screen's): B is routed once, and `screen_viewed` fires
   once per showing. Pinned by a once-per-date test, as `LandedOnce` is for A.
3. **B kills the app before tapping:** the next launch shows the same payload with no referrer. Pinned by a cold-start
   routing test and the dev walk.
4. **The largest system font and 320 × 686:** the CTA stays reachable by scrolling and nothing clips. Pinned by the fit
   test at 17 sizes and the largest-type fit row.
5. **The match of a cancel, and Date 02's chat end, are untouched:** H and 4b still route as before. Pinned by
   `endedRoute` tests for every reason × `endedByYou`.

## 6. Verification

- Server: unit + e2e (`date_partner_ended` goes to B only, once, after commit; a failed push never fails the end;
  none on a refused end). `npm run lint && npm test`, e2e in CI.
- Both apps: routing tests; screen tests per payload (copy, order, no back, one control); fit at 17 sizes; previews
  for every payload at 320 and 430; evidence at 375 × 667, 390 × 844, 430 × 932 (9 per platform).
- `verify-partner-end.py` (copy from the ticket at run time; B has no gendered pronoun and no "unsafe"; A has no rate
  value and no "less visible / lose App access"; one screen component per platform); all checkers.
- Emulator walk of all three payloads LIVE on the demo server, including "B in the chat when A ends" and a kill +
  reopen before tapping.

## 7. Owed by others

- **Philipp:** the push category (shared with the cancel push); push copy (deferred project-wide); Open item 1.
- **Devices:** the two-phone recording the ticket asks for; a real iPhone; TalkBack / VoiceOver.
