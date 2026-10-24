import { Profile } from '../../profiles/entities/profile.entity';
import { ageFromDateOfBirth } from '../../profiles/util/age';
import { isFieldHidden } from '../../profiles/util/hidden-fields';
import {
  SearchEssentialsDto,
  SearchHowILiveDto,
  SearchMediaDto,
  SearchProfileDto,
  SearchPromptDto,
} from '../dto/search.dto';
import { roundedDistanceKm } from './search';

/** Everything one Search profile is built from, already loaded, filtered and ordered. */
export interface SearchProfileSource {
  profile: Profile;
  /** Visible photos (never a rejected one), in profile order, as loadable URLs. */
  photoUrls: string[];
  prompts: SearchPromptDto[];
  voice: SearchMediaDto | null;
  video: SearchMediaDto | null;
  /** Their active Check-In window. */
  checkIn: {
    motivation: string | null;
    vibes: string[] | null;
    uplift: string | null;
    district: string | null;
  };
  distanceMeters: number;
  meetAt: Date;
  meetDay: 'today' | 'tomorrow';
  attendedCount: number;
}

/**
 * The Search 01 payload for one profile.
 *
 * HIDDEN MEANS NOT SENT. A hidden age or detail is left off the object -- not nulled for the app to
 * suppress -- so it is not on the wire at all ("A hidden age or a hidden detail is left out of the
 * payload by the server, not hidden by the app"). Matching still reads the stored value; this is
 * display only.
 *
 * UNANSWERED MEANS NOT SENT TOO, which is what lets the app render "every section is optional"
 * without a second rule: no key, no row, no card.
 */
export function buildSearchProfile(src: SearchProfileSource): SearchProfileDto {
  const p = src.profile;
  const hidden = p.hiddenFields;
  const show = <T>(
    field: Parameters<typeof isFieldHidden>[1],
    value: T | null | undefined,
  ): T | undefined =>
    value == null || isFieldHidden(hidden, field) ? undefined : value;

  const essentials: SearchEssentialsDto = omitUndefined({
    heightCm: show('height', p.heightCm),
    gender: show('gender', p.gender),
    orientation: show('orientation', p.orientation),
    datingLanguages: show(
      'dating_language',
      p.datingLanguages && p.datingLanguages.length > 0
        ? [...p.datingLanguages]
        : null,
    ),
    education: show('education', p.education),
    religion: show('religion', p.religion),
    politics: show('politics', p.politics),
  });

  // No visibility control exists for these yet (Profile 21's habits switch is unbuilt), so an
  // answer is shown.
  const howILive: SearchHowILiveDto = omitUndefined({
    activity: p.activity ?? undefined,
    diet: p.diet ?? undefined,
    alcohol: p.alcohol ?? undefined,
    smoking: p.smoking ?? undefined,
    rhythm: p.rhythm ?? undefined,
    social: p.social ?? undefined,
  });

  const age = show('age', ageFromDateOfBirth(p.dateOfBirth));
  const uplift = src.checkIn.uplift?.trim() || undefined;
  const district = src.checkIn.district?.trim() || undefined;

  return omitUndefined({
    userId: p.userId,
    name: firstName(p.displayName),
    age,
    distanceKm: roundedDistanceKm(src.distanceMeters),
    district,
    meetAt: src.meetAt.toISOString(),
    meetDay: src.meetDay,
    attendedCount: src.attendedCount,
    motivation: src.checkIn.motivation ?? undefined,
    vibes: [...(src.checkIn.vibes ?? [])],
    uplift,
    photos: src.photoUrls.map((url) => ({ url })),
    prompts: src.prompts,
    voice: src.voice ?? undefined,
    video: src.video ?? undefined,
    essentials,
    howILive,
    interests: [...(p.interests ?? [])],
  });
}

/**
 * `{name}` is the profile's first name (Search 01 copy) -- which is what the name step stores:
 * "first name only, no surname anywhere in the flow". Sent as typed, trimmed: cutting at a space
 * would turn "Mary Ann" into "Mary".
 */
export function firstName(displayName: string | null): string {
  return (displayName ?? '').trim();
}

/** Drops undefined keys, so an absent value is absent from the JSON rather than `null`. */
function omitUndefined<T extends object>(value: T): T {
  return Object.fromEntries(
    Object.entries(value).filter(([, v]) => v !== undefined),
  ) as T;
}
