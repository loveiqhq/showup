# Date 08 — what the other side sees: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When A ends a date from Date 06, B is moved at once to one full screen (three payloads) whose only control is `Proceed to review`.

**Architecture:** The server already sends B's ended outcome with `partnerScreen`; this adds B's push. Each app gains one `PartnerEndedScreen` (one layout, three payloads), a new `EndedRoute.PartnerEnded` from the existing `endedRoute`, a full-screen no-exit route, and the `startReview` slot that stands in for flow 10.

**Tech Stack:** NestJS + TypeORM (server), Kotlin / Jetpack Compose + Robolectric (Android), SwiftUI + XCTest (iOS, compiled only in CI on macos-15), Python checkers.

**Spec:** `docs/superpowers/specs/2026-11-22-date-08-partner-end-design.md` (rulings R1–R10, the value table in §4).

## Global Constraints

- Copy is the Jira ticket's, verbatim (`design_handoff_showup/date/tickets/08-partner-end.md` `## Copy -- final strings`); typographic apostrophes exactly as the ticket writes them (the 08 ticket uses straight `'`).
- Numbers from `design_handoff_showup/date/screen-partner-end-reference.jsx` and spec §4; never from the PNG.
- `{name}` is A's (the match's) first name. Product terms: **Show Up**, **Show-up Rate**.
- Both apps change in the same task pair and stay in parity (names, rules, tests). Android first, iOS mirrors it.
- Tokens, not literals: `BlushWash` / `LilacWash` (Android), the iOS twins; `Orange`, `Purple`/primary 500, `SuccessFg` (`#0A7A47`), `BorderSoft`, `Elevated`, `ShadowMd`. No new hex in a screen.
- Touch floor 44 on both platforms. No emoji anywhere. Comments say why, not what.
- No logging of a name, a phone number, a code, a token or an email.
- Memory on this machine: `./gradlew --stop` first; ONE Gradle at a time with `-Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process`; run narrow `--tests` filters locally, CI runs the full suite. `JAVA_HOME=$HOME/.jdks/jbr-21.0.11`.
- Server edits: `npm run lint && npm test` before committing (lint has 0 errors today; warnings are old).
- Never commit `.semgrep/` or `.superpowers/`. Commit on `feat/date-08-partner-end` only; push every commit.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A's end lands while B has a Date 05 / 06 / 07 sheet open:** B sees A's screen, the sheet is gone, and after process death it does not come back. Tests: Task 3 `PartnerEnded closes every sheet` and Task 4's iOS draft-clear test.
2. **Two reads see the same end** (the chat's poll and the Date screen's): B is routed once, `screen_viewed` fires once per showing. Tests: Task 3 / 4 once-per-date.
3. **B kills the app before tapping:** the next launch shows the same payload with no referrer. Tests: Task 3 / 4 cold-start routing.
4. **Largest system font and 320 × 686:** the CTA stays reachable by scrolling, nothing clips. Tests: Task 2 / 4 fit at 17 sizes plus the largest-type row.
5. **H (cancel on you) and Date 02's G / H are untouched.** Tests: Task 3 / 4 `endedRoute` for every reason × `endedByYou`.

---

### Task 1: Server — B's push after a Date 06 end

**Files:**
- Modify: `src/modules/notifications/util/preferences.ts` (type union + `CATEGORY_BY_TYPE`)
- Modify: `src/modules/notifications/messages/catalog.ts` (new entry after `date_cancelled`)
- Modify: `src/modules/dates/date-end.service.ts` (dispatch after commit, the pattern of `date-cancel.service.ts` ~lines 165–185)
- Test: `src/modules/dates/date-end.service.spec.ts`, `test/date-end.e2e-spec.ts`, the catalog / preferences specs if they enumerate kinds

**Interfaces:**
- Produces: notification kind `date_partner_ended`, category `engagement`, params `{ name?: string }`, data `{ type: 'date_partner_ended', dateId }`.

- [ ] **Step 1: failing tests.** Service spec `sends date_partner_ended to the match only, once, after the end commits` (dispatch called once with B's id and kind `date_partner_ended`; not with A's id); `a failed push never fails the end` (dispatch rejects → `end()` still resolves `{ reason }`); `a refused end pushes nothing` (409 paths: dispatch not called). E2E: after `POST` end as A (each of the three reasons), exactly one queued/sent notification of kind `date_partner_ended` for B and none for A. Catalog spec: EN title `Leonie ended your date`, no-name `Your date was ended`, body `Open Show Up to see what happens next.`; DE `Leonie hat euer Date beendet` / `Dein Date wurde beendet` / `Öffne Show Up, um zu sehen, wie es weitergeht.`
- [ ] **Step 2:** run them, see them fail.
- [ ] **Step 3:** implement. The catalog entry carries the same "TEMPORARY placeholder copy" comment as `date_cancelled`, naming Date 08 Open item 2. The name is A's first name only; never the reason, a place or a time.
- [ ] **Step 4:** `npm run lint && npm test` (0 lint errors, all unit tests pass); run the date-end e2e file locally if Postgres is up, else say it runs in CI.
- [ ] **Step 5:** commit `feat(dates): push date_partner_ended to the match after a Date 06 end (SHOWUP-199)`; push.

### Task 2: Android — `PartnerEndedScreen`, one layout, three payloads

**Files:**
- Create in `mobile/welcome-screen/android-preview-project/app/src/main/java/com/showup/date/partnerend/`: `PartnerEndedCopy.kt`, `PartnerEndedRules.kt`, `PartnerEndedScreen.kt`, `PartnerEndedSamples.kt`, `PartnerEndedPreviews.kt`
- Test (under `app/src/test/java/com/showup/`): `date/partnerend/PartnerEndedRulesTest.kt`, `date/partnerend/PartnerEndedScreenTest.kt`, `fit/PartnerEndedFitTest.kt`

**Interfaces:**
- Consumes: `PartnerScreen` (`date/ActiveDate.kt`), `EndedOutcome`, primitives `WelcomeBackdrop`, `MatchPhoto`, `IconTile`, `pillShadows`, `WashHeadline`, `prettyWrap`, `cssLineBox`, `PrimaryButton`, `BrandIcon` (`UserX`, `MessageCircle`, `ShieldCheck`, `Hand`, `Heart`, `Search`, `Clock`, `Check`, `HeartFilled`). Find the Atmosphere `hero` variant's existing mapping (grep `Atmosphere` / `OrbPlacement` callers that cite `variant="hero"`) and reuse it.
- Produces:
  - `data class PartnerEndedPayload(...)` holding every per-payload value of spec §4 (hero brush, hero padding, avatar gap, badge colour / icon / size / stroke, rows margin-top, rows, accent / tint) and `fun partnerEndedPayload(screen: PartnerScreen): PartnerEndedPayload`.
  - `fun partnerEndedScreenId(screen: PartnerScreen): ProfileScreen` is Task 3's; do not add it here.
  - `@Composable fun PartnerEndedScreen(screen: PartnerScreen, name: String, photoUrl: String?, onProceed: () -> Unit)`.
  - Test tags: `PARTNER_ENDED_CTA_TAG = "partnerEnded:cta"`, `PARTNER_ENDED_ROW_TAG = "partnerEnded:row"`, `PARTNER_ENDED_HERO_TAG = "partnerEnded:hero"`.

- [ ] **Step 1: failing tests.**
  - `PartnerEndedRulesTest`: `each screen maps to its payload` (YouNoShowed → blush, padding 30/24, gap 18, Clock 16 / 2.6, 3 rows UserX / MessageCircle / ShieldCheck orange; DateEndedDiscomfort → blush, 28/22, gap 16, Hand 15 / 2.6, rows margin 16; DateClosedPositive → lilac, 30/24, gap 18, Check 15 / 3 on `SuccessFg`, 2 rows Heart / Search violet); `layout values shared by all three are equal` (radius, avatar 76, badge 30, tile 36 / 12, row gap 10).
  - `PartnerEndedScreenTest` (per payload): the headline, sub and every row read the ticket's strings exactly (A's sub as two lines, "after waiting for you." then "It's now closed."); `Proceed to review` exists and is the only node with a click action; no node reads "unsafe", "she", "he", "her", "his" on B; no digit and no "less visible" / "lose App access" on A; tapping the CTA calls `onProceed` once.
  - `PartnerEndedFitTest`: every payload at the 17 `FitHarness` sizes and the largest-type row: no clipped text, the CTA reachable (use `ScrollReach.kt`), the CTA below the last row (never overlapping); on 375 × 667 the CTA is reached by scrolling for A and B.
- [ ] **Step 2:** run them, see them fail (`--tests "com.showup.date.partnerend.*" --tests "com.showup.fit.PartnerEndedFitTest"`).
- [ ] **Step 3:** implement. Copy constants in `PartnerEndedCopy` as runs (bold / italic pieces as their own constants, the way `CancelCopy` / `DateEndCopy` do), with `%s` for the name. Layout per R5: one `verticalScroll` column whose content is at least the viewport tall (measure the viewport with `BoxWithConstraints` and use `heightIn(min = maxHeight)` with `Arrangement.SpaceBetween` or an equivalent — `weight` does nothing under an unbounded scroll), hero → rows → min-14 space → CTA (bottom padding 6), system-bar insets. Row padding 15 (the reference's 14 inside a 1 px border CSS counts in the box). Tile at the row top, text centred on it. The file opens with a comment saying why this is not `DateEndedScreen` (spec R4).
- [ ] **Step 4:** run the tests above (one Gradle, flags from Global Constraints): all pass. Add previews for all three payloads at 320 × 686 and 430 × 932.
- [ ] **Step 5:** commit `feat(date): Date 08's PartnerEndedScreen, one layout and three payloads, on Android (SHOWUP-199)`; push.

### Task 3: Android — routing, no exit, `startReview`, tracking, dev frames, evidence

**Files:**
- Modify: `date/cancel/CancelRules.kt` (`EndedRoute` + `endedRoute`), `AppRoute.kt` (new route), `chat/ChatRouting.kt` (a `partnerEndedRoute(outcome, photoUrl)` beside `outcomeRoute`), `MainActivity.kt` (`routeEnded`, the route's composable, `startReview`), `profile/ProfileAnalytics.kt` (three `ProfileScreen` rows), `chat/ChatRouting.kt` or the analytics owner of `outcomeShown` (the `screen_viewed`), `dev/DevJump.kt` + `dev/DevDateDemo.kt` (States rows, live frames), `DevMenu.kt` labels
- Modify (tests): `app/src/test/java/com/showup/fit/EvidenceScreenshots.kt`; Create: `date/partnerend/PartnerEndedRoutingTest.kt` (or extend the existing `CancelRules` / routing tests)

**Interfaces:**
- Consumes: Task 2's `PartnerEndedScreen`, `partnerEndedPayload`.
- Produces: `EndedRoute.PartnerEnded`; `fun partnerEndedScreenId(screen: PartnerScreen): ProfileScreen`; `ProfileScreen.DatePartnerEndedNoShow("date_partner_ended_no_show", "Date - Partner ended no-show")`, `…Uncomfortable("date_partner_ended_uncomfortable", "Date - Partner ended uncomfortable")`, `…Met("date_partner_ended_met", "Date - Partner ended met")`; dev frame ids `199A` (no-show), `199B` (uncomfortable), `199C` (met), `199A-chat` (B in the chat when A ends with no-show).

- [ ] **Step 1: failing tests.**
  - `endedRoute`: `Date 06 end on you is PartnerEnded` for each of the three reasons with `endedByYou = false` and `partnerScreen` set; `your own Date 06 end lands` (`endedByYou = true` → `Land`); `chat end is DateEnded` either way; `cancel on you is PartnerCancelled`; `your own cancel lands`; `a Date 06 end with no partnerScreen lands` (an older server).
  - `partnerEndedScreenId` maps the three screens to the three ids and names above.
  - Routing (Compose or plain-function test, whichever the codebase uses for `LandedOnce`): `two reads of one end route once`; `a cold start shows the payload with no referrer`; `reached from the chat carries the chat's referrer`; `screen_viewed fires each time the screen is shown`; `Proceed fires nothing and calls startReview`; `system back on the screen does nothing` (the route is still on top after `pressBack`).
  - `PartnerEnded closes every sheet`: with the Date 06 sheet, the Date 07 guard and a Date 05 picker open on the demo, A's end routes to the screen and none of the sheets is present; their saved state is cleared.
- [ ] **Step 2:** run them, see them fail.
- [ ] **Step 3:** implement.
  - `endedRoute` order: chat → `DateEnded`; cancel on you → `PartnerCancelled`; `partnerScreen != null && !endedByYou` → `PartnerEnded`; else `Land`. Update its KDoc (Date 08 is no longer "to plug in").
  - `routeEnded` shows the new route via `navigate(... ) { popUpTo(AppRoute.Flow) { inclusive = false }; launchSingleTop = true }`, once per `dateId` (reuse `LandedOnce` or a sibling), with `outcomeFrom` as the referrer (none on a launch, as H does).
  - The route's composable: no TabBar, `BackHandler(enabled = true) {}`, `PartnerEndedScreen(..., onProceed = { startReview(outcome) })`.
  - `startReview(outcome)`: the flow 10 slot (spec R1). Today: mark the end seen and `findNextDate()`, as `landAfterEnd` does. A KDoc says what flow 10 replaces it with (`ScreenRating`, `claimedNoShow` for `YouNoShowed`, mark-seen on the review's send).
  - Leaving: clear the Date 05 / 06 / 07 sheets' saved state for the date (find how each restores: `rememberSaveable` keys / view-model saved state) so none reappears.
  - Dev: States rows `199A`–`199C` (ticket label `SHOWUP-199 What the other side sees`, labels `A · You didn't show up`, `B · Uncomfortable`, `C · We met`) are LIVE frames on `DevDateDemo`: open the demo date, then the demo's `partnerEnds(reason)` so the real routing shows the screen; `199A-chat · B in the chat when A ends` opens the chat and has Leonie end with no-show 3 s later. Rename the Screens-tab "Leonie ends the date …" labels from "SHOWUP-197, Date 08 slot" to "SHOWUP-199".
  - Evidence: `EvidenceScreenshots` adds `SHOWUP-199_A-no-show`, `SHOWUP-199_B-uncomfortable`, `SHOWUP-199_C-met` at 375 × 667, 390 × 844, 430 × 932 (9 PNGs); for 375 × 667 also shoot A and B scrolled to the end.
- [ ] **Step 4:** run the new tests and the existing `CancelRules`, `DateEnd`, chat routing and `com.showup.dev.*` tests (one Gradle); then `-Pevidence` for the 199 shots only if the harness allows a filter.
- [ ] **Step 5:** commit `feat(date): route the match of a Date 06 end to Date 08, no exit but Proceed to review, on Android (SHOWUP-199)`; push.

### Task 4: iOS — the twin of Tasks 2 and 3

**Files:**
- Create in `mobile/welcome-screen/ios-app/ShowUpWelcome/`: `PartnerEndedCopy.swift`, `PartnerEndedRules.swift`, `PartnerEndedScreen.swift`, `PartnerEndedSamples.swift`, `PartnerEndedPreviews.swift`
- Modify: `CancelRules.swift` (`EndedRoute`, `endedRoute`), `ShowUpWelcomeApp.swift` (`routeEnded`, the route, `startReview`), `ProfileAnalytics.swift` (three `ProfileScreen` cases), `DevCheckIn.swift` (States rows), `DevDateDemo.swift`, `DevMenu.swift` (labels)
- Test (in `ShowUpWelcomeTests/`): `PartnerEndedRulesTests.swift`, `PartnerEndedScreenTests.swift`, `PartnerEndedFitTests.swift`, `EvidenceShots.swift` (the 9 shots), routing tests beside `CancelRulesTests.swift`
- Regenerate `ShowUpWelcome.xcodeproj` with `python gen_pbxproj.py` (CI fails on a stale project; `audit/check-pbxproj.py` checks it).

**Interfaces:** the Swift spellings of Task 2 / 3's: `PartnerEndedPayload`, `partnerEndedPayload(_:)`, `PartnerEndedScreen(screen:name:photoURL:onProceed:)`, `EndedRoute.partnerEnded`, `partnerEndedScreenID(_:)`, `ProfileScreen.datePartnerEndedNoShow / …Uncomfortable / …Met`, dev ids `199A`–`199C`, `199A-chat`.

- [ ] **Step 1: failing tests** mirroring every Task 2 / 3 test name, plus `a restored Date 05 / 06 / 07 draft is cleared when Date 08 shows` (the `@SceneStorage` drafts in `CancelModel`, `DateEndModel`, `ChangePickerModel`).
- [ ] **Step 2:** cannot be run here (no Mac); say so.
- [ ] **Step 3:** implement. Full-screen route with no back button, `interactiveDismissDisabled`, no swipe-back, no TabBar. Layout per R5 with `ScrollView` + a column of `minHeight` = the viewport (`GeometryReader` for the size only, per `ios-app/CLAUDE.md`). `WashHeadline` for the italic run. Strict concurrency: `@MainActor` views, no escape hatches. iOS 17 APIs only (run `audit/check-ios-availability.py`).
- [ ] **Step 4:** run `python audit/check-swift-concurrency.py`, `check-swift-structure.py`, `check-swift-arg-order.py`, `check-ios-availability.py`, `check-pbxproj.py`; all pass. iOS compiles and tests in CI only.
- [ ] **Step 5:** commit `feat(date): Date 08 on iOS -- PartnerEndedScreen, the routing, no exit, startReview, dev frames and evidence (SHOWUP-199)`; push.

### Task 5: The copy checker and the parity checks

**Files:**
- Create: `mobile/welcome-screen/audit/verify-partner-end.py` (shape of `audit/verify-end.py`)
- Modify only if a checker requires it: tracking build-status notes for the three §11 rows.

- [ ] **Step 1:** write the checker. It reads the ticket's copy section at run time and checks every backticked string in both apps' `PartnerEndedCopy` (runs joined in order); B's strings hold no `she / he / her / his / him` and no `unsafe`; A's hold no digit and no `less visible` / `lose App access`; exactly one `fun PartnerEndedScreen(` / `struct PartnerEndedScreen`; the three §11 ids in both apps' `ProfileScreen`; no `date_ended` fired from either app's Date 08 code. One negative self-test (mutation) per group, as `verify-end.py` has.
- [ ] **Step 2:** run it: all pass; then every `audit/verify-*.py` and `audit/check-*.py` by exit code: 0 failing.
- [ ] **Step 3:** commit `test(audit): verify-partner-end.py quotes Date 08's ticket on both apps (SHOWUP-199)`; push.
