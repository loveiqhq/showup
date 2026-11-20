import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  Logger,
  NotFoundException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, EntityManager, IsNull, Not, Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import {
  changeAgreedEvent,
  changeExpiredEvent,
  changeSuggestedEvent,
  coordinateUnlockedEvent,
} from '../analytics/events/date-events';
import { NotificationDispatchService } from '../notifications/services/notification-dispatch.service';
import { NotificationsScheduler } from '../notifications/services/notifications.scheduler';
import {
  PLACES_PROVIDER,
  type Place,
  type PlacesProvider,
  PlacesUnavailableError,
} from '../places/places.provider';
import { Profile } from '../profiles/entities/profile.entity';
import { isTimeZone } from '../search/util/search';
import { firstName } from '../search/util/search-profile';
import { DateChangeService } from './date-change.service';
import {
  AgreeChangeDto,
  ChangePendingDto,
  ChangeStateDto,
  ChangeSuggestionDto,
  SuggestChangeDto,
} from './dto/change.dto';
import { DateArrivalCheckIn } from './entities/date-arrival-check-in.entity';
import {
  ChangeKind,
  ChangeStatus,
  DateChangeSuggestion,
} from './entities/date-change-suggestion.entity';
import { DateEntity } from './entities/date.entity';
import { PLACE_TYPES, changeDeadline } from './util/change-rules';
import {
  type VenueIdentity,
  agreeRefusal,
  changeStateOf,
  clockLabel,
  isBindingVenue,
  pickChangeRequest,
  replacesOf,
  suggestRefusal,
  toPendingDto,
  toSuggestionDto,
} from './util/change-suggestions';
import { minutesBeforeStart } from './util/coordinate-unlock';
import { DateStatus } from './util/date-lifecycle';
import { chatOpensAt, isUniqueViolation } from './util/pre-date-chat';

/**
 * The zone a push's `{deadline}` is written in. The server holds no zone for a person (the Date
 * screen reads the phone's `tz` per request, and the reminder job carries a clock string someone
 * else formatted), and a push has no request to read one from. The product runs in Berlin, so the
 * interim copy (R10) uses it; final copy moves this to the person's own zone.
 */
export const PUSH_TIME_ZONE = 'Europe/Berlin';

/** The pg driver answers UPDATE ... RETURNING as `[rows, count]`; unwrap it. */
function rowsOf<T>(result: unknown): T[] {
  if (Array.isArray(result) && Array.isArray(result[0])) {
    return result[0] as T[];
  }
  return result as T[];
}

/** Thrown inside `agree`'s transaction (nothing written yet) to answer a repeat with the stored row. */
class AlreadyAgreed extends Error {
  constructor(readonly row: DateChangeSuggestion) {
    super('Already agreed');
  }
}

interface BindingVenue extends VenueIdentity {
  id: string;
}

/** Test seams for the dev routes; never set by a real request. */
export interface SuggestOptions {
  /** Dev only: the test match suggests a time that is not in the caller's free hours. */
  skipSlotCheck?: boolean;
}

/**
 * Suggest, agree and expire a change of a date's meet time or place (Date 05, SHOWUP-196).
 *
 * THE BINDING VALUES NEVER MOVE UNTIL AGREED. A suggestion is a row; `dates.scheduled_at` and
 * `dates.venue_id` change only in `agree`. Both writes lock the date row (`SELECT ... FOR UPDATE`)
 * so two phones, or a suggestion and an agree, serialise: the later suggestion of a kind replaces the
 * earlier, and `I agree` on a replaced one is a 409. Nothing here logs, tracks or pushes a place
 * name, address, coordinate or distance.
 */
@Injectable()
export class ChangeSuggestionsService {
  private readonly logger = new Logger(ChangeSuggestionsService.name);

  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @InjectRepository(DateChangeSuggestion)
    private readonly rows: Repository<DateChangeSuggestion>,
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    @Inject(PLACES_PROVIDER) private readonly provider: PlacesProvider,
    private readonly changes: DateChangeService,
    private readonly analytics: AnalyticsService,
    private readonly dispatch: NotificationDispatchService,
    private readonly scheduler: NotificationsScheduler,
  ) {}

  /**
   * Store a suggestion. 409 when the date is over, past meet - 0:30, after a committed check-in,
   * for a time that is not one of the match's slots or for the date's current place; 400 for a
   * missing value or a place that is not a public café, bar or restaurant; 503 when places cannot be
   * fetched. A repeat of a `clientId` answers with the stored row and fires nothing.
   */
  async suggest(
    dateId: string,
    userId: string,
    dto: SuggestChangeDto,
    now: Date = new Date(),
    opts: SuggestOptions = {},
  ): Promise<ChangeSuggestionDto> {
    const date = await this.participantDate(dateId, userId);

    // Before any check: a resend of a Suggest that landed was made, even if the date has moved on.
    const sent = await this.sentBy(userId, dto.clientId);
    if (sent) {
      if (sent.dateId !== date.id) {
        throw new ConflictException('This suggestion id is already used');
      }
      return toSuggestionDto(sent, userId);
    }

    const tz = isTimeZone(dto.tz) ? dto.tz : 'UTC';
    const time =
      dto.kind === ChangeKind.Time ? this.parsedTime(dto.time) : null;
    if (dto.kind === ChangeKind.Place && !dto.placeId) {
      throw new BadRequestException('A place suggestion needs a place');
    }
    // Outside the transaction: a slow provider must not hold the date row locked.
    const place =
      dto.kind === ChangeKind.Place && dto.placeId
        ? await this.publicPlace(dto.placeId)
        : null;

    const created = await this.db
      .transaction(async (em) => {
        const locked = await em.findOne(DateEntity, {
          where: { id: date.id },
          lock: { mode: 'pessimistic_write' },
        });
        if (!locked) throw new NotFoundException('Date not found');
        const refusal = suggestRefusal({
          status: locked.status,
          bindingAt: locked.scheduledAt,
          now,
          anyCommittedCheckIn: await this.anyCommittedCheckIn(em, locked.id),
        });
        if (refusal === 'ended') {
          throw new ConflictException('This date is over');
        }
        if (refusal === 'cutoff') {
          throw new ConflictException(
            'Changes close 30 minutes before the meet time',
          );
        }
        if (refusal === 'checked_in') {
          throw new ConflictException('Someone has already arrived');
        }

        const venue = await this.bindingVenue(em, locked.venueId);
        if (time && !opts.skipSlotCheck) {
          const slots = await this.changes.matchSlots(locked, userId, tz);
          if (!slots.some((s) => s.getTime() === time.getTime())) {
            throw new ConflictException('That time is not one of the slots');
          }
        }
        if (place && venue) {
          const candidate = {
            placeId: place.placeId,
            name: place.name,
            location: place.location,
          };
          if (isBindingVenue(venue, candidate)) {
            throw new ConflictException('That is already the meeting place');
          }
        }

        const replaced = rowsOf<{ sender_id: string }>(
          await em.query(
            `UPDATE "date_change_suggestions"
              SET "status" = 'replaced', "replaced_at" = $3
            WHERE "date_id" = $1 AND "kind" = $2::change_kind AND "status" = 'pending'
        RETURNING "sender_id"`,
            [locked.id, dto.kind, now],
          ),
        );
        const deadline = changeDeadline(locked.scheduledAt);
        const [{ id }] = await em.query<Array<{ id: string }>>(
          `INSERT INTO "date_change_suggestions"
           ("date_id", "sender_id", "kind", "value_time", "place_id", "place_name", "place_type",
            "place_address", "place_location", "was_time", "was_venue_id", "was_place_name",
            "deadline", "entry", "from_search", "client_id", "created_at")
         VALUES ($1, $2, $3::change_kind, $4, $5, $6, $7, $8,
                 ST_SetSRID(ST_MakePoint($9::float8, $10::float8), 4326)::geography,
                 $11, $12, $13, $14, $15::change_entry, $16, $17, $18)
         RETURNING "id"`,
          [
            locked.id,
            userId,
            dto.kind,
            time,
            place?.placeId ?? null,
            place?.name ?? null,
            place?.type ?? null,
            place?.address ?? null,
            place?.location.lng ?? null,
            place?.location.lat ?? null,
            locked.scheduledAt,
            locked.venueId,
            venue?.name ?? null,
            deadline,
            dto.entry,
            dto.kind === ChangeKind.Place ? (dto.fromSearch ?? false) : null,
            dto.clientId,
            now,
          ],
        );
        const row = await em.findOneOrFail(DateChangeSuggestion, {
          where: { id },
        });
        return {
          row,
          replaces: replacesOf(replaced[0]?.sender_id ?? null, userId),
          bindingAt: locked.scheduledAt,
          receiverId:
            locked.userAId === userId ? locked.userBId : locked.userAId,
        };
      })
      .catch(async (error: unknown) => {
        // Two sends of one Suggest tap at once: the pre-check ran outside the lock, so the loser
        // reaches the (sender, client_id) index. Answer with the winner's row, like the chat does.
        const winner = isUniqueViolation(error)
          ? await this.sentBy(userId, dto.clientId)
          : null;
        if (winner) return { stored: winner };
        throw error;
      });

    if ('stored' in created) return toSuggestionDto(created.stored, userId);

    const { row, bindingAt } = created;
    void this.analytics.trackServerEvent({
      userId,
      ...changeSuggestedEvent({
        dateId: date.id,
        suggestionId: row.id,
        kind: dto.kind,
        entry: dto.entry,
        replaces: created.replaces,
        offsetMin:
          time === null
            ? null
            : Math.round((time.getTime() - bindingAt.getTime()) / 60_000),
        fromSearch: dto.kind === ChangeKind.Place ? row.fromSearch : null,
        minutesToDeadline: Math.trunc(
          (row.deadline.getTime() - now.getTime()) / 60_000,
        ),
        minutesBeforeStart: minutesBeforeStart(bindingAt, now),
      }),
    });
    await this.pushSuggested(created.receiverId, userId, date.id, row.deadline);
    return toSuggestionDto(row, userId);
  }

  /**
   * `I agree` (R11, R12, R22): only the receiver of a pending suggestion. A time moves
   * `dates.scheduled_at`, a place upserts the venue by its place id and moves `dates.venue_id`.
   * 404 unknown, 403 not a participant, 409 when it is not pending any more (replaced, expired,
   * replaced, expired), is the caller's own or is past its deadline. Someone having arrived does
   * not refuse it: an open suggestion stays answerable until its deadline. The receiver agreeing
   * again to one they already agreed answers 200 with it (a lost response), firing nothing.
   */
  async agree(
    dateId: string,
    suggestionId: string,
    userId: string,
    dto: AgreeChangeDto,
    now: Date = new Date(),
  ): Promise<ChangeSuggestionDto> {
    await this.participantDate(dateId, userId);

    const done = await this.db
      .transaction(async (em) => {
        const locked = await em.findOne(DateEntity, {
          where: { id: dateId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!locked) throw new NotFoundException('Date not found');
        const row = await em.findOne(DateChangeSuggestion, {
          where: { id: suggestionId, dateId },
        });
        if (!row) throw new NotFoundException('Suggestion not found');
        if (locked.status !== DateStatus.Confirmed) {
          throw new ConflictException('This date is over');
        }
        const refusal = agreeRefusal({
          status: row.status,
          senderId: row.senderId,
          viewerId: userId,
          deadline: row.deadline,
          now,
        });
        if (refusal === 'already_agreed') throw new AlreadyAgreed(row);
        if (refusal !== null) {
          throw new ConflictException(
            {
              not_pending: 'This suggestion is no longer open',
              sender: 'You cannot agree to your own suggestion',
              expired: 'The time to answer has passed',
            }[refusal],
          );
        }

        const oldTime = locked.scheduledAt;
        let unlockedNow = false;
        if (row.kind === ChangeKind.Time) {
          const newTime = row.valueTime as Date;
          await em.update(DateEntity, { id: dateId }, { scheduledAt: newTime });
          // The place suggestion still open now answers to the NEW meet time - 0:30.
          await em.query(
            `UPDATE "date_change_suggestions" SET "deadline" = $2
            WHERE "date_id" = $1 AND "kind" = 'place' AND "status" = 'pending'`,
            [dateId, changeDeadline(newTime)],
          );
          // The chat was open by the old time and is unstamped: stamp it now. Without that, a later
          // meet time would shut a chat the two people were already writing in (R11).
          if (
            locked.coordinateUnlockedAt === null &&
            chatOpensAt(oldTime).getTime() <= now.getTime()
          ) {
            unlockedNow =
              rowsOf(
                await em.query(
                  `UPDATE "dates" SET "coordinate_unlocked_at" = $2
                  WHERE "id" = $1 AND "coordinate_unlocked_at" IS NULL RETURNING "id"`,
                  [dateId, now],
                ),
              ).length > 0;
          }
        } else {
          const [venue] = await em.query<Array<{ id: string }>>(
            `INSERT INTO "venues" ("name", "address", "location", "place_id", "place_type")
           SELECT "place_name", "place_address", "place_location", "place_id", "place_type"
             FROM "date_change_suggestions" WHERE "id" = $1
           ON CONFLICT ("place_id") WHERE "place_id" IS NOT NULL DO UPDATE
             SET "name" = EXCLUDED."name", "address" = EXCLUDED."address",
                 "location" = EXCLUDED."location", "place_type" = EXCLUDED."place_type"
           RETURNING "id"`,
            [row.id],
          );
          await em.update(DateEntity, { id: dateId }, { venueId: venue.id });
        }

        // Conditional, so a concurrent expiry that got there first leaves this a 409, not two answers.
        const marked = rowsOf(
          await em.query(
            `UPDATE "date_change_suggestions" SET "status" = 'agreed', "answered_at" = $2
            WHERE "id" = $1 AND "status" = 'pending' RETURNING "id"`,
            [row.id, now],
          ),
        );
        if (marked.length === 0) {
          throw new ConflictException('This suggestion is no longer open');
        }
        return {
          row,
          oldTime,
          unlockedNow,
          users: [locked.userAId, locked.userBId],
        };
      })
      .catch((error: unknown) => {
        // The receiver's own agree again, its answer lost: the stored state, and nothing fires twice.
        if (error instanceof AlreadyAgreed) return error;
        throw error;
      });
    if (done instanceof AlreadyAgreed) return toSuggestionDto(done.row, userId);

    const { row, oldTime } = done;
    void this.analytics.trackServerEvent({
      userId,
      ...changeAgreedEvent({
        dateId,
        suggestionId: row.id,
        kind: row.kind,
        surface: dto.surface,
        secondsSinceSuggested: Math.trunc(
          (now.getTime() - row.createdAt.getTime()) / 1000,
        ),
        minutesToDeadline: Math.trunc(
          (row.deadline.getTime() - now.getTime()) / 60_000,
        ),
      }),
    });
    if (row.kind === ChangeKind.Time) {
      const event = coordinateUnlockedEvent(
        minutesBeforeStart(oldTime, now),
        'time',
      );
      if (done.unlockedNow) {
        for (const id of done.users) {
          void this.analytics.trackServerEvent({ userId: id, ...event });
        }
      }
      await this.rescheduleReminder(
        dateId,
        (row.valueTime as Date).getTime() - oldTime.getTime(),
      );
    }
    await this.pushAgreed(row.senderId, userId, dateId);
    const fresh = await this.rows.findOneOrFail({ where: { id: row.id } });
    return toSuggestionDto(fresh, userId);
  }

  /**
   * Expires every pending suggestion past its deadline (R10, run once a minute). One conditional
   * UPDATE, so however many workers run it each row expires, and is reported, once; it snapshots the
   * binding that stayed. A replaced or agreed row never matches. One `change_expired` per row (keyed
   * to its sender) and a push to both people. Returns how many expired.
   */
  async expireDue(now: Date = new Date()): Promise<number> {
    const expired = rowsOf<{
      id: string;
      date_id: string;
      kind: ChangeKind;
      sender_id: string;
      user_a_id: string;
      user_b_id: string;
    }>(
      await this.db.query(
        `UPDATE "date_change_suggestions" s
            SET "status" = 'expired', "expired_at" = $1,
                "expired_binding_time" = d."scheduled_at",
                "expired_binding_place_name" =
                  (SELECT v."name" FROM "venues" v WHERE v."id" = d."venue_id")
           FROM "dates" d
          WHERE d."id" = s."date_id" AND d."status" = 'confirmed'
            AND s."status" = 'pending' AND s."deadline" <= $1
      RETURNING s."id", s."date_id", s."kind", s."sender_id", d."user_a_id", d."user_b_id"`,
        [now],
      ),
    );
    for (const r of expired) {
      void this.analytics.trackServerEvent({
        userId: r.sender_id,
        ...changeExpiredEvent(r.date_id, r.id, r.kind),
      });
      for (const id of [r.user_a_id, r.user_b_id]) {
        await this.safely(r.date_id, () =>
          this.dispatch.sendPush(
            id,
            'date_change_expired',
            {},
            { type: 'date_change_expired', dateId: r.date_id },
          ),
        );
      }
    }
    return expired.length;
  }

  /** The date's suggestions with their history, oldest first, for the chat thread (R9). */
  async listFor(
    dateId: string,
    userId: string,
  ): Promise<ChangeSuggestionDto[]> {
    await this.participantDate(dateId, userId);
    const rows = await this.rows.find({
      where: { dateId },
      order: { createdAt: 'ASC' },
    });
    return rows.map((r) => toSuggestionDto(r, userId));
  }

  /**
   * `change` and `changeRequest` for `GET /me/date` (R7, R8, R13). `slots` come from the match's
   * free hours in the viewer's zone.
   */
  async payloadFor(
    date: DateEntity,
    viewerId: string,
    tz: string,
    anyCommittedCheckIn: boolean,
  ): Promise<{
    change: ChangeStateDto;
    changeRequest: ChangePendingDto | null;
  }> {
    const [rows, slots] = await Promise.all([
      this.rows.find({
        where: { dateId: date.id },
        order: { createdAt: 'ASC' },
      }),
      this.changes.matchSlots(date, viewerId, tz),
    ]);
    const firstPlace = rows
      .filter(
        (r) =>
          r.kind === ChangeKind.Place &&
          r.status === ChangeStatus.Agreed &&
          r.answeredAt,
      )
      .sort(
        (a, b) =>
          (a.answeredAt as Date).getTime() - (b.answeredAt as Date).getTime(),
      )[0];
    const change = changeStateOf({
      rows,
      viewerId,
      bindingAt: date.scheduledAt,
      slots,
      anyCommittedCheckIn,
      placeWas: firstPlace ? await this.placeWas(firstPlace) : null,
    });
    const request = pickChangeRequest(rows, viewerId);
    return {
      change,
      changeRequest: request ? toPendingDto(request, viewerId) : null,
    };
  }

  /** The venue the first agreed place change replaced: its row when it still has one, else the name. */
  private async placeWas(
    first: DateChangeSuggestion,
  ): Promise<{ name: string; address?: string } | null> {
    if (first.wasVenueId) {
      const [v] = await this.db.query<
        Array<{ name: string; address: string | null }>
      >(`SELECT "name", "address" FROM "venues" WHERE "id" = $1`, [
        first.wasVenueId,
      ]);
      if (v)
        return v.address
          ? { name: v.name, address: v.address }
          : { name: v.name };
    }
    return first.wasPlaceName ? { name: first.wasPlaceName } : null;
  }

  private async sentBy(
    userId: string,
    clientId: string,
  ): Promise<DateChangeSuggestion | null> {
    return this.rows.findOne({ where: { senderId: userId, clientId } });
  }

  private parsedTime(value: string | undefined): Date {
    const time = value ? new Date(value) : null;
    if (!time || Number.isNaN(time.getTime())) {
      throw new BadRequestException('A time suggestion needs a time');
    }
    return time;
  }

  /** The provider's own record of a place: unknown or not a café, bar or restaurant is a 400. */
  private async publicPlace(placeId: string): Promise<Place> {
    let place: Place | null;
    try {
      place = await this.provider.details(placeId);
    } catch (err) {
      if (err instanceof PlacesUnavailableError) {
        throw new ServiceUnavailableException('Places are unavailable');
      }
      throw err;
    }
    if (!place || !(PLACE_TYPES as readonly string[]).includes(place.type)) {
      throw new BadRequestException('That is not a public place to meet');
    }
    return place;
  }

  private async bindingVenue(
    em: EntityManager,
    venueId: string | null,
  ): Promise<BindingVenue | null> {
    if (!venueId) return null;
    const [v] = await em.query<
      Array<{
        id: string;
        name: string;
        place_id: string | null;
        lat: number | null;
        lng: number | null;
      }>
    >(
      `SELECT "id", "name", "place_id",
              ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
         FROM "venues" WHERE "id" = $1`,
      [venueId],
    );
    if (!v) return null;
    return {
      id: v.id,
      name: v.name,
      placeId: v.place_id,
      location: { lat: Number(v.lat), lng: Number(v.lng) },
    };
  }

  private anyCommittedCheckIn(
    em: EntityManager,
    dateId: string,
  ): Promise<boolean> {
    return em.exists(DateArrivalCheckIn, {
      where: { dateId, committedAt: Not(IsNull()) },
    });
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

  private async pushSuggested(
    receiverId: string,
    senderId: string,
    dateId: string,
    deadline: Date,
  ): Promise<void> {
    const name = await this.nameOf(senderId);
    await this.safely(dateId, () =>
      this.dispatch.sendPush(
        receiverId,
        'date_change_suggested',
        { name, deadline: clockLabel(deadline, PUSH_TIME_ZONE) },
        { type: 'date_change_suggested', dateId },
      ),
    );
  }

  private async pushAgreed(
    senderId: string,
    agreedById: string,
    dateId: string,
  ): Promise<void> {
    const name = await this.nameOf(agreedById);
    await this.safely(dateId, () =>
      this.dispatch.sendPush(
        senderId,
        'date_change_agreed',
        { name },
        { type: 'date_change_agreed', dateId },
      ),
    );
  }

  private async nameOf(userId: string): Promise<string> {
    try {
      const profile = await this.profiles.findOne({ where: { userId } });
      return firstName(profile?.displayName ?? null);
    } catch {
      return '';
    }
  }

  private async rescheduleReminder(
    dateId: string,
    shiftMs: number,
  ): Promise<void> {
    await this.safely(dateId, () =>
      this.scheduler.rescheduleDateReminder(dateId, shiftMs),
    );
  }

  /** The write is committed: a failed push or reschedule is logged by type and never undoes or fails it. */
  private async safely(
    dateId: string,
    send: () => Promise<unknown>,
  ): Promise<void> {
    try {
      await send();
    } catch (error) {
      this.logger.warn(
        `Change side effect failed for date ${dateId}: ${
          error instanceof Error ? error.name : 'unknown error'
        }`,
      );
    }
  }
}
