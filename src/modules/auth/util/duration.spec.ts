import { parseDurationMs } from './duration';

describe('parseDurationMs', () => {
  it('parses each unit', () => {
    expect(parseDurationMs('500ms')).toBe(500);
    expect(parseDurationMs('900s')).toBe(900_000);
    expect(parseDurationMs('15m')).toBe(900_000);
    expect(parseDurationMs('24h')).toBe(86_400_000);
    expect(parseDurationMs('30d')).toBe(2_592_000_000);
  });

  it('treats a bare number as seconds', () => {
    expect(parseDurationMs('300')).toBe(300_000);
  });

  it('throws on malformed input', () => {
    expect(() => parseDurationMs('abc')).toThrow();
    expect(() => parseDurationMs('15x')).toThrow();
  });
});
