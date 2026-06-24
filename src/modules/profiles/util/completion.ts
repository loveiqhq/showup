/** Minimum number of photos a profile needs before it counts as complete. */
export const MIN_PHOTOS = 4;

/**
 * Whether a profile is "complete": it has a display name, a date of birth, and at least
 * MIN_PHOTOS photos. Pure decision logic (no DB) so it can be unit-tested — callers pass in
 * the current photo count. (The upload cap, MAX_PHOTOS = 6, is enforced separately in PhotosService.)
 */
export function isProfileComplete(input: {
  displayName: string | null;
  dateOfBirth: string | null;
  photoCount: number;
}): boolean {
  return (
    Boolean(input.displayName) &&
    Boolean(input.dateOfBirth) &&
    input.photoCount >= MIN_PHOTOS
  );
}
