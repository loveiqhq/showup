import { ageFromDateOfBirth, isAtLeast18 } from './age';

describe('age helpers', () => {
  it('returns null for missing or invalid input', () => {
    expect(ageFromDateOfBirth(null)).toBeNull();
    expect(ageFromDateOfBirth('not-a-date')).toBeNull();
  });

  it('computes age in whole years', () => {
    const year = new Date().getUTCFullYear();
    expect(ageFromDateOfBirth(`${year - 30}-01-01`)).toBeGreaterThanOrEqual(29);
  });

  it('enforces the 18+ rule', () => {
    const year = new Date().getUTCFullYear();
    expect(isAtLeast18(`${year - 30}-06-15`)).toBe(true);
    expect(isAtLeast18(`${year - 10}-06-15`)).toBe(false);
    expect(isAtLeast18(null)).toBe(false);
  });
});
