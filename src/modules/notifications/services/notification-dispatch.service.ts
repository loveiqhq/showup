import { Inject, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { User, UserStatus } from '../../users/entities/user.entity';
import {
  NotificationChannel,
  NotificationLogEntry,
  NotificationLogStatus,
} from '../entities/notification-log.entity';
import { Locale, MessageParams, renderMessage } from '../messages/catalog';
import {
  EMAIL_SENDER,
  type EmailSender,
} from '../email/email-sender.interface';
import { PUSH_SENDER, type PushSender } from '../push/push-sender.interface';
import {
  categoryForType,
  isCriticalType,
  NotificationType,
  shouldSend,
} from '../util/preferences';
import { NotificationPreferencesService } from './notification-preferences.service';
import { PushTokensService } from './push-tokens.service';

/** Outcome of a dispatch attempt. `skipped` = suppressed by status/preferences (not an error). */
export interface DispatchResult {
  status: 'sent' | 'skipped' | 'failed' | 'no_recipient';
  delivered: number;
}

/**
 * The single gated send-path for every notification (SHOWUP-68). Push and email both flow through
 * here so that the account-status gate, preference gate, locale resolution, delivery logging, and
 * dead-token pruning are applied in exactly one place — a new feature cannot send a notification
 * without going through these checks.
 */
@Injectable()
export class NotificationDispatchService {
  /** Statuses that never receive a push (app-surface engagement is pointless for these). */
  private static readonly PUSH_BLOCKED: ReadonlySet<UserStatus> = new Set([
    UserStatus.Suspended,
    UserStatus.Deleted,
    UserStatus.DeletionPending,
  ]);

  constructor(
    @InjectRepository(User)
    private readonly users: Repository<User>,
    private readonly prefs: NotificationPreferencesService,
    private readonly pushTokens: PushTokensService,
    @InjectRepository(NotificationLogEntry)
    private readonly logRepo: Repository<NotificationLogEntry>,
    @Inject(PUSH_SENDER) private readonly pushSender: PushSender,
    @Inject(EMAIL_SENDER) private readonly emailSender: EmailSender,
  ) {}

  async sendPush(
    userId: string,
    type: NotificationType,
    params: MessageParams = {},
    data?: Record<string, string>,
  ): Promise<DispatchResult> {
    const user = await this.users.findOne({ where: { id: userId } });
    const locale = this.localeOf(user);

    if (!user || NotificationDispatchService.PUSH_BLOCKED.has(user.status)) {
      await this.log(
        NotificationChannel.Push,
        userId,
        type,
        NotificationLogStatus.Skipped,
        { locale },
      );
      return { status: 'skipped', delivered: 0 };
    }
    if (!shouldSend(type, await this.prefs.getState(userId))) {
      await this.log(
        NotificationChannel.Push,
        userId,
        type,
        NotificationLogStatus.Skipped,
        { locale },
      );
      return { status: 'skipped', delivered: 0 };
    }

    const { title, body } = renderMessage(type, locale, params);
    const tokens = await this.pushTokens.listForUser(userId);
    if (tokens.length === 0) return { status: 'no_recipient', delivered: 0 };

    const dead: string[] = [];
    let delivered = 0;
    for (const t of tokens) {
      const res = await this.pushSender.send({
        token: t.token,
        title,
        body,
        data,
      });
      if (res.success) {
        delivered++;
        await this.log(
          NotificationChannel.Push,
          userId,
          type,
          NotificationLogStatus.Sent,
          {
            locale,
            providerMessageId: res.messageId,
          },
        );
      } else {
        await this.log(
          NotificationChannel.Push,
          userId,
          type,
          NotificationLogStatus.Failed,
          {
            locale,
            error: res.error,
          },
        );
        if (res.invalidToken) dead.push(t.token);
      }
    }
    await this.pushTokens.prune(dead);
    return { status: delivered > 0 ? 'sent' : 'failed', delivered };
  }

  async sendEmail(
    userId: string,
    type: NotificationType,
    params: MessageParams = {},
  ): Promise<DispatchResult> {
    const user = await this.users.findOne({ where: { id: userId } });
    const locale = this.localeOf(user);
    const critical = isCriticalType(type);

    if (!user || !this.canEmail(user.status, critical)) {
      await this.log(
        NotificationChannel.Email,
        userId,
        type,
        NotificationLogStatus.Skipped,
        { locale },
      );
      return { status: 'skipped', delivered: 0 };
    }
    if (!user.email) {
      await this.log(
        NotificationChannel.Email,
        userId,
        type,
        NotificationLogStatus.Failed,
        {
          locale,
          error: 'no email address',
        },
      );
      return { status: 'failed', delivered: 0 };
    }
    if (!shouldSend(type, await this.prefs.getState(userId))) {
      await this.log(
        NotificationChannel.Email,
        userId,
        type,
        NotificationLogStatus.Skipped,
        { locale },
      );
      return { status: 'skipped', delivered: 0 };
    }

    const { title, body } = renderMessage(type, locale, params);
    const res = await this.emailSender.send({
      to: user.email,
      subject: title,
      body,
    });
    if (res.success) {
      await this.log(
        NotificationChannel.Email,
        userId,
        type,
        NotificationLogStatus.Sent,
        {
          locale,
          providerMessageId: res.messageId,
        },
      );
      return { status: 'sent', delivered: 1 };
    }
    await this.log(
      NotificationChannel.Email,
      userId,
      type,
      NotificationLogStatus.Failed,
      {
        locale,
        error: res.error,
      },
    );
    return { status: 'failed', delivered: 0 };
  }

  /**
   * Email status gate: a fully deleted user never gets email; critical/transactional emails
   * (account closure, password reset, safety) still reach suspended or leaving users; non-critical
   * email skips suspended/leaving accounts.
   */
  private canEmail(status: UserStatus, critical: boolean): boolean {
    if (status === UserStatus.Deleted) return false;
    if (critical) return true;
    return (
      status !== UserStatus.Suspended && status !== UserStatus.DeletionPending
    );
  }

  private localeOf(user: User | null): Locale {
    return user?.locale === 'de' ? 'de' : 'en';
  }

  private log(
    channel: NotificationChannel,
    userId: string,
    type: NotificationType,
    status: NotificationLogStatus,
    opts: { locale: Locale; providerMessageId?: string; error?: string },
  ): Promise<NotificationLogEntry> {
    return this.logRepo.save(
      this.logRepo.create({
        userId,
        channel,
        type,
        category: categoryForType(type),
        locale: opts.locale,
        status,
        providerMessageId: opts.providerMessageId ?? null,
        error: opts.error ?? null,
      }),
    );
  }
}
