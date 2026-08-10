import {
  buildEnvelope,
  SCHEMA_VERSION,
  AnalyticsEnvelopeInput,
} from './envelope';

const baseInput = (): AnalyticsEnvelopeInput => ({
  eventName: 'checkin_completed',
  eventId: 'evt-1',
  timestampUtc: '2026-07-31T10:00:00.000Z',
  context: { source: 'server', anonymousId: 'anon-1' },
});

describe('buildEnvelope (standard background-details stamp)', () => {
  it('stamps the schema version, event id, name, source, timestamp and anonymous id', () => {
    const e = buildEnvelope(baseInput());
    expect(e.schema_version).toBe(SCHEMA_VERSION);
    expect(e.event_id).toBe('evt-1');
    expect(e.event_name).toBe('checkin_completed');
    expect(e.event_source).toBe('server');
    expect(e.timestamp_utc).toBe('2026-07-31T10:00:00.000Z');
    expect(e.anonymous_id).toBe('anon-1');
  });

  it('sets user_id_hashed to null when there is no signed-in user (pre-auth)', () => {
    expect(buildEnvelope(baseInput()).user_id_hashed).toBeNull();
  });

  it('includes the hashed user id when present', () => {
    const input = baseInput();
    input.context.userIdHashed = 'abc123';
    expect(buildEnvelope(input).user_id_hashed).toBe('abc123');
  });

  it('defaults is_test_account to false', () => {
    expect(buildEnvelope(baseInput()).is_test_account).toBe(false);
  });

  it('merges event-specific properties alongside the standard fields', () => {
    const input = baseInput();
    input.properties = { duration_s: 42, steps_skipped: 1 };
    const e = buildEnvelope(input);
    expect(e.duration_s).toBe(42);
    expect(e.steps_skipped).toBe(1);
  });

  it('never lets an event property overwrite a reserved envelope field', () => {
    const input = baseInput();
    input.properties = { event_name: 'HACKED', schema_version: '9.9.9' };
    const e = buildEnvelope(input);
    expect(e.event_name).toBe('checkin_completed');
    expect(e.schema_version).toBe(SCHEMA_VERSION);
  });

  it('omits optional context fields that were not provided (no undefined noise)', () => {
    const e = buildEnvelope(baseInput());
    expect('device_model' in e).toBe(false);
    expect('city_geohash' in e).toBe(false);
  });
});
