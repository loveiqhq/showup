import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { User, UserStatus } from './entities/user.entity';

/** Normalized social-login identity (structurally matches auth's SocialIdentity). */
export interface SocialProfile {
  provider: 'apple' | 'google';
  providerId: string;
  email: string | null;
  emailVerified: boolean;
  name: string | null;
}

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
  ) {}

  findById(id: string): Promise<User | null> {
    return this.users.findOne({ where: { id } });
  }

  findByPhone(phone: string): Promise<User | null> {
    return this.users.findOne({ where: { phone } });
  }

  findByEmail(email: string): Promise<User | null> {
    return this.users.findOne({ where: { email: email.toLowerCase() } });
  }

  findByProvider(
    provider: 'apple' | 'google',
    providerId: string,
  ): Promise<User | null> {
    return provider === 'apple'
      ? this.users.findOne({ where: { appleUserId: providerId } })
      : this.users.findOne({ where: { googleUserId: providerId } });
  }

  createFromPhone(phone: string): Promise<User> {
    return this.users.save(
      this.users.create({
        phone,
        phoneVerifiedAt: new Date(),
        status: UserStatus.Active,
      }),
    );
  }

  createFromSocial(
    profile: SocialProfile,
    name?: string | null,
  ): Promise<User> {
    return this.users.save(
      this.users.create({
        email: profile.email ? profile.email.toLowerCase() : null,
        emailVerifiedAt:
          profile.email && profile.emailVerified ? new Date() : null,
        appleUserId: profile.provider === 'apple' ? profile.providerId : null,
        googleUserId: profile.provider === 'google' ? profile.providerId : null,
        displayName: name ?? profile.name ?? null,
        status: UserStatus.Active,
      }),
    );
  }

  linkSocial(user: User, profile: SocialProfile): Promise<User> {
    if (profile.provider === 'apple') user.appleUserId = profile.providerId;
    else user.googleUserId = profile.providerId;
    if (!user.email && profile.email) {
      user.email = profile.email.toLowerCase();
      if (profile.emailVerified) user.emailVerifiedAt = new Date();
    }
    return this.users.save(user);
  }

  markPhoneVerified(user: User): Promise<User> {
    if (!user.phoneVerifiedAt) user.phoneVerifiedAt = new Date();
    if (user.status === UserStatus.Registered) user.status = UserStatus.Active;
    return this.users.save(user);
  }

  touchLastLogin(user: User): Promise<User> {
    user.lastLoginAt = new Date();
    return this.users.save(user);
  }

  requestDeletion(user: User): Promise<User> {
    user.status = UserStatus.DeletionPending;
    user.deletionRequestedAt = new Date();
    return this.users.save(user);
  }

  cancelDeletion(user: User): Promise<User> {
    user.status = UserStatus.Active;
    user.deletionRequestedAt = null;
    return this.users.save(user);
  }

  /** Scrub PII and mark deleted. Final purge scheduling comes with BullMQ (Epic 17). */
  anonymize(user: User): Promise<User> {
    user.phone = null;
    user.email = null;
    user.appleUserId = null;
    user.googleUserId = null;
    user.displayName = 'Deleted user';
    user.phoneVerifiedAt = null;
    user.emailVerifiedAt = null;
    user.status = UserStatus.Deleted;
    user.deletedAt = new Date();
    return this.users.save(user);
  }
}
