import {
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, IsNull, Not, Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import { dateCancelledEvent } from '../analytics/events/date-events';
import type { EndedOutcomeDto } from '../date-screen/dto/active-date.dto';
import { Match } from '../matching/entities/match.entity';
import { MatchStatus, orderedUserPair } from '../matching/util/match';
import { NotificationDispatchService } from '../notifications/services/notification-dispatch.service';
import { Profile } from '../profiles/entities/profile.entity';
import { firstName } from '../search/util/search-profile';
import {
  DateEndService,
  REFUSAL_TEXT,
  refusalException,
} from './date-end.service';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import {
  ChangeEntry,
  DateChangeSuggestion,
} from './entities/date-change-suggestion.entity';
import {
  DatePairBlock,
  PAIR_BLOCK_RED_LINK_CANCEL,
} from './entities/date-pair-block.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { cancelRefusal, hoursNotice } from './util/date-cancel';
import { DateStatus } from './util/date-lifecycle';
import { SHOW_UP_RATE } from './util/show-up-rate.config';

/**
 * Date 07, cancel this date (SHOWUP-198): the red link. A cancel without reaching out is a no-show.
 *
 * ONE TRANSACTION UNDER A ROW LOCK, as `DateEndService.end` does: the first of two simultaneous
 * cancels (or an end and a cancel) wins; the rest get 409 `date_not_active` and fire nothing. A
 * canceller with a committed check-in gets 409 `checked_in`. On success the date is `cancelled`, the
 * no-show is recorded (`penalty_points` from the rate config, `hours_notice`, both check-in states,
 * `change_requested` derived from the canceller's `cancel_sheet` suggestions), the pair gets a
 * permanent block, and the match is closed. `date_cancelled` and the match's push go out once, after
 * the commit. The Show-up Rate itself is not computed here (its formula is not built).
 */
@Injectable()
export class DateCancelService {
  private readonly logger = new Logger(DateCancelService.name);

  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    private readonly analytics: AnalyticsService,
    private readonly dispatch: NotificationDispatchService,
    private readonly ends: DateEndService,
  ) {}

  /** 404 when the switch is off or the date is unknown, 403 for a stranger, 409 `date_not_active | checked_in`. */
  async cancel(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<EndedOutcomeDto> {
    if (!this.ends.endsEnabled()) throw new NotFoundException();
    const known = await this.dates.findOne({ where: { id: dateId } });
    if (!known) throw new NotFoundException('Date not found');
    if (known.userAId !== userId && known.userBId !== userId) {
      throw new ForbiddenException('You are not part of this date');
    }

    const done = await this.db.transaction(async (em) => {
      const locked = await em.findOne(DateEntity, {
        where: { id: dateId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!locked) throw new NotFoundException('Date not found');
      const partnerId =
        locked.userAId === userId ? locked.userBId : locked.userAId;
      const committed = await em.find(DateArrivalCheckIn, {
        where: { dateId, committedAt: Not(IsNull()) },
        select: { userId: true },
      });
      const cancellerCheckedIn = committed.some((r) => r.userId === userId);
      const partnerCheckedIn = committed.some((r) => r.userId === partnerId);

      const refusal = cancelRefusal({
        status: locked.status,
        cancellerCheckedIn,
      });
      if (refusal !== null) {
        throw refusalException(refusal, REFUSAL_TEXT[refusal]);
      }

      const changeRequested =
        (await em.count(DateChangeSuggestion, {
          where: { dateId, senderId: userId, entry: ChangeEntry.CancelSheet },
        })) > 0;
      const notice = hoursNotice(now, locked.scheduledAt);
      const penalty = SHOW_UP_RATE.noShowPenalty;

      const updated = await em.update(
        DateEntity,
        { id: dateId, status: DateStatus.Confirmed },
        {
          status: DateStatus.Cancelled,
          cancelledById: userId,
          cancelledAt: now,
          hoursNotice: notice,
          penaltyPoints: penalty,
          enderCheckedIn: cancellerCheckedIn,
          partnerCheckedIn,
          cancelChangeRequested: changeRequested,
        },
      );
      if (!updated.affected) {
        throw refusalException('date_not_active', REFUSAL_TEXT.date_not_active);
      }
      const [userLow, userHigh] = orderedUserPair(
        locked.userAId,
        locked.userBId,
      );
      await em
        .createQueryBuilder()
        .insert()
        .into(DatePairBlock)
        .values({
          userLow,
          userHigh,
          dateId,
          reason: PAIR_BLOCK_RED_LINK_CANCEL,
        })
        .orIgnore()
        .execute();
      await em.update(
        Match,
        { userAId: userLow, userBId: userHigh, status: MatchStatus.Active },
        { status: MatchStatus.Cancelled },
      );
      await em.save(
        em.create(DateStatusChange, {
          dateId,
          fromStatus: DateStatus.Confirmed,
          toStatus: DateStatus.Cancelled,
          changedByUserId: userId,
        }),
      );
      return { partnerId, notice, penalty, changeRequested };
    });

    void this.analytics.trackServerEvent({
      userId,
      ...dateCancelledEvent({
        dateId,
        hoursNotice: done.notice,
        penaltyPoints: done.penalty,
        changeRequested: done.changeRequested,
      }),
    });

    const [partner, self] = await Promise.all([
      this.profiles.findOne({ where: { userId: done.partnerId } }),
      this.profiles.findOne({ where: { userId } }),
    ]);
    try {
      await this.dispatch.sendPush(
        done.partnerId,
        'date_cancelled',
        { name: firstName(self?.displayName ?? null) },
        { type: 'date_cancelled', dateId },
      );
    } catch (error) {
      // The cancel is committed; a failed push must not undo or fail it.
      this.logger.warn(
        `Cancel push failed for date ${dateId}: ${
          error instanceof Error ? error.name : 'unknown error'
        }`,
      );
    }
    return {
      dateId,
      name: firstName(partner?.displayName ?? null),
      endedByYou: true,
      reason: 'cancelled',
    };
  }
}
