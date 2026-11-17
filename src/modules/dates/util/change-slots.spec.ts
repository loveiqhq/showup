import { changeSlots, localDay } from './change-slots';

const TZ = 'Europe/Berlin';
// 2026-11-17 is CET (UTC+1): 19:00 local = 18:00Z.
const local = (hhmm: string) => new Date(`2026-11-17T${hhmm}:00+01:00`);
const hm = (d: Date) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: TZ,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(d);

describe('changeSlots', () => {
  it('the kit case: binding 19:00, free until 22:00 gives 19:15 to 21:30', () => {
    const slots = changeSlots(
      local('19:00'),
      [{ start: local('17:00'), end: local('22:00') }],
      TZ,
    );
    expect(hm(slots[0])).toBe('19:15');
    expect(hm(slots[slots.length - 1])).toBe('21:30');
    expect(slots).toHaveLength(10);
  });

  it('offers nothing past local midnight', () => {
    const slots = changeSlots(
      local('22:00'),
      [{ start: local('20:00'), end: new Date('2026-11-18T03:00:00+01:00') }],
      TZ,
    );
    expect(hm(slots[slots.length - 1])).toBe('23:45');
    expect(slots.every((s) => s < new Date('2026-11-18T00:00:00+01:00'))).toBe(
      true,
    );
  });

  it('offers nothing outside the runs, and unions separate runs ascending without duplicates', () => {
    const slots = changeSlots(
      local('18:00'),
      [
        { start: local('20:00'), end: local('21:00') },
        { start: local('18:30'), end: local('19:15') },
        { start: local('18:45'), end: local('19:30') },
      ],
      TZ,
    );
    expect(slots.map(hm)).toEqual([
      '18:30',
      '18:45',
      '19:00',
      '20:00',
      '20:15',
      '20:30',
    ]);
  });

  it('is empty when the binding is already at the last slot', () => {
    expect(
      changeSlots(
        local('21:30'),
        [{ start: local('17:00'), end: local('22:00') }],
        TZ,
      ),
    ).toEqual([]);
  });

  it('starts strictly after the binding time and keeps 15-minute alignment from the run start', () => {
    const slots = changeSlots(
      local('19:00'),
      [{ start: local('18:50'), end: local('20:00') }],
      TZ,
    );
    // Starts are on the quarter hour, not offset by the run's 18:50.
    expect(slots.map(hm)).toEqual(['19:15', '19:30']);
  });

  it('finds local midnight across the clock change', () => {
    // 2026-10-25 is the night the clocks go back in Berlin (25 h day).
    const binding = new Date('2026-10-25T20:00:00+02:00');
    const slots = changeSlots(
      binding,
      [{ start: binding, end: new Date('2026-10-26T05:00:00+01:00') }],
      TZ,
    );
    expect(hm(slots[slots.length - 1])).toBe('23:45');
    expect(slots.every((s) => s < new Date('2026-10-26T00:00:00+01:00'))).toBe(
      true,
    );
  });
});

describe('changeSlots, touching runs', () => {
  it('treats back-to-back runs as one stretch of free time', () => {
    const slots = changeSlots(
      local('19:00'),
      [
        { start: local('17:00'), end: local('20:00') },
        { start: local('20:00'), end: local('22:00') },
      ],
      TZ,
    );
    expect(slots).toHaveLength(10);
    expect(hm(slots[2])).toBe('19:45');
    expect(hm(slots[slots.length - 1])).toBe('21:30');
  });
});

describe('localDay', () => {
  it('is local midnight to local midnight', () => {
    const { start, end } = localDay(local('19:00'), TZ);
    expect(start).toEqual(new Date('2026-11-17T00:00:00+01:00'));
    expect(end).toEqual(new Date('2026-11-18T00:00:00+01:00'));
  });

  it('is 25 hours on the night the clocks go back', () => {
    const { start, end } = localDay(new Date('2026-10-25T20:00:00+02:00'), TZ);
    expect(start).toEqual(new Date('2026-10-25T00:00:00+02:00'));
    expect(end).toEqual(new Date('2026-10-26T00:00:00+01:00'));
  });
});
