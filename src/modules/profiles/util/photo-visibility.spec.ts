import { PhotoModerationStatus } from '../entities/profile-photo.entity';
import {
  VISIBLE_PHOTO_STATUSES,
  isPhotoVisibleToOthers,
} from './photo-visibility';

describe('photo visibility (post-moderation: hide only rejected)', () => {
  it('shows approved and pending photos to others', () => {
    expect(isPhotoVisibleToOthers(PhotoModerationStatus.Approved)).toBe(true);
    expect(isPhotoVisibleToOthers(PhotoModerationStatus.Pending)).toBe(true);
  });

  it('never shows a rejected photo to others', () => {
    expect(isPhotoVisibleToOthers(PhotoModerationStatus.Rejected)).toBe(false);
  });

  it('VISIBLE_PHOTO_STATUSES is pending + approved, never rejected', () => {
    expect(VISIBLE_PHOTO_STATUSES).toEqual(
      expect.arrayContaining([
        PhotoModerationStatus.Pending,
        PhotoModerationStatus.Approved,
      ]),
    );
    expect(VISIBLE_PHOTO_STATUSES).not.toContain(PhotoModerationStatus.Rejected);
  });
});
