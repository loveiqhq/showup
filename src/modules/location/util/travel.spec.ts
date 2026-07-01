import {
  DEFAULT_TRAVEL_BUFFER,
  FetchLike,
  GoogleRoutesTravelEstimator,
  MIN_MEETING_MINUTES,
  MeetingParticipant,
  RoughTravelEstimator,
  createTravelEstimator,
  earliestArrival,
  earliestFeasibleMeeting,
  estimateTravelMinutes,
  haversineKm,
} from './travel';

const at = (iso: string) => new Date(iso);

describe('estimateTravelMinutes', () => {
  it('uses the default rate (2 km ≈ 30 min → 15 min/km)', () => {
    expect(estimateTravelMinutes(2)).toBe(30);
  });

  it('returns 0 for zero distance', () => {
    expect(estimateTravelMinutes(0)).toBe(0);
  });

  it('rounds partial minutes up (never under-estimates travel)', () => {
    // 1.1 km * 15 min/km = 16.5 min → 17
    expect(estimateTravelMinutes(1.1)).toBe(17);
  });

  it('honours a custom, tunable rate', () => {
    expect(estimateTravelMinutes(10, { minutesPerKm: 2 })).toBe(20);
  });

  it('rejects a negative distance', () => {
    expect(() => estimateTravelMinutes(-1)).toThrow();
  });

  it('exposes a default buffer rate that is configurable', () => {
    expect(DEFAULT_TRAVEL_BUFFER.minutesPerKm).toBeGreaterThan(0);
  });
});

describe('earliestArrival', () => {
  const now = at('2026-07-01T13:00:00Z');

  it('is free-from + preparation + travel when free now', () => {
    const p: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:00:00Z'),
      availabilityEnd: at('2026-07-01T16:00:00Z'),
      preparationMinutes: 15,
      travelMinutes: 20,
    };
    // 13:00 + 15 + 20 = 13:35
    expect(earliestArrival(p, now).toISOString()).toBe(
      '2026-07-01T13:35:00.000Z',
    );
  });

  it('never starts the clock before "now" even if availability began earlier', () => {
    const p: MeetingParticipant = {
      availabilityStart: at('2026-07-01T12:00:00Z'),
      availabilityEnd: at('2026-07-01T16:00:00Z'),
      preparationMinutes: 15,
      travelMinutes: 15,
    };
    // now (13:00) + 30 = 13:30, not 12:00 + 30
    expect(earliestArrival(p, now).toISOString()).toBe(
      '2026-07-01T13:30:00.000Z',
    );
  });
});

describe('earliestFeasibleMeeting', () => {
  it('returns null when prep + travel eat the whole window (the 1:30–2:00 case)', () => {
    // Both check in at 13:30, free until 14:00. Prep 15 + travel 20 = 35 min.
    // Earliest either could arrive is 14:05 — past the 14:00 window end.
    const now = at('2026-07-01T13:30:00Z');
    const p: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:30:00Z'),
      availabilityEnd: at('2026-07-01T14:00:00Z'),
      preparationMinutes: 15,
      travelMinutes: 20,
    };
    expect(earliestFeasibleMeeting(p, p, now)).toBeNull();
  });

  it('returns the earliest time both can be present with room to meet', () => {
    const now = at('2026-07-01T13:00:00Z');
    const a: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:00:00Z'),
      availabilityEnd: at('2026-07-01T16:00:00Z'),
      preparationMinutes: 15,
      travelMinutes: 15,
    };
    // Earliest arrival 13:30; window has hours left → feasible at 13:30.
    expect(earliestFeasibleMeeting(a, a, now)?.toISOString()).toBe(
      '2026-07-01T13:30:00.000Z',
    );
  });

  it('uses the later of the two participants (both must be able to arrive)', () => {
    const now = at('2026-07-01T13:00:00Z');
    const early: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:00:00Z'),
      availabilityEnd: at('2026-07-01T18:00:00Z'),
      preparationMinutes: 0,
      travelMinutes: 0,
    };
    const late: MeetingParticipant = {
      availabilityStart: at('2026-07-01T15:00:00Z'),
      availabilityEnd: at('2026-07-01T18:00:00Z'),
      preparationMinutes: 0,
      travelMinutes: 0,
    };
    expect(earliestFeasibleMeeting(early, late, now)?.toISOString()).toBe(
      '2026-07-01T15:00:00.000Z',
    );
  });

  it('enforces a mandatory 30-minute minimum meeting', () => {
    expect(MIN_MEETING_MINUTES).toBe(30);
  });

  it('is not feasible when only 20 minutes remain (below the 30-min minimum)', () => {
    const now = at('2026-07-01T13:00:00Z');
    const p: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:00:00Z'),
      availabilityEnd: at('2026-07-01T13:50:00Z'), // 50-min window
      preparationMinutes: 15,
      travelMinutes: 15, // arrival 13:30 → only 20 min left until 13:50
    };
    expect(earliestFeasibleMeeting(p, p, now)).toBeNull();
  });

  it('is feasible when there is exactly the minimum meeting time left', () => {
    const now = at('2026-07-01T13:00:00Z');
    const p: MeetingParticipant = {
      availabilityStart: at('2026-07-01T13:00:00Z'),
      // arrival 13:45, and exactly MIN_MEETING_MINUTES until 14:00
      availabilityEnd: new Date(
        at('2026-07-01T13:45:00Z').getTime() + MIN_MEETING_MINUTES * 60_000,
      ),
      preparationMinutes: 15,
      travelMinutes: 30,
    };
    expect(earliestFeasibleMeeting(p, p, now)).not.toBeNull();
  });
});

describe('haversineKm', () => {
  it('is zero for identical points', () => {
    expect(
      haversineKm({ lat: 52.52, lng: 13.405 }, { lat: 52.52, lng: 13.405 }),
    ).toBe(0);
  });

  it('is about 111 km for one degree of longitude at the equator', () => {
    expect(haversineKm({ lat: 0, lng: 0 }, { lat: 0, lng: 1 })).toBeCloseTo(
      111.19,
      0,
    );
  });
});

describe('RoughTravelEstimator', () => {
  it('applies the distance buffer over the great-circle distance', async () => {
    const a = { lat: 0, lng: 0 };
    const b = { lat: 0, lng: 0.1 };
    const est = new RoughTravelEstimator();
    expect(await est.estimateMinutes(a, b)).toBe(
      estimateTravelMinutes(haversineKm(a, b)),
    );
  });
});

describe('GoogleRoutesTravelEstimator', () => {
  const from = { lat: 52.52, lng: 13.405 };
  const to = { lat: 52.5, lng: 13.45 };

  const fakeFetch =
    (body: unknown, ok = true, status = 200): FetchLike =>
    () =>
      Promise.resolve({
        ok,
        status,
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(''),
      });

  const route = (extra: Record<string, unknown> = {}) => [
    {
      originIndex: 0,
      destinationIndex: 0,
      condition: 'ROUTE_EXISTS',
      ...extra,
    },
  ];

  it('parses the route duration into whole minutes, rounded up', async () => {
    const est = new GoogleRoutesTravelEstimator({
      apiKey: 'k',
      fetchFn: fakeFetch(route({ duration: '601s' })),
    });
    expect(await est.estimateMinutes(from, to)).toBe(11); // 601s → 11 min
  });

  it('sends the API key and coordinates in the request', async () => {
    let captured:
      | { url: string; headers: Record<string, string>; body: string }
      | undefined;
    const fetchFn: FetchLike = (url, init) => {
      captured = { url, headers: init.headers, body: init.body };
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(route({ duration: '60s' })),
        text: () => Promise.resolve(''),
      });
    };
    const est = new GoogleRoutesTravelEstimator({
      apiKey: 'secret-key',
      fetchFn,
    });
    await est.estimateMinutes(from, to);
    expect(captured?.headers['X-Goog-Api-Key']).toBe('secret-key');
    expect(captured?.url).toContain('computeRouteMatrix');
    expect(captured?.body).toContain('13.405'); // origin longitude present
  });

  it('throws when the API responds with an error status', async () => {
    const est = new GoogleRoutesTravelEstimator({
      apiKey: 'k',
      fetchFn: fakeFetch([], false, 429),
    });
    await expect(est.estimateMinutes(from, to)).rejects.toThrow();
  });

  it('throws when no route exists', async () => {
    const est = new GoogleRoutesTravelEstimator({
      apiKey: 'k',
      fetchFn: fakeFetch(route({ condition: 'ROUTE_NOT_FOUND' })),
    });
    await expect(est.estimateMinutes(from, to)).rejects.toThrow();
  });

  it('requires an API key', () => {
    expect(() => new GoogleRoutesTravelEstimator({ apiKey: '' })).toThrow();
  });
});

describe('createTravelEstimator', () => {
  it('returns the Google estimator when an API key is provided', () => {
    expect(createTravelEstimator({ apiKey: 'k' })).toBeInstanceOf(
      GoogleRoutesTravelEstimator,
    );
  });

  it('falls back to the rough estimator when no key is set', () => {
    expect(createTravelEstimator({})).toBeInstanceOf(RoughTravelEstimator);
  });
});
