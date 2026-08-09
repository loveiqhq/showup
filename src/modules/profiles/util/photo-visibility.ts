import { PhotoModerationStatus } from '../entities/profile-photo.entity';

/**
 * Photo moderation is POST-moderation: a photo is shown to others (and counts toward a complete
 * profile) unless staff have REJECTED it. Pending (not-yet-reviewed) and approved photos both count;
 * a rejected photo is never shown to other users and never counts toward completeness.
 *
 * Single source of truth for "which photo statuses are publicly usable" — the display accessor and
 * the completeness count both key off it, so they can't drift apart.
 */
export const VISIBLE_PHOTO_STATUSES: PhotoModerationStatus[] = [
  PhotoModerationStatus.Pending,
  PhotoModerationStatus.Approved,
];

/** Whether a photo may be shown to users other than its owner. Rejected photos never are. */
export function isPhotoVisibleToOthers(status: PhotoModerationStatus): boolean {
  return status !== PhotoModerationStatus.Rejected;
}
