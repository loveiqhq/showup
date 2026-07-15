import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { Profile } from '../profiles/entities/profile.entity';
import { ProfilePhoto } from '../profiles/entities/profile-photo.entity';
import { User } from '../users/entities/user.entity';
import { ModerationStatusChange } from './entities/moderation-status-change.entity';
import { ModerationStanding, ModerationSubjectType } from './util/safety';

export interface StandingChangeResult {
  subjectType: ModerationSubjectType;
  subjectId: string;
  from: ModerationStanding | null;
  to: ModerationStanding;
}

/**
 * Moderation standing (SHOWUP-79). Sets the safety standing on a user, profile, or photo and writes
 * an append-only history row for every change. Limited/banned users are kept out of discovery and
 * matching by the proximity query and the like check (see SafetyModule wiring), so setting a user
 * to `limited`/`banned` here is what removes them from those surfaces.
 */
@Injectable()
export class ModerationService {
  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(ProfilePhoto)
    private readonly photos: Repository<ProfilePhoto>,
    @InjectRepository(ModerationStatusChange)
    private readonly changes: Repository<ModerationStatusChange>,
  ) {}

  async setUserStanding(
    userId: string,
    standing: ModerationStanding,
    changedByUserId: string | null,
    reason?: string,
  ): Promise<StandingChangeResult> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    const from = user.moderationStanding;
    user.moderationStanding = standing;
    await this.users.save(user);
    await this.record(
      ModerationSubjectType.User,
      userId,
      from,
      standing,
      changedByUserId,
      reason,
    );
    return {
      subjectType: ModerationSubjectType.User,
      subjectId: userId,
      from,
      to: standing,
    };
  }

  async setProfileStanding(
    profileId: string,
    standing: ModerationStanding,
    changedByUserId: string | null,
    reason?: string,
  ): Promise<StandingChangeResult> {
    const profile = await this.profiles.findOne({ where: { id: profileId } });
    if (!profile) throw new NotFoundException('Profile not found');
    const from = profile.moderationStanding;
    profile.moderationStanding = standing;
    await this.profiles.save(profile);
    await this.record(
      ModerationSubjectType.Profile,
      profileId,
      from,
      standing,
      changedByUserId,
      reason,
    );
    return {
      subjectType: ModerationSubjectType.Profile,
      subjectId: profileId,
      from,
      to: standing,
    };
  }

  async setPhotoStanding(
    photoId: string,
    standing: ModerationStanding,
    changedByUserId: string | null,
    reason?: string,
  ): Promise<StandingChangeResult> {
    const photo = await this.photos.findOne({ where: { id: photoId } });
    if (!photo) throw new NotFoundException('Photo not found');
    const from = photo.moderationStanding;
    photo.moderationStanding = standing;
    await this.photos.save(photo);
    await this.record(
      ModerationSubjectType.Photo,
      photoId,
      from,
      standing,
      changedByUserId,
      reason,
    );
    return {
      subjectType: ModerationSubjectType.Photo,
      subjectId: photoId,
      from,
      to: standing,
    };
  }

  /** Users currently in a given standing (for the staff queue). Capped. */
  async listUsersByStanding(standing: ModerationStanding): Promise<User[]> {
    return this.users.find({
      where: { moderationStanding: standing },
      order: { createdAt: 'DESC' },
      take: 200,
    });
  }

  async getUser(userId: string): Promise<User> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) throw new NotFoundException('User not found');
    return user;
  }

  /** A user's profile, if they have one (for the staff safety view). */
  async getUserProfile(userId: string): Promise<Profile | null> {
    return this.profiles.findOne({ where: { userId } });
  }

  /** The full standing history of one subject, newest first (audit trail). */
  async history(
    subjectType: ModerationSubjectType,
    subjectId: string,
  ): Promise<ModerationStatusChange[]> {
    return this.changes.find({
      where: { subjectType, subjectId },
      order: { createdAt: 'DESC' },
    });
  }

  private async record(
    subjectType: ModerationSubjectType,
    subjectId: string,
    from: ModerationStanding | null,
    to: ModerationStanding,
    changedByUserId: string | null,
    reason?: string,
  ): Promise<void> {
    await this.changes.save(
      this.changes.create({
        subjectType,
        subjectId,
        fromStanding: from,
        toStanding: to,
        changedByUserId: changedByUserId ?? null,
        reason: reason ?? null,
      }),
    );
  }
}
