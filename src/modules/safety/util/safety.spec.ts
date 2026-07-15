import {
  dedupeBlockIds,
  evaluateVerification,
  isDiscoverable,
  ModerationStanding,
  VerificationOutcome,
} from './safety';

describe('ModerationStanding', () => {
  it('exposes the standings', () => {
    expect(ModerationStanding.Active).toBe('active');
    expect(ModerationStanding.PendingReview).toBe('pending_review');
    expect(ModerationStanding.Rejected).toBe('rejected');
    expect(ModerationStanding.Limited).toBe('limited');
    expect(ModerationStanding.Banned).toBe('banned');
  });
});

describe('isDiscoverable', () => {
  it('lets active and pending-review users be surfaced', () => {
    expect(isDiscoverable(ModerationStanding.Active)).toBe(true);
    expect(isDiscoverable(ModerationStanding.PendingReview)).toBe(true);
  });

  it('hides limited and banned users from discovery and matching', () => {
    expect(isDiscoverable(ModerationStanding.Limited)).toBe(false);
    expect(isDiscoverable(ModerationStanding.Banned)).toBe(false);
  });
});

describe('dedupeBlockIds', () => {
  it('unions block lists from both directions', () => {
    expect(dedupeBlockIds(['a', 'b'], ['c'])).toEqual(['a', 'b', 'c']);
  });

  it('removes duplicates when the same pair blocked each other', () => {
    expect(dedupeBlockIds(['a', 'b'], ['b', 'd'])).toEqual(['a', 'b', 'd']);
  });

  it('returns an empty list when nobody is blocked', () => {
    expect(dedupeBlockIds([], [])).toEqual([]);
  });
});

describe('evaluateVerification', () => {
  it('verifies only when the live check AND the face match both pass', () => {
    expect(
      evaluateVerification({ livenessPassed: true, faceMatched: true }),
    ).toBe(VerificationOutcome.Verified);
  });

  it('rejects when the live check fails', () => {
    expect(
      evaluateVerification({ livenessPassed: false, faceMatched: true }),
    ).toBe(VerificationOutcome.Rejected);
  });

  it('rejects when the face does not match the profile photos', () => {
    expect(
      evaluateVerification({ livenessPassed: true, faceMatched: false }),
    ).toBe(VerificationOutcome.Rejected);
  });
});
