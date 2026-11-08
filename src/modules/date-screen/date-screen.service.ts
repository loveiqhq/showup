import { Injectable } from '@nestjs/common';
import { DataSource, In, IsNull, Not } from 'typeorm';

import { CheckInWindow } from '../check-ins/entities/check-in-window.entity';
import { DateChatMessage } from '../dates/entities/date-chat-message.entity';
import { DateEntity } from '../dates/entities/date.entity';
import { DateStatus } from '../dates/util/date-lifecycle';
import { dateLocksUser } from '../dates/util/date-lock';
import { Profile } from '../profiles/entities/profile.entity';
import { SearchProfileDto } from '../search/dto/search.dto';
import { firstName } from '../search/util/search-profile';
import { SearchService } from '../search/search.service';
import { isTimeZone, meetDay } from '../search/util/search';
import { ActiveDateDto, EndedInChatDto } from './dto/active-date.dto';
import { endedOutcomeOf, toActiveDate, unreadOf } from './util/active-date';

/**
 * The Date screen's reads (Date 01, SHOWUP-193).
 *
 * THE ACTIVE DATE IS THE MATCHING LOCK. A date is active for a person while it is confirmed and
 * they have not reviewed it -- exactly `dateLocksUser`, the rule that already keeps them out of
 * Search. Being on the Date screen and being locked out of Search can therefore never disagree.
 */
@Injectable()
export class DateScreenService {
  constructor(
    private readonly db: DataSource,
    private readonly search: SearchService,
  ) {}

  /** The viewer's active date, newest first if (against the rules) there were two. */
  async activeDateRow(viewerId: string): Promise<DateEntity | null> {
    const rows = await this.db.getRepository(DateEntity).find({
      where: [
        { userAId: viewerId, status: DateStatus.Confirmed },
        { userBId: viewerId, status: DateStatus.Confirmed },
      ],
      order: { scheduledAt: 'DESC' },
    });
    return rows.find((d) => dateLocksUser(d, viewerId)) ?? null;
  }

  /** `GET /me/date`. Null when the viewer has no active date. */
  async activeDate(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
  ): Promise<ActiveDateDto | null> {
    const date = await this.activeDateRow(viewerId);
    if (!date) return null;
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const viewerIsA = date.userAId === viewerId;
    const partnerId = viewerIsA ? date.userBId : date.userAId;

    const [windows, viewerProfile, venue, meters, messages] = await Promise.all(
      [
        this.latestWindows([viewerId, partnerId]),
        this.db.getRepository(Profile).findOne({ where: { userId: viewerId } }),
        this.venueOf(date.venueId),
        this.metersBetween(viewerId, partnerId),
        this.db.getRepository(DateChatMessage).find({
          where: { dateId: date.id },
          order: { createdAt: 'ASC' },
        }),
      ],
    );
    const partner = await this.search.profileFor(
      partnerId,
      windows.get(partnerId) ?? null,
      meters ?? Number.NaN,
      date.scheduledAt,
      now,
      tz,
    );
    if (!partner) return null;
    const own = windows.get(viewerId);

    return toActiveDate({
      dateId: date.id,
      scheduledAt: date.scheduledAt,
      venue,
      partner,
      distanceMeters: meters,
      viewer: {
        motivation: own?.motivation ?? null,
        vibes: [...(own?.vibes ?? [])],
        interests: [...(viewerProfile?.interests ?? [])],
      },
      unread: unreadOf(
        messages.map((m) => ({
          senderId: m.senderId,
          reason: m.reason,
          body: m.body,
          createdAt: m.createdAt,
        })),
        viewerId,
        viewerIsA ? date.aChatReadAt : date.bChatReadAt,
      ),
      // Date 03 adds the arrival check-in store; until then nobody has checked in.
      checkedIn: false,
      chatTutorialSeen:
        (viewerIsA ? date.aChatTutorialAt : date.bChatTutorialAt) !== null,
      meetDay: meetDay(date.scheduledAt, now, tz),
      now,
    });
  }

  /**
   * With no active date: the newest date that ended in the chat whose outcome the viewer has still to
   * see (Date 02) -- how the match lands on H, and how the ender still sees G after a crash.
   */
  async endedOutcome(viewerId: string): Promise<EndedInChatDto | null> {
    const rows = await this.db.getRepository(DateEntity).find({
      where: [
        { userAId: viewerId, status: DateStatus.EndedInChat },
        { userBId: viewerId, status: DateStatus.EndedInChat },
      ],
      order: { endedAt: 'DESC' },
    });
    const row = rows.find((d) => endedOutcomeOf(d, viewerId, '') !== null);
    if (!row) return null;
    const partnerId = row.userAId === viewerId ? row.userBId : row.userAId;
    const [profile, photo] = await Promise.all([
      this.db.getRepository(Profile).findOne({ where: { userId: partnerId } }),
      this.search.firstPhotoUrl(partnerId),
    ]);
    const outcome = endedOutcomeOf(
      row,
      viewerId,
      firstName(profile?.displayName ?? null),
    );
    // The match's photo travels with the outcome: at the next launch no date is held to take it from,
    // and H drew an initial (the SHOWUP-194 review).
    if (outcome && photo) outcome.photoUrl = photo;
    return outcome;
  }

  /** `GET /me/date/profile`: the match's full profile, built as the queue builds it. */
  async partnerProfile(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
  ): Promise<SearchProfileDto | null> {
    const date = await this.activeDateRow(viewerId);
    if (!date) return null;
    const partnerId = date.userAId === viewerId ? date.userBId : date.userAId;
    const [windows, meters] = await Promise.all([
      this.latestWindows([partnerId]),
      this.metersBetween(viewerId, partnerId),
    ]);
    return this.search.profileFor(
      partnerId,
      windows.get(partnerId) ?? null,
      meters ?? Number.NaN,
      date.scheduledAt,
      now,
      zone,
    );
  }

  /**
   * Each person's latest COMMITTED Check-In window -- the one the date was matched from. Not only
   * an open one: a date can outlive the window it came from, and its motivation and vibes still
   * describe what the two people said they wanted.
   */
  private async latestWindows(
    ids: string[],
  ): Promise<Map<string, CheckInWindow>> {
    const rows = await this.db.getRepository(CheckInWindow).find({
      where: { userId: In(ids), committedAt: Not(IsNull()) },
      order: { committedAt: 'ASC' },
    });
    return new Map(rows.map((w) => [w.userId, w]));
  }

  private async venueOf(
    venueId: string | null,
  ): Promise<{ name: string; address: string | null } | null> {
    if (!venueId) return null;
    const rows = await this.db.query<
      Array<{ name: string; address: string | null }>
    >(`SELECT "name", "address" FROM "venues" WHERE "id" = $1`, [venueId]);
    return rows[0] ?? null;
  }

  /**
   * Between the two people's latest located Check-Ins, whatever their hours -- Search's own
   * distance only counts hours still open, and a date outlives those. Null when either has none.
   */
  private async metersBetween(a: string, b: string): Promise<number | null> {
    const rows = await this.db.query<Array<{ meters: number | null }>>(
      `SELECT ST_Distance(x."location", y."location") AS "meters"
         FROM (SELECT "location" FROM "check_ins" WHERE "user_id" = $1 AND "location" IS NOT NULL
                ORDER BY "availability_start" DESC LIMIT 1) x,
              (SELECT "location" FROM "check_ins" WHERE "user_id" = $2 AND "location" IS NOT NULL
                ORDER BY "availability_start" DESC LIMIT 1) y`,
      [a, b],
    );
    const m = rows[0]?.meters;
    return m == null ? null : Number(m);
  }
}
