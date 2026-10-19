import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { EntityManager, In, Repository } from 'typeorm';

import { UpsertProfileDto } from './dto/upsert-profile.dto';
import { ProfilePhoto } from './entities/profile-photo.entity';
import { Profile, ProfileVerificationStatus } from './entities/profile.entity';
import { isAtLeast18 } from './util/age';
import { isProfileComplete } from './util/completion';
import { ensureCheckInWindow } from '../check-ins/util/check-in-window';
import {
  advanceFlowPosition,
  FLOW_POSITION_COMPLETE,
} from './util/flow-position';
import {
  canonicalDatingLanguages,
  canonicalInterests,
  canonicalLifeAnswer,
  isContradictoryLifeAnswer,
  LIFE_QUESTIONS,
} from './util/profile-details';
import type { LifeQuestionId } from './util/profile-details';
import {
  normaliseHiddenFields,
  unknownHiddenFields,
} from './util/hidden-fields';
import { VISIBLE_PHOTO_STATUSES } from './util/photo-visibility';

/** The six §4 answers: the DTO and entity property, and its registry `question_id`. */
type LifeAnswerField =
  | 'partnership'
  | 'family'
  | 'living'
  | 'familyAhead'
  | 'partnershipAhead'
  | 'livingAhead';
const LIFE_ANSWER_FIELDS: readonly (readonly [
  LifeAnswerField,
  LifeQuestionId,
])[] = [
  ['partnership', 'partnership'],
  ['family', 'family'],
  ['living', 'living'],
  ['familyAhead', 'family_ahead'],
  ['partnershipAhead', 'partnership_ahead'],
  ['livingAhead', 'living_ahead'],
];

/**
 * Stores a canonical answer. The cast is sound because `canonicalLifeAnswer` returns only values of
 * that question's own list, which is the element type of the column it is written to.
 */
function setLifeAnswer(
  profile: Profile,
  field: LifeAnswerField,
  values: string[],
): void {
  (profile as unknown as Record<LifeAnswerField, string[]>)[field] = values;
}

@Injectable()
export class ProfilesService {
  constructor(
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
  ) {}

  /** Returns the user's profile, creating an empty one on first access. */
  async getOrCreate(userId: string): Promise<Profile> {
    const existing = await this.profiles.findOne({ where: { userId } });
    if (existing) return existing;
    // ON CONFLICT DO NOTHING, then read back. The previous version checked, found nothing and
    // inserted, so two FIRST requests in flight at once -- the app opening profile creation reads
    // progress and the profile in parallel -- both inserted and one failed the one-profile-per-user
    // constraint as a 500. Found by the concurrent-save e2e test (SHOWUP-165). Now one INSERT wins,
    // the other is ignored, and both requests read back the same row.
    await this.profiles
      .createQueryBuilder()
      .insert()
      .into(Profile)
      .values({ userId, hiddenFields: [] })
      .orIgnore()
      .execute();
    return this.profiles.findOneOrFail({ where: { userId } });
  }

  /** Apply the provided fields to the user's profile (used by both POST and PATCH). */
  async update(userId: string, dto: UpsertProfileDto): Promise<Profile> {
    // Refusals first, before any row is created or locked: a request that is going to be turned
    // away should not wait in the queue for the lock just to be told so.
    if (dto.dateOfBirth !== undefined && !isAtLeast18(dto.dateOfBirth)) {
      throw new BadRequestException('You must be at least 18 years old');
    }
    const contradictory = LIFE_ANSWER_FIELDS.filter(([field, questionId]) => {
      const values = dto[field];
      return (
        values != null &&
        isContradictoryLifeAnswer(LIFE_QUESTIONS[questionId], values)
      );
    }).map(([, questionId]) => questionId);
    if (contradictory.length > 0) {
      throw new BadRequestException(
        `Contradictory answer(s): ${contradictory.join(', ')}`,
      );
    }
    if (dto.hiddenFields != null) {
      const unknown = unknownHiddenFields(dto.hiddenFields);
      if (unknown.length > 0) {
        throw new BadRequestException(
          `Unknown hidden field(s): ${unknown.join(', ')}`,
        );
      }
    }

    await this.getOrCreate(userId);

    // ONE TRANSACTION HOLDING THE ROW LOCK, from read to write.
    //
    // `save()` writes back every column that differs from what TypeORM last read -- not only the
    // ones this request touched. So two saves in flight for one person, each working from its own
    // read, let the later commit quietly put back the earlier one's old values: a `{religion}` and
    // a `{politics}` sent together lost one of them, and a slower report of an earlier step rewound
    // the flow position. Found by review on SHOWUP-165; a guarded UPDATE on the position alone did
    // not fix it, because the stale save had already written the old position back.
    //
    // `pessimistic_write` is SELECT ... FOR UPDATE: the second request waits at its read until the
    // first commits, then reads the first one's result. The wait is per profile, so it only ever
    // queues one person's own requests behind each other.
    //
    // It also keeps "persist the value, then advance the flow position" (every detail ticket) from
    // half-happening: the answer and the position commit together or not at all.
    return this.profiles.manager.transaction(async (em) => {
      const profile = await em.findOneOrFail(Profile, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });

      if (dto.dateOfBirth !== undefined) profile.dateOfBirth = dto.dateOfBirth;
      if (dto.displayName !== undefined) profile.displayName = dto.displayName;
      // THE DETAIL ANSWERS. `!= null` throughout, so an explicit null is "not supplied" rather
      // than "clear it": Skip saves nothing and "never deletes a value saved earlier" (flow README
      // rule 0), and gender's radio "never clears" (Profile 15). Nothing in the flow can erase an
      // answer.
      if (dto.gender != null) profile.gender = dto.gender;
      if (dto.heightCm != null) profile.heightCm = dto.heightCm;
      if (dto.orientation != null) profile.orientation = dto.orientation;
      if (dto.datingLanguages != null) {
        profile.datingLanguages = canonicalDatingLanguages(dto.datingLanguages);
      }
      if (dto.education != null) profile.education = dto.education;
      if (dto.religion != null) profile.religion = dto.religion;
      if (dto.politics != null) profile.politics = dto.politics;
      // Interests (SHOWUP-174). Same `!= null` rule: an unanswered "How I live" group is simply not
      // sent, and keeps whatever was saved before.
      if (dto.activity != null) profile.activity = dto.activity;
      if (dto.diet != null) profile.diet = dto.diet;
      if (dto.alcohol != null) profile.alcohol = dto.alcohol;
      if (dto.smoking != null) profile.smoking = dto.smoking;
      if (dto.rhythm != null) profile.rhythm = dto.rhythm;
      if (dto.social != null) profile.social = dto.social;
      if (dto.interests != null) {
        profile.interests = canonicalInterests(dto.interests);
      }
      // Life and Future (SHOWUP-175/176). `[]` is NOT null, so it is stored: "an unanswered
      // question is saved as empty". Only a field left out keeps its earlier value.
      for (const [field, questionId] of LIFE_ANSWER_FIELDS) {
        const values = dto[field];
        if (values != null) {
          setLifeAnswer(
            profile,
            field,
            canonicalLifeAnswer(LIFE_QUESTIONS[questionId], values),
          );
        }
      }
      if (dto.lookingFor !== undefined) profile.lookingFor = dto.lookingFor;
      // `!= null` for the same reason as hiddenFields below: @IsOptional() lets an explicit null
      // through, and is_visible is NOT NULL, so assigning it would fail at the database rather
      // than at validation. Null is never a meaningful value for this flag.
      if (dto.isVisible != null) profile.isVisible = dto.isVisible;

      // Presentation only. Note what this branch does NOT do: it never reads or writes isVisible.
      // Hiding a field must leave the user fully discoverable and matchable — a hidden age is
      // still passed to matching. Unknown values were refused above rather than stored: an
      // unrecognised field_id would otherwise sit in the set forever, hiding nothing.
      // `!= null`, not `!== undefined`: class-validator's @IsOptional() ignores null as well as
      // undefined, so an explicit `"hiddenFields": null` reaches this line. Treating it as
      // "not supplied" is the only safe reading — the alternative is normalising null and
      // throwing.
      if (dto.hiddenFields != null) {
        profile.hiddenFields = normaliseHiddenFields(dto.hiddenFields);
      }

      if (dto.flowPosition != null) {
        const before = profile.flowPosition;
        profile.flowPosition = advanceFlowPosition(
          profile.flowPosition,
          dto.flowPosition,
        );
        // PROFILE CREATION JUST COMPLETED: open the first Check-In window, in the same transaction
        // and under the same row lock -- "Server: open a Check-In window when the profile-creation-
        // complete flag is set" (Check-In 01), so `window_id` is never null on the intro.
        if (
          before !== FLOW_POSITION_COMPLETE &&
          profile.flowPosition === FLOW_POSITION_COMPLETE
        ) {
          await ensureCheckInWindow(em, userId, profile.flowPosition);
        }
      }

      profile.isComplete = await this.computeComplete(em, userId, profile);
      return em.save(profile);
    });
  }

  /** A profile is "complete" once it has a name, a date of birth, and at least MIN_PHOTOS photos. */
  private async computeComplete(
    em: EntityManager,
    userId: string,
    profile: Profile,
  ): Promise<boolean> {
    // ON THE CALLER'S CONNECTION, not `this.photos`. The callers hold the profile row lock inside
    // a transaction; counting through the repository would borrow a SECOND pooled connection while
    // holding the first. With every connection held by a request waiting on that same lock, the
    // one holding it could never get its second connection, and the pool would deadlock.
    // Rejected photos don't count toward completeness (they aren't shown to others).
    const photoCount = await em.count(ProfilePhoto, {
      where: { userId, moderationStatus: In(VISIBLE_PHOTO_STATUSES) },
    });
    return isProfileComplete({
      displayName: profile.displayName,
      dateOfBirth: profile.dateOfBirth,
      photoCount,
    });
  }

  /**
   * Recompute completion after photos change.
   *
   * Under the same row lock as `update`, and writing only `is_complete`. Without the lock, a photo
   * upload and a name save landing together could each compute from the other's old state and
   * leave a finished profile marked incomplete. Without the targeted write, this save would put
   * back every other column as it was when it read them -- the stale-save hazard `update`
   * describes.
   */
  async refreshCompletion(userId: string): Promise<void> {
    await this.profiles.manager.transaction(async (em) => {
      const profile = await em.findOne(Profile, {
        where: { userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!profile) return;
      const complete = await this.computeComplete(em, userId, profile);
      if (complete !== profile.isComplete) {
        await em.update(Profile, { userId }, { isComplete: complete });
      }
    });
  }

  async requestVerification(userId: string): Promise<Profile> {
    await this.getOrCreate(userId);
    // One conditional UPDATE, touching only the two verification columns. A whole-entity save here
    // would write back every column as this request last read it, undoing a detail answer saved in
    // between; and the "already verified" check is in the WHERE so an admin's approval landing at
    // the same moment cannot be overwritten back to pending.
    const result = await this.profiles
      .createQueryBuilder()
      .update(Profile)
      .set({
        verificationStatus: ProfileVerificationStatus.Pending,
        verificationRequestedAt: new Date(),
      })
      .where({ userId })
      .andWhere('"verification_status" <> :verified', {
        verified: ProfileVerificationStatus.Verified,
      })
      .execute();
    if (!result.affected) {
      throw new BadRequestException('Profile is already verified');
    }
    return this.profiles.findOneOrFail({ where: { userId } });
  }

  /** Admin sets the verification result. */
  async setVerification(
    userId: string,
    status:
      | ProfileVerificationStatus.Verified
      | ProfileVerificationStatus.Rejected,
  ): Promise<Profile> {
    // Targeted, for the reason in `requestVerification`: an admin decision must not write back the
    // user's other columns as they were when the admin's request read them.
    const result = await this.profiles.update(
      { userId },
      {
        verificationStatus: status,
        verifiedAt:
          status === ProfileVerificationStatus.Verified ? new Date() : null,
      },
    );
    if (!result.affected) throw new NotFoundException('Profile not found');
    return this.profiles.findOneOrFail({ where: { userId } });
  }
}
