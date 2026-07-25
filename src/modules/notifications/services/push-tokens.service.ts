import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';

import { PushPlatform, PushToken } from '../entities/push-token.entity';

export interface RegisterPushTokenInput {
  token: string;
  platform: PushPlatform;
}

/**
 * Manages device push tokens (SHOWUP-67). A token is unique across the table, so registering an
 * existing token re-points it to the current user (devices get handed on, apps reinstall) instead
 * of duplicating. Deregistration is idempotent. `prune` drops tokens FCM reported as dead.
 */
@Injectable()
export class PushTokensService {
  constructor(
    @InjectRepository(PushToken)
    private readonly repo: Repository<PushToken>,
  ) {}

  async register(
    userId: string,
    input: RegisterPushTokenInput,
  ): Promise<PushToken> {
    const existing = await this.repo.findOne({
      where: { token: input.token },
    });
    if (existing) {
      existing.userId = userId;
      existing.platform = input.platform;
      return this.repo.save(existing);
    }
    return this.repo.save(
      this.repo.create({
        userId,
        token: input.token,
        platform: input.platform,
      }),
    );
  }

  /** Remove a device's token (e.g. on logout). Idempotent — unknown tokens are a no-op. */
  async deregister(userId: string, token: string): Promise<void> {
    await this.repo.delete({ token });
  }

  /** Tokens currently registered to a user (used by the dispatcher to fan a push out). */
  listForUser(userId: string): Promise<PushToken[]> {
    return this.repo.find({ where: { userId } });
  }

  /** Drop tokens the provider reported as permanently invalid. No-op for an empty list. */
  async prune(tokens: string[]): Promise<void> {
    if (tokens.length === 0) return;
    await this.repo.delete({ token: In(tokens) });
  }
}
