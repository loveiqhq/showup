import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { UpsertProfileDto } from './dto/upsert-profile.dto';
import { ProfilePhoto } from './entities/profile-photo.entity';
import { Profile, ProfileVerificationStatus } from './entities/profile.entity';
import { isAtLeast18 } from './util/age';
import { isProfileComplete } from './util/completion';

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
    return this.profiles.save(this.profiles.create({ userId }));
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
    if (dto.bio !== undefined) profile.bio = dto.bio;
    if (dto.gender !== undefined) profile.gender = dto.gender;
    if (dto.lookingFor !== undefined) profile.lookingFor = dto.lookingFor;
    if (dto.isVisible !== undefined) profile.isVisible = dto.isVisible;

    profile.isComplete = await this.computeComplete(userId, profile);
    return this.profiles.save(profile);
  }

  /** A profile is "complete" once it has a name, a date of birth, and at least MIN_PHOTOS photos. */
  private async computeComplete(
    userId: string,
    profile: Profile,
  ): Promise<boolean> {
    const photoCount = await this.photos.count({ where: { userId } });
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
