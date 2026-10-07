import {
  Profile,
  ProfileVerificationStatus,
} from '../../profiles/entities/profile.entity';
import { DiscoveryProfileDto } from './matching.dto';

const profile = (overrides: Partial<Profile>): Profile =>
  ({
    userId: 'u1',
    displayName: 'Ana',
    gender: 'woman',
    lookingFor: null,
    verificationStatus: ProfileVerificationStatus.None,
    hiddenFields: [],
    ...overrides,
  }) as Profile;

describe('DiscoveryProfileDto -- the card another person sees', () => {
  it('shows a gender the person has not hidden', () => {
    expect(DiscoveryProfileDto.from(profile({}), 120).gender).toBe('woman');
  });

  it('sends no gender at all once the person hides it on their profile', () => {
    const card = DiscoveryProfileDto.from(
      profile({ hiddenFields: ['gender'] }),
      120,
    );
    expect(card.gender).toBeNull();
  });

  it('hiding a different field leaves gender alone', () => {
    const card = DiscoveryProfileDto.from(
      profile({ hiddenFields: ['age', 'religion'] }),
      120,
    );
    expect(card.gender).toBe('woman');
  });

  it('carries none of the other detail answers, hidden or not', () => {
    const card = DiscoveryProfileDto.from(
      profile({
        orientation: 'bisexual',
        religion: 'catholic',
        politics: 'middle',
        heightCm: 171,
      }),
      120,
    );
    for (const key of [
      'orientation',
      'religion',
      'politics',
      'heightCm',
      'education',
      'datingLanguages',
    ]) {
      expect(card).not.toHaveProperty(key);
    }
  });
});
