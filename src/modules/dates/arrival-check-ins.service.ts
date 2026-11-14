import {
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import {
  arrivalConfirmedEvent,
  arrivalUndoneEvent,
} from '../analytics/events/date-events';
import { NotificationDispatchService } from '../notifications/services/notification-dispatch.service';
import { Profile } from '../profiles/entities/profile.entity';
import { firstName } from '../search/util/search-profile';
import { CreateArrivalCheckInDto } from './dto/arrival.dto';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import { DateEntity } from './entities/date.entity';
import { minutesVsStart, retentionCutoff, secondsSince } from './util/arrival';
import { DateStatus } from './util/date-lifecycle';

const TABLE = 'date_arrival_check_ins';

/**
 * The arrival check-in's write side (Date 03, SHOWUP-195). "I'm here" stores a record that stays
 * private and undoable until the person commits it (Close); only the commit tells the match. Every
 * write is one conditional statement, so two taps or two phones cannot double-send. Coordinates and
 * accuracy are written to the table and nowhere else: not to a log, an event, a push or an error.
 */
@Injectable()
export class ArrivalCheckInsService {
  private readonly logger = new Logger(ArrivalCheckInsService.name);

  constructor(
    @InjectRepository(DateArrivalCheckIn)
    private readonly rows: Repository<DateArrivalCheckIn>,
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    private readonly analytics: AnalyticsService,
    private readonly dispatch: NotificationDispatchService,
  ) {}

  /**
   * Store the caller's "I'm here". The same `clientId` as the stored row is a resend: that row, no
   * event. An uncommitted row with another `clientId` is replaced in place. A committed row is final.
   */
  async create(
    dateId: string,
    userId: string,
    dto: CreateArrivalCheckInDto,
    now: Date = new Date(),
  ): Promise<DateArrivalCheckIn> {
    const date = await this.participantDate(dateId, userId);
    if (date.status !== DateStatus.Confirmed) {
      throw new ConflictException('This date is no longer active');
    }
    const partnerId = date.userAId === userId ? date.userBId : date.userAId;
    const partnerHasRow = await this.rows.exists({
      where: { dateId, userId: partnerId },
    });

    // One statement: insert, or replace the caller's own uncommitted row from another tap. No row
    // comes back for a resend of the same tap or for a committed row, and it never throws on the
    // unique index, so two concurrent taps resolve to one stored row.
    const stored: Array<{ id: string }> = await this.rows.query(
      `INSERT INTO ${TABLE}
         (date_id, user_id, location, accuracy_m, entry, client_id, checked_in_at)
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography, $5, $6, $7, $8)
       ON CONFLICT (date_id, user_id) DO UPDATE
         SET location = EXCLUDED.location, accuracy_m = EXCLUDED.accuracy_m,
             entry = EXCLUDED.entry, client_id = EXCLUDED.client_id,
             checked_in_at = EXCLUDED.checked_in_at
         WHERE ${TABLE}.committed_at IS NULL AND ${TABLE}.client_id <> EXCLUDED.client_id
       RETURNING id`,
      [
        dateId,
        userId,
        dto.longitude,
        dto.latitude,
        dto.accuracyM ?? null,
        dto.entry,
        dto.clientId,
        now,
      ],
    );

    const row = await this.rows.findOneOrFail({ where: { dateId, userId } });
    if (stored.length === 0) {
      if (row.clientId !== dto.clientId) {
        throw new ConflictException('You are already checked in');
      }
      return row;
    }

    void this.analytics.trackServerEvent({
      userId,
      ...arrivalConfirmedEvent(
        dateId,
        dto.entry,
        minutesVsStart(date.scheduledAt, now),
        !partnerHasRow,
      ),
    });
    return row;
  }

  /** Take back an uncommitted "I'm here". No row is a no-op: an undo that already landed. */
  async undo(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    await this.participantDate(dateId, userId);
    const removed: Array<{ entry: DateArrivalCheckIn['entry']; at: Date }> =
      normalise(
        await this.rows.query(
          `DELETE FROM ${TABLE}
            WHERE date_id = $1 AND user_id = $2 AND committed_at IS NULL
        RETURNING entry, checked_in_at AS at`,
          [dateId, userId],
        ),
      );
    if (removed.length > 0) {
      void this.analytics.trackServerEvent({
        userId,
        ...arrivalUndoneEvent(
          dateId,
          removed[0].entry,
          secondsSince(removed[0].at, now),
        ),
      });
      return;
    }
    if (await this.rows.exists({ where: { dateId, userId } })) {
      throw new ConflictException('A check-in cannot be undone after Close');
    }
  }

  /**
   * Commit the caller's check-in (Close): the match is told, once. Already committed is a no-op;
   * no row (never made, or undone) is 404; a date that is no longer confirmed is 409, so an undo that landed first is never turned into a commit.
   */
  async commit(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    const date = await this.participantDate(dateId, userId);
    // A date cancelled, ended in the chat or marked no-show while the check-in waited must not
    // tell the person who ended it that the match "has arrived".
    if (date.status !== DateStatus.Confirmed) {
      throw new ConflictException('This date is no longer active');
    }
    const committed = normalise(
      await this.rows.query(
        `UPDATE ${TABLE} SET committed_at = $3
          WHERE date_id = $1 AND user_id = $2 AND committed_at IS NULL
      RETURNING id`,
        [dateId, userId, now],
      ),
    );
    if (committed.length > 0) {
      // Awaited: notifyPartner swallows its own errors, so the request still cannot fail on a push.
      await this.notifyPartner(date, userId);
      return;
    }
    if (!(await this.rows.exists({ where: { dateId, userId } }))) {
      throw new NotFoundException('No check-in to commit');
    }
  }

  /** Both people's rows for a date (no coordinates: the column is not selected). */
  rowsFor(dateId: string): Promise<DateArrivalCheckIn[]> {
    return this.rows.find({ where: { dateId } });
  }

  /**
   * The retention sweep (R14): deletes check-ins a year old or older. The cutoff is a parameter, not
   * `now()` in SQL, so a test controls the clock. Returns how many rows went.
   */
  async deleteExpired(now: Date = new Date()): Promise<number> {
    const removed = normalise<{ id: string }>(
      await this.rows.query(
        `DELETE FROM ${TABLE} WHERE checked_in_at < $1 RETURNING id`,
        [retentionCutoff(now)],
      ),
    );
    return removed.length;
  }

  /** The push to the match: the checker's first name, the date id, never a place. */
  private async notifyPartner(date: DateEntity, userId: string): Promise<void> {
    try {
      const partnerId = date.userAId === userId ? date.userBId : date.userAId;
      const profile = await this.profiles.findOne({ where: { userId } });
      await this.dispatch.sendPush(
        partnerId,
        'date_partner_arrived',
        { name: firstName(profile?.displayName ?? null) },
        { type: 'date_partner_arrived', dateId: date.id },
      );
    } catch (error) {
      // The check-in is committed; a failed push must not undo or fail it.
      this.logger.warn(
        `Arrival push failed for date ${date.id}: ${
          error instanceof Error ? error.name : 'unknown error'
        }`,
      );
    }
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

/** The pg driver answers UPDATE / DELETE ... RETURNING as `[rows, count]`; unwrap it. */
function normalise<T>(result: unknown): T[] {
  if (Array.isArray(result) && Array.isArray(result[0])) {
    return result[0] as T[];
  }
  return result as T[];
}
