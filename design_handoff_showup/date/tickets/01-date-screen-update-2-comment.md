# [Date 01] Update 9 Nov 2026 — answers to the build questions

**This comment is the change list, not the spec.** The description has been updated in place (every change is marked **(9 Nov)**). Build and test from the description.

## Your questions

1. **No count on the locked Likes tab — yes.** The "2" is sample data. No count while a date is active; it returns when the date ends.
2. **Whole kilometres — yes.** Use Search's distance formatter so the two never disagree. `1.2 km` is sample data.
3. **Icons only where the design draws them — yes.** **Directions straight to the default maps app — yes**, no picker of ours. An Android chooser (no default set) is the OS's.
4. **Bottom bar wording — don't take it from the reference.** This ticket builds the sticky slot and the `checkedIn` swap only; a slot with nothing built ships empty. Check-in bar strings are final in **Date 03**; End Date bar strings come with **Date 06**.
5. **Load failed — yes:** `Couldn't load your date. Check your connection and try again.` · `Try again`. Replaces the content column; TabBar stays. Same pattern as Search 01.
6. **Profile back pill — yes:** `Your date`. Same rule as `All profiles` / `All likes`: the pill names where it goes back to.
7. **Calendar permission:** on **iOS 17+** present the system add-event sheet **without requesting access** — no popup at all. Only iOS 16 and earlier request access, with `NSCalendarsUsageDescription` = `Show Up only adds your date to your calendar. It never reads your events.` Android: nothing.

## What changed in the description

- **Decided before build:** seven bullets (load failed · Likes count · whole km · icons · Directions · Calendar permission · footer bars not built here)
- **Copy:** load failed · back pill `Your date` · iOS 16 calendar permission string
- **Behaviour:** first-load failure · Directions (default app) · Calendar (no access request on iOS 17+) · back pill label
- **Acceptance criteria:** five new lines; evidence adds the load-failed state at 390 × 844 and one iOS 17+ Calendar recording
- **Tracking:** no new events or properties. One rule added: a failed load fires nothing; `screen_viewed` and `date_screen_viewed` fire once the date has loaded.

## Spec sheet — re-exported 9 Nov

New frames **E · Load failed** (callout ⑲) and **F · Full profile, read-only** (callout ⑳). Also on the sheet: ⑫ distance in whole km, ⑰ no likes count on a disabled tab. Nothing else moved. Values: `ScreenMatchCoord` (`loadFailed`, `initialProfileOpen`) in `date/screen-date-reference.jsx` and `ScreenSearchProfile` (`readOnly`) in `search/screen-search-profile-reference.jsx`.
