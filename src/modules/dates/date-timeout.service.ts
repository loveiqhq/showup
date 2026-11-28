import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource, IsNull, Not } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import { dateEndedEvent } from '../analytics/events/date-events';
import { Match } from '../matching/entities/match.entity';
import { MatchStatus, orderedUserPair } from '../matching/util/match';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { EndEntry, EndReason, minutesVsStart } from './util/date-end';
import { DateStatus } from './util/date-lifecycle';
import {
  TIMEOUT_AFTER_MS,
  TIMEOUT_SWEEP_LIMIT,
  dateTimeoutEnabled,
  timeoutDue,
} from './util/date-timeout';

/**
 * Date 09, the +2 h switch (SHOWUP-200). Once a minute it closes every confirmed date whose latest
 * agreed meet time is two hours gone and nobody ended: status `timed_out`, no ender, the pair's match
 * cancelled, and one `date_ended` (`timed_out`, `auto_switch`). OFF unless `DATE_TIMEOUT_ENABLED=true`
 * (read per run): shipped dark until flow 10 exists. No push of any kind.
 *
 * Each date closes in its own transaction under a row lock with a conditional update, as Date 06's
 * end does, so a person's end or cancel racing the sweep serialises: one wins, the other is a 409.
 * The due time is re-checked under the lock, so an agreed later time that landed first is respected.
 */
@Injectable()
export class DateTimeoutService {
  private readonly logger = new Logger(DateTimeoutService.name);

  constructor(
    @InjectDataSource() private readonly db: DataSource,
    private readonly analytics: AnalyticsService,
    private readonly config: ConfigService,
  ) {}

  /** Close every due date; returns how many it closed. 0, with no query, while the switch is off. */
  async closeDue(now: Date = new Date()): Promise<number> {
    if (!dateTimeoutEnabled(this.config.get<string>('DATE_TIMEOUT_ENABLED'))) {
      return 0;
    }
    const candidates: Array<{ id: string }> = await this.db.query(
      `SELECT id FROM dates
        WHERE status = 'confirmed' AND scheduled_at <= $1
        ORDER BY scheduled_at
        LIMIT $2`,
      [new Date(now.getTime() - TIMEOUT_AFTER_MS), TIMEOUT_SWEEP_LIMIT],
    );
    let closed = 0;
    for (const { id } of candidates) {
      try {
        if (await this.closeOne(id, now)) closed += 1;
      } catch (error) {
        // One bad date must not stop the rest. The id and the error name only: never a name or a place.
        this.logger.warn(
          `Timeout failed for date ${id}: ${
            error instanceof Error ? error.name : 'unknown error'
          }`,
        );
      }
    }
    if (closed > 0) this.logger.log(`Timed out ${closed} date(s)`);
    return closed;
  }

  private async closeOne(dateId: string, now: Date): Promise<boolean> {
    const done = await this.db.transaction(async (em) => {
      const locked = await em.findOne(DateEntity, {
        where: { id: dateId },
        lock: { mode: 'pessimistic_write' },
      });
      if (
        !locked ||
        locked.status !== DateStatus.Confirmed ||
        !timeoutDue(locked.scheduledAt, now)
      ) {
        return null;
      }
      const committed = await em.find(DateArrivalCheckIn, {
        where: { dateId, committedAt: Not(IsNull()) },
        select: { userId: true },
      });
      const ended = await em.update(
        DateEntity,
        { id: dateId, status: DateStatus.Confirmed },
        {
          status: DateStatus.TimedOut,
          endedById: null,
          endedAt: now,
          endReason: EndReason.TimedOut,
          endEntry: EndEntry.AutoSwitch,
        },
      );
      if (!ended.affected) return null;
      const [userAId, userBId] = orderedUserPair(
        locked.userAId,
        locked.userBId,
      );
      await em.update(
        Match,
        { userAId, userBId, status: MatchStatus.Active },
        { status: MatchStatus.Cancelled },
      );
      await em.save(
        em.create(DateStatusChange, {
          dateId,
          fromStatus: DateStatus.Confirmed,
          toStatus: DateStatus.TimedOut,
          changedByUserId: null,
        }),
      );
      return { locked, checkedInCount: committed.length };
    });
    if (!done) return false;

    void this.analytics.trackServerEvent({
      userId: done.locked.userAId,
      ...dateEndedEvent({
        dateId,
        endReason: EndReason.TimedOut,
        entry: EndEntry.AutoSwitch,
        checkedInCount: done.checkedInCount,
        minutesVsStart: minutesVsStart(done.locked.scheduledAt, now),
      }),
    });
    return true;
  }
}
