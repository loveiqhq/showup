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
        alcohol: 'socially',
        smoking: 'no',
        interests: ['gym', 'wine'],
        partnership: ['single'],
        family: ['no_children'],
        familyAhead: ['want_kids'],
        partnershipAhead: ['find_out_together'],
      }),
      120,
    );
    // Life and Future are matching-only and must never reach another user (Profiles 22, 23). The
    // interests are public on the screen, but showing them is the profile view's ticket, not this
    // card's: until that ticket decides how, the card carries none of them.
    for (const key of [
      'orientation',
      'religion',
      'politics',
      'heightCm',
      'education',
      'datingLanguages',
      'activity',
      'diet',
      'alcohol',
      'smoking',
      'rhythm',
      'social',
      'interests',
      'partnership',
      'family',
      'living',
      'familyAhead',
      'partnershipAhead',
      'livingAhead',
    ]) {
      expect(card).not.toHaveProperty(key);
    }
  });
});
