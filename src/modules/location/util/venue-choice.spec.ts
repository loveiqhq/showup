import { pickBalancedVenue, VenueTravel } from './venue-choice';

const v = (id: string, minutesA: number, minutesB: number): VenueTravel => ({
  id,
  minutesA,
  minutesB,
});

describe('pickBalancedVenue', () => {
  it('returns null when there are no candidates', () => {
    expect(pickBalancedVenue([])).toBeNull();
  });

  it('picks the venue where both people travel for the most similar time', () => {
    const chosen = pickBalancedVenue([
      v('beside-a', 2, 40),
      v('between', 20, 22),
      v('beside-b', 38, 3),
    ]);
    expect(chosen?.id).toBe('between');
  });

  it('does not favour a venue just because it is close to one person', () => {
    // 'beside-a' has the shortest single journey of any option, and the smallest total. It still
    // loses, because one person would travel twenty times as long as the other.
    const chosen = pickBalancedVenue([
      v('beside-a', 1, 20),
      v('between', 11, 11),
    ]);
    expect(chosen?.id).toBe('between');
  });

  it('breaks a tie on the shorter combined journey', () => {
    // Both are perfectly balanced, so the closer pair wins — equal-but-further is not fairer, it is
    // just worse for both people.
    const chosen = pickBalancedVenue([v('far', 30, 30), v('near', 10, 10)]);
    expect(chosen?.id).toBe('near');
  });

  it('treats the difference as absolute, whichever person travels further', () => {
    const chosen = pickBalancedVenue([
      v('a-further', 25, 15),
      v('b-further', 12, 14),
    ]);
    expect(chosen?.id).toBe('b-further');
  });

  it('handles a single candidate', () => {
    expect(pickBalancedVenue([v('only', 5, 45)])?.id).toBe('only');
  });
});
