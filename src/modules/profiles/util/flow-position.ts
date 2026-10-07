/**
 * The saved flow position -- how far through profile creation an account has walked.
 *
 * WHY THIS EXISTS AT ALL. Resume (flow README rule 4a) was built by DERIVING the position from facts
 * the server already holds: a name, a verified address, four photos, a prompt. That works for every
 * mandatory step and fails for the first skippable one, because a skip saves nothing -- rule 0:
 * "Skip advances the flow position, saves nothing for that step". Height, dating language,
 * education, religion and politics can all be skipped, so no fact on the account can say they were
 * passed. Without a stored position, a relaunch after prompts landed on Home and silently skipped
 * every screen from media to politics.
 *
 * THE VOCABULARY IS `enums.json` §2 `step_id`, the registry's flow-progress vocabulary ("where in the
 * sequence"), never a screen id. Only the steps AFTER prompts are here: everything before them is
 * still derived from facts, which remain the stronger evidence. The media screen carries both
 * `media_voice` and `media_video`, which share step_index 3 in §2, so both are accepted at the same
 * rank.
 *
 * Steps that exist in §2 but have no screen yet (interests, life_now, life_ahead, habits) are absent
 * on purpose: accepting a value nothing can set would make this list a wish rather than a contract.
 * Add each one when its screen ships.
 */
export const FLOW_POSITION_ORDER: readonly (readonly string[])[] = [
  ['media_voice', 'media_video'],
  ['notifications'],
  ['reachability'],
  ['location'],
  ['height'],
  ['gender'],
  ['orientation'],
  ['dating_language'],
  ['education'],
  ['religion'],
  ['politics'],
];

/** Every accepted position, flat, for validation. */
export const FLOW_POSITIONS: readonly string[] = FLOW_POSITION_ORDER.flat();

/** A position's place in the walk, or -1 when it is not a position at all. */
export function flowPositionRank(position: string | null | undefined): number {
  if (position == null) return -1;
  return FLOW_POSITION_ORDER.findIndex((rank) => rank.includes(position));
}

/**
 * The further of the stored position and a newly reported one.
 *
 * MONOTONIC BY DESIGN. Going back is a normal thing to do -- the back chevron on gender returns to
 * height -- and it must not rewind the account: a user who goes back to fix their height and then
 * closes the app has still walked as far as gender, and resume lands on the last incomplete step,
 * not the last one looked at. A report at the same rank keeps the stored value, so the media
 * screen reporting either of its two ids never reads as movement. A stored value outside the
 * vocabulary ranks -1 and is overwritten by any real report.
 *
 * ONLY SAFE UNDER THE ROW LOCK. This is a read-then-write: two saves in flight that each read the
 * same old value would let whichever commits last win, rewinding the position when the slower one
 * carried the earlier step. `ProfilesService.update` calls it with the profile row locked
 * (`pessimistic_write`), so the second save reads the first one's result. Calling it on an
 * unlocked read reintroduces the race.
 */
export function advanceFlowPosition(
  stored: string | null,
  reported: string,
): string {
  return flowPositionRank(reported) > flowPositionRank(stored)
    ? reported
    : (stored ?? reported);
}
