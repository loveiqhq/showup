import { Injectable, NotFoundException } from '@nestjs/common';
import { DataSource, IsNull, Not } from 'typeorm';

import { CheckInsService } from '../check-ins/check-ins.service';
import { CheckInWindow } from '../check-ins/entities/check-in-window.entity';
import { CheckInVibe } from '../check-ins/util/check-in-flow';
import { DatesService } from '../dates/dates.service';
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
/** About 1 km north: far enough that About reads "~1 km away", near enough to be plausible. */
const PARTNER_OFFSET_LAT = 0.009;

/**
 * Debug-only helpers that let every Date screen state be walked on an emulator (Date 01,
 * SHOWUP-193): a date with a test match at a chosen time, a message from that match (state C),
 * and an end to it, so a test never gets stuck on a screen whose real exits are later tickets.
 * The controller refuses all of it in production.
 */
@Injectable()
export class DateDevService {
  constructor(
    private readonly db: DataSource,
    private readonly dates: DatesService,
    private readonly checkIns: CheckInsService,
    private readonly screen: DateScreenService,
  ) {}

  /** Ends any active date of the caller, then makes a confirmed one with Leonie in `minutes`. */
  async createDate(userId: string, minutes: number): Promise<void> {
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
    const date = await this.screen.activeDateRow(userId);
    if (!date) throw new NotFoundException('No active date');
    const partnerId = date.userAId === userId ? date.userBId : date.userAId;
    await this.dates.sendChatMessage(date.id, partnerId, reason, body);
  }

  /** Cancels every confirmed date of the caller's -- the dev escape from the Date screen. */
  async endDate(userId: string): Promise<void> {
    await this.db
      .getRepository(DateEntity)
      .createQueryBuilder()
      .update()
      .set({
        status: DateStatus.Cancelled,
        cancelledAt: new Date(),
        cancelledById: userId,
      })
      .where('(user_a_id = :id OR user_b_id = :id) AND status = :status', {
        id: userId,
        status: DateStatus.Confirmed,
      })
      .execute();
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

  /** Cafe Krone, made if missing. */
  private async venue(): Promise<string> {
    const [row] = await this.db.query<Array<{ id: string }>>(
      `INSERT INTO "venues" ("name", "address", "location")
       VALUES ($1, $2, ST_SetSRID(ST_MakePoint($3, $4), 4326)::geography)
       ON CONFLICT ("name") DO UPDATE SET "address" = EXCLUDED."address"
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
