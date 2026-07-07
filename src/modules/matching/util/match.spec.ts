import { LikeStatus, MatchStatus, orderedUserPair } from './match';

describe('orderedUserPair', () => {
  it('sorts two ids ascending', () => {
    expect(orderedUserPair('bbb', 'aaa')).toEqual(['aaa', 'bbb']);
  });

  it('is the same regardless of the argument order', () => {
    expect(orderedUserPair('aaa', 'bbb')).toEqual(
      orderedUserPair('bbb', 'aaa'),
    );
  });

  it('leaves an already-ordered pair ordered', () => {
    expect(orderedUserPair('a', 'b')).toEqual(['a', 'b']);
  });
});

describe('status enums', () => {
  it('exposes the like statuses', () => {
    expect(LikeStatus.Active).toBe('active');
    expect(LikeStatus.Matched).toBe('matched');
  });

  it('exposes the match statuses', () => {
    expect(MatchStatus.Active).toBe('active');
    expect(MatchStatus.Cancelled).toBe('cancelled');
    expect(MatchStatus.Expired).toBe('expired');
  });
});
