import {
  ChangeKind,
  ChangeStatus,
} from '../entities/date-change-suggestion.entity';
import {
  agreeRefusal,
  changeStateOf,
  clockLabel,
  isBindingVenue,
  pickChangeRequest,
  replacesOf,
  suggestRefusal,
  toPendingDto,
  toSuggestionDto,
  type ChangeRow,
} from './change-suggestions';

const at = (iso: string) => new Date(iso);
const ME = 'me';
const THEM = 'them';
const BINDING = at('2026-11-17T19:00:00Z');

let n = 0;
const row = (over: Partial<ChangeRow> = {}): ChangeRow => ({
  id: `s${++n}`,
  senderId: THEM,
  kind: ChangeKind.Time,
  valueTime: at('2026-11-17T19:30:00Z'),
  placeId: null,
  placeName: null,
  placeType: null,
  placeAddress: null,
  wasTime: BINDING,
  wasPlaceName: 'Cafe Krone',
  status: ChangeStatus.Pending,
  deadline: at('2026-11-17T18:30:00Z'),
  createdAt: at('2026-11-17T17:00:00Z'),
  answeredAt: null,
  expiredAt: null,
  expiredBindingTime: null,
  expiredBindingPlaceName: null,
  ...over,
});
const place = (over: Partial<ChangeRow> = {}) =>
  row({
    kind: ChangeKind.Place,
    valueTime: null,
    placeId: 'fixture:bar-lenz',
    placeName: 'Bar Lenz',
    placeType: 'bar',
    placeAddress: 'Lausitzer Platz 9',
    ...over,
  });

describe('suggestRefusal', () => {
  const base = {
    status: 'confirmed',
    bindingAt: BINDING,
    now: at('2026-11-17T17:00:00Z'),
    anyCommittedCheckIn: false,
  };
  it('lets a suggestion through on a confirmed date before the cutoff', () => {
    expect(suggestRefusal(base)).toBeNull();
  });
  it('refuses an ended date, the cutoff and a check-in', () => {
    expect(suggestRefusal({ ...base, status: 'cancelled' })).toBe('ended');
    expect(suggestRefusal({ ...base, now: at('2026-11-17T18:30:00Z') })).toBe(
      'cutoff',
    );
    expect(suggestRefusal({ ...base, anyCommittedCheckIn: true })).toBe(
      'checked_in',
    );
  });
});

describe('agreeRefusal', () => {
  const base = {
    status: ChangeStatus.Pending,
    senderId: THEM,
    viewerId: ME,
    deadline: at('2026-11-17T18:30:00Z'),
    now: at('2026-11-17T17:00:00Z'),
  };
  it('lets the receiver agree to a pending one', () => {
    expect(agreeRefusal(base)).toBeNull();
  });
  it('refuses the sender, a replaced or expired one and a late one', () => {
    expect(agreeRefusal({ ...base, senderId: ME })).toBe('sender');
    expect(
      agreeRefusal({ ...base, senderId: ME, status: ChangeStatus.Agreed }),
    ).toBe('sender');
    expect(agreeRefusal({ ...base, status: ChangeStatus.Replaced })).toBe(
      'not_pending',
    );
    expect(agreeRefusal({ ...base, status: ChangeStatus.Expired })).toBe(
      'not_pending',
    );
    expect(agreeRefusal({ ...base, now: at('2026-11-17T18:30:00Z') })).toBe(
      'expired',
    );
  });
  it('knows the receiver agreeing again after it was agreed', () => {
    expect(agreeRefusal({ ...base, status: ChangeStatus.Agreed })).toBe(
      'already_agreed',
    );
  });
});

describe('replacesOf', () => {
  it('names whose open suggestion was replaced', () => {
    expect(replacesOf(null, ME)).toBe('none');
    expect(replacesOf(ME, ME)).toBe('own');
    expect(replacesOf(THEM, ME)).toBe('theirs');
  });
});

describe('isBindingVenue', () => {
  const venue = {
    placeId: 'g1',
    name: 'Cafe Krone',
    location: { lat: 52.5016, lng: 13.4186 },
  };
  const candidate = {
    placeId: 'g2',
    name: 'Cafe Krone',
    location: { lat: 52.5016, lng: 13.4186 },
  };
  it('matches by place id when the venue has one', () => {
    expect(isBindingVenue(venue, { ...candidate, placeId: 'g1' })).toBe(true);
    // Same name and spot but a different id: two places.
    expect(isBindingVenue(venue, candidate)).toBe(false);
  });
  it('without an id matches the same name within 50 m', () => {
    const bare = { ...venue, placeId: null };
    expect(isBindingVenue(bare, candidate)).toBe(true);
    expect(
      isBindingVenue(bare, {
        ...candidate,
        location: { lat: 52.5019, lng: 13.4186 },
      }),
    ).toBe(true);
    expect(
      isBindingVenue(bare, {
        ...candidate,
        location: { lat: 52.503, lng: 13.4186 },
      }),
    ).toBe(false);
    expect(isBindingVenue(bare, { ...candidate, name: 'Bar Lenz' })).toBe(
      false,
    );
  });
  it('is never the binding place when there is none', () => {
    expect(isBindingVenue(null, candidate)).toBe(false);
  });
});

describe('clockLabel', () => {
  it('is local HH:mm in the zone', () => {
    expect(clockLabel(at('2026-06-15T16:30:00Z'), 'Europe/Berlin')).toBe(
      '18:30',
    );
    expect(clockLabel(at('2026-01-15T05:05:00Z'), 'Europe/Berlin')).toBe(
      '06:05',
    );
    expect(clockLabel(at('2026-01-15T00:00:00Z'), 'UTC')).toBe('00:00');
  });
});

describe('toPendingDto / toSuggestionDto', () => {
  it('a time suggestion carries the time, no place, and the was values', () => {
    expect(toPendingDto(row({ id: 'a' }), ME)).toEqual({
      id: 'a',
      kind: 'time',
      byYou: false,
      time: '2026-11-17T19:30:00.000Z',
      wasTime: '2026-11-17T19:00:00.000Z',
      wasPlace: { name: 'Cafe Krone' },
      deadline: '2026-11-17T18:30:00.000Z',
      createdAt: '2026-11-17T17:00:00.000Z',
    });
  });

  it('a place suggestion carries the place snapshot and no time; byYou follows the sender', () => {
    const dto = toPendingDto(place({ id: 'b', senderId: ME }), ME);
    expect(dto.byYou).toBe(true);
    expect(dto.time).toBeUndefined();
    expect(dto.place).toEqual({
      placeId: 'fixture:bar-lenz',
      name: 'Bar Lenz',
      type: 'bar',
      address: 'Lausitzer Platz 9',
    });
  });

  it('wasPlace is null when no place name was stored', () => {
    expect(toPendingDto(row({ wasPlaceName: null }), ME).wasPlace).toBeNull();
  });

  it('the thread item adds the status and the history of an agreed one', () => {
    const dto = toSuggestionDto(
      row({
        status: ChangeStatus.Agreed,
        answeredAt: at('2026-11-17T17:10:00Z'),
      }),
      ME,
    );
    expect(dto.status).toBe('agreed');
    expect(dto.answeredAt).toBe('2026-11-17T17:10:00.000Z');
    expect(dto.expiredAt).toBeUndefined();
    expect(dto.expiredBindingTime).toBeUndefined();
    expect(dto.expiredBindingPlace).toBeUndefined();
  });

  it('an expired one carries the binding that stayed', () => {
    const dto = toSuggestionDto(
      place({
        status: ChangeStatus.Expired,
        expiredAt: at('2026-11-17T18:30:00Z'),
        expiredBindingTime: BINDING,
        expiredBindingPlaceName: 'Cafe Krone',
      }),
      ME,
    );
    expect(dto.expiredAt).toBe('2026-11-17T18:30:00.000Z');
    expect(dto.expiredBindingTime).toBe('2026-11-17T19:00:00.000Z');
    expect(dto.expiredBindingPlace).toEqual({ name: 'Cafe Krone' });
  });
});

describe('pickChangeRequest', () => {
  it('is null with nothing pending from the match', () => {
    expect(pickChangeRequest([], ME)).toBeNull();
    expect(pickChangeRequest([row({ senderId: ME })], ME)).toBeNull();
    expect(
      pickChangeRequest([row({ status: ChangeStatus.Agreed })], ME),
    ).toBeNull();
  });

  it('takes the earliest deadline', () => {
    const early = place({ deadline: at('2026-11-17T18:00:00Z') });
    const late = row({ deadline: at('2026-11-17T18:30:00Z') });
    expect(pickChangeRequest([late, early], ME)?.id).toBe(early.id);
  });

  it('puts time first on a tie', () => {
    const p = place();
    const t = row();
    expect(pickChangeRequest([p, t], ME)?.id).toBe(t.id);
    expect(pickChangeRequest([t, p], ME)?.id).toBe(t.id);
  });

  it('ignores my own suggestions even when earlier', () => {
    const mine = row({ senderId: ME, deadline: at('2026-11-17T10:00:00Z') });
    const theirs = place();
    expect(pickChangeRequest([mine, theirs], ME)?.id).toBe(theirs.id);
  });
});

describe('changeStateOf', () => {
  const base = {
    viewerId: ME,
    bindingAt: BINDING,
    slots: [at('2026-11-17T19:15:00Z'), at('2026-11-17T19:30:00Z')],
    anyCommittedCheckIn: false,
    placeWas: null,
  };

  it('carries the cutoff, allowed, the slots and the pending list', () => {
    const pending = row({ senderId: ME });
    const state = changeStateOf({
      ...base,
      rows: [pending, row({ status: ChangeStatus.Replaced })],
    });
    expect(state.cutoffAt).toBe('2026-11-17T18:30:00.000Z');
    expect(state.allowed).toBe(true);
    expect(state.slots).toEqual([
      '2026-11-17T19:15:00.000Z',
      '2026-11-17T19:30:00.000Z',
    ]);
    expect(state.pending.map((p) => p.id)).toEqual([pending.id]);
    expect(state.timeWas).toBeUndefined();
    expect(state.placeWas).toBeUndefined();
  });

  it('allowed is false once anyone has a committed check-in', () => {
    expect(
      changeStateOf({ ...base, rows: [], anyCommittedCheckIn: true }).allowed,
    ).toBe(false);
  });

  it('timeWas and placeWas come from the FIRST agreed suggestion of that kind', () => {
    const first = row({
      status: ChangeStatus.Agreed,
      answeredAt: at('2026-11-17T16:00:00Z'),
      wasTime: at('2026-11-17T18:00:00Z'),
    });
    const second = row({
      status: ChangeStatus.Agreed,
      answeredAt: at('2026-11-17T16:30:00Z'),
      wasTime: at('2026-11-17T18:30:00Z'),
    });
    const agreedPlace = place({
      status: ChangeStatus.Agreed,
      answeredAt: at('2026-11-17T16:10:00Z'),
    });
    const state = changeStateOf({
      ...base,
      rows: [second, agreedPlace, first],
      placeWas: { name: 'Cafe Krone', address: 'Oranienstrasse 42' },
    });
    expect(state.timeWas).toBe('2026-11-17T18:00:00.000Z');
    expect(state.placeWas).toEqual({
      name: 'Cafe Krone',
      address: 'Oranienstrasse 42',
    });
  });

  it('no placeWas without an agreed place, even when one is offered', () => {
    expect(
      changeStateOf({
        ...base,
        rows: [],
        placeWas: { name: 'Cafe Krone' },
      }).placeWas,
    ).toBeUndefined();
  });
});
