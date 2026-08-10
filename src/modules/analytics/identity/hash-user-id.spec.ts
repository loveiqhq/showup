import { hashUserId } from './hash-user-id';

describe('hashUserId (server-side analytics identity anonymisation)', () => {
  const salt = 'test-global-analytics-salt';

  it('is deterministic — the same user id and salt always produce the same hash', () => {
    expect(hashUserId('user-123', salt)).toBe(hashUserId('user-123', salt));
  });

  it('produces a 64-character lowercase hex SHA-256 string', () => {
    expect(hashUserId('user-123', salt)).toMatch(/^[0-9a-f]{64}$/);
  });

  it('never contains the raw user id (the real id must not be recoverable from the hash)', () => {
    expect(hashUserId('user-123', salt)).not.toContain('user-123');
  });

  it('produces different hashes for different users', () => {
    expect(hashUserId('user-123', salt)).not.toBe(hashUserId('user-456', salt));
  });

  it('changes when the salt changes, so the mapping cannot be reproduced without the secret salt', () => {
    expect(hashUserId('user-123', salt)).not.toBe(
      hashUserId('user-123', 'a-different-salt'),
    );
  });

  it('refuses to hash without a salt rather than silently producing a weak, reproducible hash', () => {
    expect(() => hashUserId('user-123', '')).toThrow();
  });
});
