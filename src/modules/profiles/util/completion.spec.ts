import { isProfileComplete, MIN_PHOTOS } from './completion';

describe('isProfileComplete', () => {
  const base = {
    displayName: 'Leo',
    dateOfBirth: '1998-04-23',
    photoCount: MIN_PHOTOS,
  };

  it('completes a profile with a name, date of birth, and the minimum photos', () => {
    expect(isProfileComplete(base)).toBe(true);
  });

  it('requires at least 4 photos', () => {
    expect(MIN_PHOTOS).toBe(4);
    expect(isProfileComplete({ ...base, photoCount: 3 })).toBe(false);
    expect(isProfileComplete({ ...base, photoCount: 4 })).toBe(true);
  });

  it('is incomplete without a display name', () => {
    expect(isProfileComplete({ ...base, displayName: null })).toBe(false);
  });

  it('is incomplete without a date of birth', () => {
    expect(isProfileComplete({ ...base, dateOfBirth: null })).toBe(false);
  });
});
