import { Profile } from '../../profiles/entities/profile.entity';
import {
  buildSearchProfile,
  firstName,
  SearchProfileSource,
} from './search-profile';

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    userId: 'u-leonie',
    displayName: 'Leonie',
    dateOfBirth: '1998-03-01',
    gender: 'woman',
    heightCm: 172,
    orientation: 'straight',
    datingLanguages: ['german', 'english'],
    education: 'university',
    religion: 'spiritual',
    politics: 'mid_left',
    activity: 'active',
    diet: 'flexitarian',
    alcohol: 'socially',
    smoking: 'no',
    rhythm: 'early_bird',
    social: 'balanced',
    interests: ['hiking', 'reading'],
    hiddenFields: [],
    ...overrides,
  } as unknown as Profile;
}

function source(
  overrides: Partial<SearchProfileSource> = {},
): SearchProfileSource {
  return {
    profile: profile(),
    photoUrls: ['/uploads/a.jpg', '/uploads/b.jpg'],
    prompts: [{ topicId: 'first_date', answer: 'Talk about anything real.' }],
    voice: null,
    video: null,
    checkIn: {
      motivation: 'long_term',
      vibes: ['coffee_chat', 'sunset'],
      uplift: null,
      district: 'Neukölln',
    },
    distanceMeters: 1240,
    meetAt: new Date('2026-10-22T17:00:00Z'),
    meetDay: 'today',
    attendedCount: 4,
    ...overrides,
  };
}

const keysOf = (v: object) =>
  Object.keys(JSON.parse(JSON.stringify(v)) as object);

describe('buildSearchProfile', () => {
  it('sends the answered sections and the rounded distance, never metres', () => {
    const dto = buildSearchProfile(source());
    expect(dto.name).toBe('Leonie');
    expect(dto.distanceKm).toBe(1);
    expect(JSON.stringify(dto)).not.toContain('1240');
    expect(dto.district).toBe('Neukölln');
    expect(dto.meetAt).toBe('2026-10-22T17:00:00.000Z');
    expect(dto.meetDay).toBe('today');
    expect(dto.essentials).toEqual({
      heightCm: 172,
      gender: 'woman',
      orientation: 'straight',
      datingLanguages: ['german', 'english'],
      education: 'university',
      religion: 'spiritual',
      politics: 'mid_left',
    });
    expect(dto.photos).toEqual([
      { url: '/uploads/a.jpg' },
      { url: '/uploads/b.jpg' },
    ]);
  });

  it('leaves a hidden age off the wire entirely, not null', () => {
    const dto = buildSearchProfile(
      source({ profile: profile({ hiddenFields: ['age'] }) }),
    );
    expect(keysOf(dto)).not.toContain('age');
    expect(JSON.stringify(dto)).not.toMatch(/"age"/);
  });

  it('drops a hidden detail from Essentials, and only that one', () => {
    const dto = buildSearchProfile(
      source({
        profile: profile({
          hiddenFields: ['religion', 'height', 'dating_language'],
        }),
      }),
    );
    expect(keysOf(dto.essentials)).toEqual([
      'gender',
      'orientation',
      'education',
      'politics',
    ]);
    expect(JSON.stringify(dto)).not.toContain('spiritual');
  });

  it('sends no key for an unanswered section, so the app renders no card', () => {
    const dto = buildSearchProfile(
      source({
        profile: profile({
          heightCm: null,
          education: null,
          religion: null,
          politics: null,
          datingLanguages: null,
          activity: null,
          diet: null,
          alcohol: null,
          smoking: null,
          rhythm: null,
          social: null,
          interests: null,
        }),
        checkIn: {
          motivation: 'long_term',
          vibes: ['coffee_chat'],
          uplift: '  ',
          district: null,
        },
      }),
    );
    expect(keysOf(dto.essentials)).toEqual(['gender', 'orientation']);
    expect(dto.howILive).toEqual({});
    expect(dto.interests).toEqual([]);
    expect(keysOf(dto)).not.toContain('uplift');
    expect(keysOf(dto)).not.toContain('district');
    expect(keysOf(dto)).not.toContain('voice');
    expect(keysOf(dto)).not.toContain('video');
  });

  it('reads 900 m as under one km', () => {
    expect(buildSearchProfile(source({ distanceMeters: 900 })).distanceKm).toBe(
      0,
    );
  });
});

describe('firstName', () => {
  it('is the stored first name as typed, a double first name included', () => {
    expect(firstName('Mary Ann')).toBe('Mary Ann');
    expect(firstName('  Ana  ')).toBe('Ana');
    expect(firstName(null)).toBe('');
  });
});
