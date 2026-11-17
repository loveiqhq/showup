import {
  ConflictException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { DataSource, IsNull, Not } from 'typeorm';

import { randomUUID } from 'crypto';

import { CheckInsService } from '../check-ins/check-ins.service';
import { CheckInWindow } from '../check-ins/entities/check-in-window.entity';
import { CheckInVibe } from '../check-ins/util/check-in-flow';
import { ArrivalCheckInsService } from '../dates/arrival-check-ins.service';
import { ArrivalEntry } from '../dates/util/arrival';
import { ChangeSuggestionsService } from '../dates/change-suggestions.service';
import { DateChangeService } from '../dates/date-change.service';
import { DatesService } from '../dates/dates.service';
import {
  ChangeEntry,
  ChangeKind,
  ChangeStatus,
  DateChangeSuggestion,
} from '../dates/entities/date-change-suggestion.entity';
import { isBindingVenue } from '../dates/util/change-suggestions';
import {
  PLACES_PROVIDER,
  type PlacesProvider,
} from '../places/places.provider';
import { DateEntity } from '../dates/entities/date.entity';
import { DateStatus } from '../dates/util/date-lifecycle';
import { ChatReason } from '../dates/util/pre-date-chat';
import { Profile } from '../profiles/entities/profile.entity';
import { InterestTag } from '../profiles/util/profile-details';
import { User, UserStatus } from '../users/entities/user.entity';
import { DateScreenService } from './date-screen.service';

/** The test match every developer's date is with. A phone no real person can hold. */
const PARTNER_PHONE = '+490000193193';
/** The Date screen reference's own venue, so a local date looks like the spec sheet. */
const VENUE = {
  name: 'Cafe Krone',
  address: 'Oranienstrasse 42, Kreuzberg',
  lat: 52.5016,
  lng: 13.4186,
};
/** Where the test match arrives when the date has no venue point. */
const BERLIN = { lat: 52.52, lng: 13.405 };
/** The zone the test match's slots are read in: the product's. */
const DEV_TZ = 'Europe/Berlin';
/** About 1 km north: far enough that About reads "~1 km away", near enough to be plausible. */
const PARTNER_OFFSET_LAT = 0.009;

/**
 * Debug-only helpers that let every Date screen state be walked on an emulator (Date 01,
 * SHOWUP-193): a date with a test match at a chosen time, a message from that match (state C),
 * and an end to it, so a test never gets stuck on a screen whose real exits are later tickets.
 * The controller refuses all of it in production.
 *
 * ONLY EVER THE TEST MATCH'S DATE. On a test server real people test the real app too: every helper
 * finds the caller's date with Leonie and nothing else, so a tester's real date is never ended, sent
 * a message as their match, or ended in the chat by a dev button.
 */
@Injectable()
export class DateDevService {
  constructor(
    private readonly db: DataSource,
    private readonly dates: DatesService,
    private readonly checkIns: CheckInsService,
    private readonly screen: DateScreenService,
    private readonly arrivals: ArrivalCheckInsService,
    private readonly changes: ChangeSuggestionsService,
    private readonly changeSlots: DateChangeService,
    @Inject(PLACES_PROVIDER) private readonly places: PlacesProvider,
  ) {}

  /**
   * Ends the caller's test date, if any, then makes a confirmed one with Leonie in `minutes`. Refused
   * while the caller has a date with a real person: the app holds one date at a time, and that one is
   * theirs to end in the app.
   */
  async createDate(userId: string, minutes: number): Promise<void> {
    const active = await this.screen.activeDateRow(userId);
    const testPartner = await this.testPartnerId();
    if (active && !this.isWith(active, userId, testPartner)) {
      throw new ConflictException(
        'You have a date with a real person; end it in the app first',
      );
    }
    await this.endDate(userId);
    const partnerId = await this.partnerFor(userId);
    const venueId = await this.venue();
    const repo = this.db.getRepository(DateEntity);
    await repo.save(
      repo.create({
        userAId: userId,
        userBId: partnerId,
        scheduledAt: new Date(Date.now() + minutes * 60_000),
        venueId,
        status: DateStatus.Confirmed,
      }),
    );
  }

  /** A message from the test match, through the real send path (the chat window applies). */
  async message(
    userId: string,
    reason: ChatReason | null,
    body: string,
  ): Promise<void> {
    const { date, partnerId } = await this.testDate(userId);
    await this.dates.sendChatMessage(date.id, partnerId, reason, body);
  }

  /** The test match ends the caller's date in the chat, through the real path (Date 02). */
  async endInChat(userId: string): Promise<void> {
    const { date, partnerId } = await this.testDate(userId);
    await this.dates.endInChat(date.id, partnerId);
  }

  /**
   * The test match arrives (Date 03): stores and commits her check-in through the real service, at
   * the venue's point when it has one. Already arrived answers quietly, with no second push.
   */
  async partnerArrives(userId: string): Promise<void> {
    const { date, partnerId } = await this.testDate(userId);
    const rows = await this.arrivals.rowsFor(date.id);
    if (rows.some((r) => r.userId === partnerId && r.committedAt)) return;
    const [point] = date.venueId
      ? await this.db.query<Array<{ lat: number; lng: number }>>(
          `SELECT ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
             FROM "venues" WHERE "id" = $1 AND "location" IS NOT NULL`,
          [date.venueId],
        )
      : [];
    await this.arrivals.create(date.id, partnerId, {
      latitude: point?.lat ?? BERLIN.lat,
      longitude: point?.lng ?? BERLIN.lng,
      entry: ArrivalEntry.DateScreen,
      clientId: randomUUID(),
    });
    await this.arrivals.commit(date.id, partnerId);
  }

  /**
   * The test match suggests a change for the caller's test date, through the real path (Date 05), so
   * the push, the event and the one-open-per-kind replacement are exactly production's.
   * Time: the meet time + `minutesLater`, moved to the first listed slot at or after it when the
   * caller's free hours have one, else used as is (the slot check is skipped for it, so a test date
   * with no hours can still walk the cards). Place: the first place the provider lists near the
   * meet place that is not the meet place itself.
   */
  async suggestChange(
    userId: string,
    kind: ChangeKind,
    minutesLater: number,
  ): Promise<void> {
    const { date, partnerId } = await this.testDate(userId);
    const clientId = randomUUID();
    if (kind === ChangeKind.Time) {
      const wanted = new Date(
        date.scheduledAt.getTime() + minutesLater * 60_000,
      );
      const slots = await this.changeSlots.matchSlots(date, partnerId, DEV_TZ);
      const slot = slots.find((s) => s.getTime() >= wanted.getTime());
      await this.changes.suggest(
        date.id,
        partnerId,
        {
          kind,
          time: (slot ?? wanted).toISOString(),
          entry: ChangeEntry.ChatChip,
          clientId,
          tz: DEV_TZ,
        },
        new Date(),
        { skipSlotCheck: slot === undefined },
      );
      return;
    }
    const [venue] = date.venueId
      ? await this.db.query<
          Array<{
            name: string;
            place_id: string | null;
            lat: number;
            lng: number;
          }>
        >(
          `SELECT "name", "place_id", ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
             FROM "venues" WHERE "id" = $1 AND "location" IS NOT NULL`,
          [date.venueId],
        )
      : [];
    const origin = venue
      ? { lat: Number(venue.lat), lng: Number(venue.lng) }
      : BERLIN;
    const binding = venue
      ? { name: venue.name, placeId: venue.place_id, location: origin }
      : null;
    const other = (await this.places.nearby(origin)).find(
      (p) => !isBindingVenue(binding, p),
    );
    if (!other) throw new NotFoundException('No other place to suggest');
    await this.changes.suggest(date.id, partnerId, {
      kind,
      placeId: other.placeId,
      entry: ChangeEntry.ChatChip,
      fromSearch: false,
      clientId,
    });
  }

  /** The test match agrees to the caller's open suggestion (earliest deadline first), through the real path. */
  async agreeChange(userId: string): Promise<void> {
    const { date, partnerId } = await this.testDate(userId);
    const row = await this.db.getRepository(DateChangeSuggestion).findOne({
      where: {
        dateId: date.id,
        senderId: userId,
        status: ChangeStatus.Pending,
      },
      order: { deadline: 'ASC', kind: 'ASC' },
    });
    if (!row) throw new NotFoundException('No open suggestion to agree to');
    await this.changes.agree(date.id, row.id, partnerId, { surface: 'thread' });
  }

  /** Makes every open suggestion on the caller's test date due now, then runs the expiry once. */
  async expireChanges(userId: string): Promise<void> {
    const { date } = await this.testDate(userId);
    const now = new Date();
    await this.db.query(
      `UPDATE "date_change_suggestions" SET "deadline" = $2
        WHERE "date_id" = $1 AND "status" = 'pending'`,
      [date.id, now],
    );
    await this.changes.expireDue(now);
  }

  /** Cancels the caller's confirmed dates with the test match -- the dev escape from the Date screen. */
  async endDate(userId: string): Promise<void> {
    const partnerId = await this.testPartnerId();
    if (!partnerId) return;
    await this.db
      .getRepository(DateEntity)
      .createQueryBuilder()
      .update()
      .set({
        status: DateStatus.Cancelled,
        cancelledAt: new Date(),
        cancelledById: userId,
      })
      .where(
        '((user_a_id = :id AND user_b_id = :partner) OR (user_a_id = :partner AND user_b_id = :id)) AND status = :status',
        { id: userId, partner: partnerId, status: DateStatus.Confirmed },
      )
      .execute();
  }

  /** The caller's active date with the test match; 404 when their active date (if any) is a real one. */
  private async testDate(
    userId: string,
  ): Promise<{ date: DateEntity; partnerId: string }> {
    const [date, partnerId] = await Promise.all([
      this.screen.activeDateRow(userId),
      this.testPartnerId(),
    ]);
    if (!date || !partnerId || !this.isWith(date, userId, partnerId)) {
      throw new NotFoundException('No test date');
    }
    return { date, partnerId };
  }

  /** The test match's user id, or null before the first test date made her. */
  private async testPartnerId(): Promise<string | null> {
    const partner = await this.db
      .getRepository(User)
      .findOne({ where: { phone: PARTNER_PHONE } });
    return partner?.id ?? null;
  }

  private isWith(
    date: DateEntity,
    userId: string,
    partnerId: string | null,
  ): boolean {
    const other = date.userAId === userId ? date.userBId : date.userAId;
    return partnerId !== null && other === partnerId;
  }

  /**
   * Leonie, made to overlap the caller: the same motivation, two of their vibes and interests,
   * a located Check-In about 1 km from theirs. Re-made each time, so she follows the caller's
   * latest Check-In.
   */
  private async partnerFor(userId: string): Promise<string> {
    const users = this.db.getRepository(User);
    const partner =
      (await users.findOne({ where: { phone: PARTNER_PHONE } })) ??
      (await users.save(
        users.create({ phone: PARTNER_PHONE, status: UserStatus.Active }),
      ));

    const [own, ownProfile] = await Promise.all([
      this.db.getRepository(CheckInWindow).findOne({
        where: { userId, committedAt: Not(IsNull()) },
        order: { committedAt: 'DESC' },
      }),
      this.db.getRepository(Profile).findOne({ where: { userId } }),
    ]);
    const vibes = unique<CheckInVibe>([
      ...(own?.vibes ?? []).slice(0, 2),
      'nature_walk',
    ]);
    const interests = unique<InterestTag>([
      ...(ownProfile?.interests ?? []).slice(0, 2),
      'brunch',
    ]);

    const profiles = this.db.getRepository(Profile);
    const born = new Date();
    born.setUTCFullYear(born.getUTCFullYear() - 28);
    born.setUTCDate(born.getUTCDate() - 30);
    await profiles.save({
      ...((await profiles.findOne({ where: { userId: partner.id } })) ??
        profiles.create({ userId: partner.id })),
      displayName: 'Leonie',
      dateOfBirth: born.toISOString().slice(0, 10),
      isVisible: true,
      hiddenFields: [],
      interests,
    });

    const windows = this.db.getRepository(CheckInWindow);
    await windows.delete({ userId: partner.id });
    await windows.save(
      windows.create({
        userId: partner.id,
        closesAt: new Date(Date.now() + 48 * 3_600_000),
        committedAt: new Date(),
        motivation: own?.motivation ?? 'long_term',
        vibes,
        district: 'Neukölln',
      }),
    );

    const [here] = await this.db.query<Array<{ lat: number; lng: number }>>(
      `SELECT ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
         FROM "check_ins" WHERE "user_id" = $1 AND "location" IS NOT NULL
        ORDER BY "availability_start" DESC LIMIT 1`,
      [userId],
    );
    await this.db.query(`DELETE FROM "check_ins" WHERE "user_id" = $1`, [
      partner.id,
    ]);
    await this.checkIns.create(partner.id, {
      availabilityStart: new Date(Date.now() + 60_000).toISOString(),
      availabilityEnd: new Date(Date.now() + 6 * 3_600_000).toISOString(),
      preparationMinutes: 20,
      latitude: (here?.lat ?? VENUE.lat) + PARTNER_OFFSET_LAT,
      longitude: here?.lng ?? VENUE.lng,
    });
    return partner.id;
  }

  /**
   * Cafe Krone, made if missing. Found by name, not by an ON CONFLICT: since Date 05 a venue name is
   * no longer unique (two Google places can share one), so there is no constraint to conflict on.
   */
  private async venue(): Promise<string> {
    const [existing] = await this.db.query<Array<{ id: string }>>(
      // Wrapped in a CTE so the driver returns rows: a bare UPDATE ... RETURNING comes back as
      // [rows, count] from pg.
      `WITH "upd" AS (
         UPDATE "venues" SET "address" = $2 WHERE "id" =
           (SELECT "id" FROM "venues" WHERE "name" = $1 ORDER BY "created_at" LIMIT 1)
         RETURNING "id")
       SELECT "id" FROM "upd"`,
      [VENUE.name, VENUE.address],
    );
    if (existing) return existing.id;
    const [row] = await this.db.query<Array<{ id: string }>>(
      `INSERT INTO "venues" ("name", "address", "location")
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography)
       RETURNING "id"`,
      [VENUE.name, VENUE.address, VENUE.lng, VENUE.lat],
    );
    return row.id;
  }
}

function unique<T>(xs: T[]): T[] {
  return [...new Set(xs)];
}

/** Exported for the e2e, which cleans the test match up. */
export const DEV_PARTNER_PHONE = PARTNER_PHONE;
