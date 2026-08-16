import { REDACTED, redactForLog } from './redact';

describe('redactForLog', () => {
  it('redacts contact details, secrets and coordinates', () => {
    const out = redactForLog({
      phone: '+491701234567',
      email: 'leo@example.com',
      code: '123456',
      accessToken: 'abc',
      latitude: 52.52,
      longitude: 13.4,
    }) as Record<string, unknown>;

    expect(out.phone).toBe(REDACTED);
    expect(out.email).toBe(REDACTED);
    expect(out.accessToken).toBe(REDACTED);
    expect(out.latitude).toBe(REDACTED);
    expect(out.longitude).toBe(REDACTED);
  });

  it('redacts private message content', () => {
    const out = redactForLog({ message: 'see you at 8' }) as Record<
      string,
      unknown
    >;
    expect(out.message).toBe(REDACTED);
  });

  it('keeps identifiers and outcomes, which are the useful part of a log line', () => {
    const out = redactForLog({
      userId: 'u1',
      dateId: 'd1',
      status: 'sent',
      attempts: 3,
      durationMs: 42,
    }) as Record<string, unknown>;

    expect(out).toEqual({
      userId: 'u1',
      dateId: 'd1',
      status: 'sent',
      attempts: 3,
      durationMs: 42,
    });
  });

  it('matches keys regardless of case or punctuation', () => {
    const out = redactForLog({
      Email: 'a@b.c',
      phone_number: '+49',
      'access-token': 'x',
    }) as Record<string, unknown>;

    expect(out.Email).toBe(REDACTED);
    expect(out.phone_number).toBe(REDACTED);
    expect(out['access-token']).toBe(REDACTED);
  });

  it('keeps safe look-alikes such as token_type and city_geohash', () => {
    const out = redactForLog({
      token_type: 'Bearer',
      city_geohash: 'u33d',
      age_band: '25_34',
    }) as Record<string, unknown>;

    expect(out).toEqual({
      token_type: 'Bearer',
      city_geohash: 'u33d',
      age_band: '25_34',
    });
  });

  it('redacts inside nested objects and arrays', () => {
    const out = redactForLog({
      user: { id: 'u1', email: 'a@b.c' },
      recipients: [{ phone: '+49' }, { phone: '+50' }],
    }) as any;

    expect(out.user.id).toBe('u1');
    expect(out.user.email).toBe(REDACTED);
    expect(out.recipients[0].phone).toBe(REDACTED);
    expect(out.recipients[1].phone).toBe(REDACTED);
  });

  it('never mutates the input', () => {
    const input = { email: 'a@b.c', nested: { phone: '+49' } };
    redactForLog(input);
    expect(input.email).toBe('a@b.c');
    expect(input.nested.phone).toBe('+49');
  });

  it('passes primitives through untouched', () => {
    expect(redactForLog('hello')).toBe('hello');
    expect(redactForLog(42)).toBe(42);
    expect(redactForLog(null)).toBeNull();
  });
});
