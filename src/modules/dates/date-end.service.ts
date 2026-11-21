import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, IsNull, Not, Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import {
  dateEndedEvent,
  problemStayFlaggedEvent,
} from '../analytics/events/date-events';
import type { EndedOutcomeDto } from '../date-screen/dto/active-date.dto';
import { Match } from '../matching/entities/match.entity';
import { MatchStatus, orderedUserPair } from '../matching/util/match';
import { Profile } from '../profiles/entities/profile.entity';
import { firstName } from '../search/util/search-profile';
import { EndDateDto } from './dto/end-date.dto';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import { DateDiscomfortFlag } from './entities/date-discomfort-flag.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import {
  type RefusalCode,
  dateEndsEnabled,
  endRefusal,
  minutesVsStart,
} from './util/date-end';
import { DateStatus } from './util/date-lifecycle';

const REFUSAL_TEXT: Record<RefusalCode, string> = {
  date_not_active: 'This date is no longer active',
  before_meet: 'A no-show can be reported only from the agreed meet time',
  not_checked_in: 'Check in first, so we can confirm you were there',
};

/** A 409 whose body carries the refusal `code` (and `error`, as the repo's other coded errors do). */
export function refusalException(code: string, message: string) {
  return new ConflictException({
    statusCode: 409,
    code,
    error: code,
    message,
  });
}

/**
 * Date 06, end the date and report a problem (SHOWUP-197).
 *
 * ONE ENDS IT, THE FIRST WINS. An end runs in one transaction under a row lock on the date
 * (`SELECT ... FOR UPDATE`) with a conditional status update, as `ChangeSuggestionsService.agree`
 * does, so two taps, or an end and a cancel, serialise and the loser gets 409 `date_not_active`.
 * It stores how the date ended and each person's committed check-in at that moment, and closes the
 * pair's match; it moves nobody's Show-up Rate (epic rule 6). `date_ended` is sent once, after the
 * commit, and never for a refused or failed end.
 */
@Injectable()
export class DateEndService {
  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(DateDiscomfortFlag)
    private readonly flags: Repository<DateDiscomfortFlag>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    private readonly analytics: AnalyticsService,
    private readonly config: ConfigService,
  ) {}

  /** The `DATE_ENDS_ENABLED` switch (R1). Off, the routes answer 404. */
  endsEnabled(): boolean {
    return dateEndsEnabled({
      flag: this.config.get<string>('DATE_ENDS_ENABLED'),
      nodeEnv: this.config.get<string>('app.env'),
    });
  }

  /**
   * End the date for both people. 404 when the switch is off or the date is unknown, 403 when the
   * caller is not on it, 409 with `date_not_active` / `before_meet` / `not_checked_in` (R7).
   */
  async end(
    dateId: string,
    userId: string,
    dto: EndDateDto,
    now: Date = new Date(),
  ): Promise<EndedOutcomeDto> {
    this.assertEnabled();
    await this.participantDate(dateId, userId);

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
      const enderCheckedIn = committed.some((r) => r.userId === userId);
      const partnerCheckedIn = committed.some((r) => r.userId === partnerId);

      const refusal = endRefusal({
        status: locked.status,
        now,
        meetAt: locked.scheduledAt,
        reason: dto.reason,
        enderCheckedIn,
      });
      if (refusal !== null) {
        throw refusalException(refusal, REFUSAL_TEXT[refusal]);
      }

      const ended = await em.update(
        DateEntity,
        { id: dateId, status: DateStatus.Confirmed },
        {
          status: DateStatus.Ended,
          endedById: userId,
          endedAt: now,
          endReason: dto.reason,
          endEntry: dto.entry,
          enderCheckedIn,
          partnerCheckedIn,
        },
      );
      if (!ended.affected) {
        throw refusalException('date_not_active', REFUSAL_TEXT.date_not_active);
      }
      // The pair's match ends with the date, so Search, Likes and Instant stop reading "has a date"
      // (Date 02's end does the same). Dates are saved with no match id, so the pair finds it.
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
          toStatus: DateStatus.Ended,
          changedByUserId: userId,
        }),
      );
      return { locked, partnerId, enderCheckedIn, partnerCheckedIn };
    });

    void this.analytics.trackServerEvent({
      userId,
      ...dateEndedEvent({
        dateId,
        endReason: dto.reason,
        entry: dto.entry,
        enderCheckedIn: done.enderCheckedIn,
        partnerCheckedIn: done.partnerCheckedIn,
        minutesVsStart: minutesVsStart(done.locked.scheduledAt, now),
      }),
    });

    const profile = await this.profiles.findOne({
      where: { userId: done.partnerId },
    });
    return {
      dateId,
      name: firstName(profile?.displayName ?? null),
      endedByYou: true,
      reason: dto.reason,
    };
  }

  /**
   * "Stay for now": an internal flag against the date, one row per tap (evidence for flow 10). The
   * date stays active. 404 when the switch is off, 409 `date_not_active` once it is over.
   */
  async flagDiscomfort(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    this.assertEnabled();
    const date = await this.participantDate(dateId, userId);
    if (date.status !== DateStatus.Confirmed) {
      throw refusalException('date_not_active', REFUSAL_TEXT.date_not_active);
    }
    await this.flags.save(this.flags.create({ dateId, userId }));
    void this.analytics.trackServerEvent({
      userId,
      ...problemStayFlaggedEvent(dateId, minutesVsStart(date.scheduledAt, now)),
    });
  }

  private assertEnabled(): void {
    if (!this.endsEnabled()) throw new NotFoundException();
  }

  private async participantDate(
    dateId: string,
    userId: string,
  ): Promise<DateEntity> {
    const date = await this.dates.findOne({ where: { id: dateId } });
    if (!date) throw new NotFoundException('Date not found');
    if (date.userAId !== userId && date.userBId !== userId) {
      throw new ForbiddenException('You are not part of this date');
    }
    return date;
  }
}
