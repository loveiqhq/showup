import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { NotificationPreference } from '../entities/notification-preference.entity';
import {
  DEFAULT_PREFERENCES,
  NotificationPreferenceState,
} from '../util/preferences';

/** Fields a user may change on their notification preferences. */
export type NotificationPreferencePatch = Partial<NotificationPreferenceState>;

/**
 * Stores and reads each user's notification preferences (SHOWUP-66). New users implicitly get the
 * defaults (marketing off) until they save anything, so `getState` never writes — it is called on
 * every send by the dispatcher.
 */
@Injectable()
export class NotificationPreferencesService {
  constructor(
    @InjectRepository(NotificationPreference)
    private readonly repo: Repository<NotificationPreference>,
  ) {}

  /** Effective preferences for gating: the saved row, or the defaults if none exists. Read-only. */
  async getState(userId: string): Promise<NotificationPreferenceState> {
    const row = await this.repo.findOne({ where: { userId } });
    if (!row) return { ...DEFAULT_PREFERENCES };
    return {
      essential: row.essential,
      engagement: row.engagement,
      marketing: row.marketing,
    };
  }

  /** Fetch the user's row, creating it with defaults on first access (for the GET endpoint). */
  async ensure(userId: string): Promise<NotificationPreference> {
    const existing = await this.repo.findOne({ where: { userId } });
    if (existing) return existing;
    return this.repo.save(this.repo.create({ userId, ...DEFAULT_PREFERENCES }));
  }

  /** Apply a partial change (only provided fields) and persist it. */
  async update(
    userId: string,
    patch: NotificationPreferencePatch,
  ): Promise<NotificationPreference> {
    const row = await this.ensure(userId);
    if (patch.essential !== undefined) row.essential = patch.essential;
    if (patch.engagement !== undefined) row.engagement = patch.engagement;
    if (patch.marketing !== undefined) row.marketing = patch.marketing;
    return this.repo.save(row);
  }
}
