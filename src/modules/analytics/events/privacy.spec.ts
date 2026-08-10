import { sanitizeProperties } from './privacy';

describe('sanitizeProperties (governance §3 prohibited-data enforcement)', () => {
  it('strips unmasked PII: raw email, phone, exact coordinates and address', () => {
    const clean = sanitizeProperties({
      email: 'a@b.com',
      phone: '+38761000000',
      latitude: 43.85,
      longitude: 18.41,
      address: 'Main St 1',
      position: 3,
    });
    expect(clean).toEqual({ position: 3 });
  });

  it('strips private chat content', () => {
    const clean = sanitizeProperties({
      message: 'hi',
      message_text: 'hey there',
      char_count: 5,
    });
    expect(clean).toEqual({ char_count: 5 });
  });

  it('strips exact age and date of birth but keeps the bucketed age_band', () => {
    const clean = sanitizeProperties({
      age: 27,
      dob: '1999-01-01',
      age_band: '25-34',
    });
    expect(clean).toEqual({ age_band: '25-34' });
  });

  it('strips secrets: passwords, tokens, push tokens and card details', () => {
    const clean = sanitizeProperties({
      password: 'x',
      access_token: 'y',
      push_token: 'z',
      card_number: '4111111111111111',
      ok: true,
    });
    expect(clean).toEqual({ ok: true });
  });

  it('keeps allowed analytics fields untouched (including coarse city_geohash and token_type)', () => {
    const props = {
      char_count: 10,
      position: 2,
      city_geohash: 'u2m9x',
      showup_band: 'trusted',
      token_type: 'apns',
    };
    expect(sanitizeProperties(props)).toEqual(props);
  });

  it('is case- and separator-insensitive about prohibited keys', () => {
    expect(sanitizeProperties({ Email: 'a@b.com', PhoneNumber: '1' })).toEqual(
      {},
    );
  });

  it('returns a new object and does not mutate the input', () => {
    const input = { email: 'a@b.com', position: 1 };
    sanitizeProperties(input);
    expect(input.email).toBe('a@b.com');
  });
});
