import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';

import { UpsertProfileDto } from './dto/upsert-profile.dto';
import { ProfilePhoto } from './entities/profile-photo.entity';
import { Profile, ProfileVerificationStatus } from './entities/profile.entity';
import { isAtLeast18 } from './util/age';
import { isProfileComplete } from './util/completion';
import {
  normaliseHiddenFields,
  unknownHiddenFields,
} from './util/hidden-fields';
import { VISIBLE_PHOTO_STATUSES } from './util/photo-visibility';

@Injectable()
export class ProfilesService {
  constructor(
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    @InjectRepository(ProfilePhoto)
    private readonly photos: Repository<ProfilePhoto>,
  ) {}

  /** Returns the user's profile, creating an empty one on first access. */
  async getOrCreate(userId: string): Promise<Profile> {
    const existing = await this.profiles.findOne({ where: { userId } });
    if (existing) return existing;
    // hiddenFields is defaulted DB-side; set it here too so a freshly created profile is not
    // momentarily undefined in memory before it is read back.
    return this.profiles.save(
      this.profiles.create({ userId, hiddenFields: [] }),
    );
  }

  /** Apply the provided fields to the user's profile (used by both POST and PATCH). */
  async update(userId: string, dto: UpsertProfileDto): Promise<Profile> {
    const profile = await this.getOrCreate(userId);

    if (dto.dateOfBirth !== undefined) {
      if (!isAtLeast18(dto.dateOfBirth)) {
        throw new BadRequestException('You must be at least 18 years old');
      }
      profile.dateOfBirth = dto.dateOfBirth;
    }
    if (dto.displayName !== undefined) profile.displayName = dto.displayName;
    if (dto.gender !== undefined) profile.gender = dto.gender;
    if (dto.lookingFor !== undefined) profile.lookingFor = dto.lookingFor;
    // `!= null` for the same reason as hiddenFields below: @IsOptional() lets an explicit null
    // through, and is_visible is NOT NULL, so assigning it would fail at the database rather
    // than at validation. Null is never a meaningful value for this flag.
    if (dto.isVisible != null) profile.isVisible = dto.isVisible;

    // Presentation only. Note what this branch does NOT do: it never reads or writes isVisible.
    // Hiding a field must leave the user fully discoverable and matchable — a hidden age is still
    // passed to matching. Rejecting unknown values here rather than storing them keeps the column
    // readable: an unrecognised field_id would otherwise sit in the set forever, hiding nothing.
    // `!= null`, not `!== undefined`: class-validator's @IsOptional() ignores null as well as
    // undefined, so an explicit `"hiddenFields": null` reaches this line. Treating it as
    // "not supplied" is the only safe reading — the alternative is normalising null and
    // throwing. The other fields on this DTO share the looser exposure; see the note in the
    // PR rather than a drive-by change to their behaviour here.
    if (dto.hiddenFields != null) {
      const unknown = unknownHiddenFields(dto.hiddenFields);
      if (unknown.length > 0) {
        throw new BadRequestException(
          `Unknown hidden field(s): ${unknown.join(', ')}`,
        );
      }
      profile.hiddenFields = normaliseHiddenFields(dto.hiddenFields);
    }

    profile.isComplete = await this.computeComplete(userId, profile);
    return this.profiles.save(profile);
  }

  /** A profile is "complete" once it has a name, a date of birth, and at least MIN_PHOTOS photos. */
  private async computeComplete(
    userId: string,
    profile: Profile,
  ): Promise<boolean> {
    // Rejected photos don't count toward completeness (they aren't shown to others).
    const photoCount = await this.photos.count({
      where: { userId, moderationStatus: In(VISIBLE_PHOTO_STATUSES) },
    });
    return isProfileComplete({
      displayName: profile.displayName,
      dateOfBirth: profile.dateOfBirth,
      photoCount,
    });
  }

  /** Recompute completion after photos change. */
  async refreshCompletion(userId: string): Promise<void> {
    const profile = await this.profiles.findOne({ where: { userId } });
    if (!profile) return;
    const complete = await this.computeComplete(userId, profile);
    if (complete !== profile.isComplete) {
      profile.isComplete = complete;
      await this.profiles.save(profile);
    }
  }

  async requestVerification(userId: string): Promise<Profile> {
    const profile = await this.getOrCreate(userId);
    if (profile.verificationStatus === ProfileVerificationStatus.Verified) {
      throw new BadRequestException('Profile is already verified');
    }
    profile.verificationStatus = ProfileVerificationStatus.Pending;
    profile.verificationRequestedAt = new Date();
    return this.profiles.save(profile);
  }

  /** Admin sets the verification result. */
  async setVerification(
    userId: string,
    status:
      | ProfileVerificationStatus.Verified
      | ProfileVerificationStatus.Rejected,
  ): Promise<Profile> {
    const profile = await this.profiles.findOne({ where: { userId } });
    if (!profile) throw new NotFoundException('Profile not found');
    profile.verificationStatus = status;
    profile.verifiedAt =
      status === ProfileVerificationStatus.Verified ? new Date() : null;
    return this.profiles.save(profile);
  }
}
