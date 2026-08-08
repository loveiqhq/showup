import {
  CheckInStatus,
  MAX_PREPARATION_MINUTES,
  MIN_PREPARATION_MINUTES,
  availabilityWindowError,
  isActiveCheckIn,
  locationError,
  overlapsAnyWindow,
  preparationTimeError,
} from './check-in';

const at = (iso: string) => new Date(iso);

describe('availabilityWindowError', () => {
  const now = at('2026-07-01T12:00:00Z');

  it('accepts a valid window (now to +2h)', () => {
    expect(
      availabilityWindowError(
        at('2026-07-01T12:00:00Z'),
        at('2026-07-01T14:00:00Z'),
        now,
      ),
    ).toBeNull();
  });

  it('rejects an end that is before the start', () => {
    expect(
      availabilityWindowError(
        at('2026-07-01T14:00:00Z'),
        at('2026-07-01T13:00:00Z'),
        now,
      ),
    ).not.toBeNull();
  });

  it('rejects a window that has already ended', () => {
    expect(
      availabilityWindowError(
        at('2026-07-01T09:00:00Z'),
        at('2026-07-01T11:00:00Z'),
        now,
      ),
    ).not.toBeNull();
  });

  it('rejects a window shorter than 15 minutes', () => {
    expect(
      availabilityWindowError(
        at('2026-07-01T12:00:00Z'),
        at('2026-07-01T12:05:00Z'),
        now,
      ),
    ).not.toBeNull();
  });

  it('rejects a window longer than 48 hours', () => {
    expect(
      availabilityWindowError(
        at('2026-07-01T12:00:00Z'),
        at('2026-07-03T13:00:00Z'),
        now,
      ),
    ).not.toBeNull();
  });
});

describe('isActiveCheckIn', () => {
  const now = at('2026-07-01T12:00:00Z');
  const base = {
    status: CheckInStatus.Available,
    availabilityStart: at('2026-07-01T11:00:00Z'),
    availabilityEnd: at('2026-07-01T14:00:00Z'),
  };

  it('is active when available and now is inside the window', () => {
    expect(isActiveCheckIn(base, now)).toBe(true);
  });

  it('is not active once the window has ended', () => {
    expect(isActiveCheckIn(base, at('2026-07-01T15:00:00Z'))).toBe(false);
  });

  it('is not active before the window starts', () => {
    expect(isActiveCheckIn(base, at('2026-07-01T10:00:00Z'))).toBe(false);
  });

  it('is not active when cancelled', () => {
    expect(
      isActiveCheckIn({ ...base, status: CheckInStatus.Cancelled }, now),
    ).toBe(false);
  });

  it('is not active when already expired', () => {
    expect(
      isActiveCheckIn({ ...base, status: CheckInStatus.Expired }, now),
    ).toBe(false);
  });
});

describe('preparationTimeError', () => {
  it('accepts the minimum preparation time', () => {
    expect(preparationTimeError(MIN_PREPARATION_MINUTES)).toBeNull();
  });

  it('accepts the maximum preparation time', () => {
    expect(preparationTimeError(MAX_PREPARATION_MINUTES)).toBeNull();
  });

  it('accepts a typical value in range', () => {
    expect(preparationTimeError(30)).toBeNull();
  });

  it('rejects a value below the minimum', () => {
    expect(preparationTimeError(MIN_PREPARATION_MINUTES - 1)).not.toBeNull();
  });

  it('rejects a value above the maximum', () => {
    expect(preparationTimeError(MAX_PREPARATION_MINUTES + 1)).not.toBeNull();
  });

  it('rejects a non-whole number of minutes', () => {
    expect(preparationTimeError(20.5)).not.toBeNull();
  });
});

describe('overlapsAnyWindow', () => {
  const w = (s: string, e: string) => ({
    availabilityStart: at(s),
    availabilityEnd: at(e),
  });
  const existing = [
    w('2026-07-01T11:00:00Z', '2026-07-01T13:00:00Z'),
    w('2026-07-01T18:00:00Z', '2026-07-01T20:00:00Z'),
  ];

  it('allows a window that sits between two existing windows', () => {
    expect(
      overlapsAnyWindow(
        at('2026-07-01T14:00:00Z'),
        at('2026-07-01T15:00:00Z'),
        existing,
      ),
    ).toBe(false);
  });

  it('detects a window that overlaps an existing one', () => {
    expect(
      overlapsAnyWindow(
        at('2026-07-01T12:00:00Z'),
        at('2026-07-01T12:30:00Z'),
        existing,
      ),
    ).toBe(true);
  });

  it('treats back-to-back windows (touching at the edge) as non-overlapping', () => {
    expect(
      overlapsAnyWindow(
        at('2026-07-01T13:00:00Z'),
        at('2026-07-01T14:00:00Z'),
        existing,
      ),
    ).toBe(false);
  });

  it('detects a new window that fully contains an existing one', () => {
    expect(
      overlapsAnyWindow(
        at('2026-07-01T10:00:00Z'),
        at('2026-07-01T21:00:00Z'),
        existing,
      ),
    ).toBe(true);
  });

  it('allows any window when the user has none yet', () => {
    expect(
      overlapsAnyWindow(
        at('2026-07-01T13:00:00Z'),
        at('2026-07-01T14:00:00Z'),
        [],
      ),
    ).toBe(false);
  });
});

describe('locationError', () => {
  it('accepts a latitude/longitude pair', () => {
    expect(locationError(52.52, 13.405)).toBeNull();
  });

  it('rejects a missing latitude', () => {
    expect(locationError(undefined, 13.405)).not.toBeNull();
  });

  it('rejects a missing longitude', () => {
    expect(locationError(52.52, undefined)).not.toBeNull();
  });

  it('rejects both coordinates missing', () => {
    expect(locationError(null, null)).not.toBeNull();
  });
});
