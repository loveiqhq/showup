import {
  BadRequestException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectDataSource } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { DataSource, In, IsNull, LessThan, MoreThan, Not } from 'typeorm';

import { CheckInStatus } from '../check-ins/entities/check-in.entity';
import { CheckInWindow } from '../check-ins/entities/check-in-window.entity';
import { FilterSettings } from '../check-ins/entities/filter-settings.entity';
import {
  checkInGenderDefault,
  FILTER_AGE_MIN,
  FILTER_DISTANCE_DEFAULT,
} from '../check-ins/util/check-in-flow';
import { AnalyticsService } from '../analytics/analytics.service';
import {
  likeExpiredEvent,
  likeWithdrawnEvent,
  SearchFeedItem,
  searchFeedRankedEvent,
} from '../analytics/events/server-events';
import { DatesService } from '../dates/dates.service';
import { DateStatus } from '../dates/util/date-lifecycle';
import { Like } from '../matching/entities/like.entity';
import {
  INELIGIBLE_USER_STATUSES,
  LikeCloseReason,
  LikeStatus,
  MatchStatus,
} from '../matching/util/match';
import { Profile } from '../profiles/entities/profile.entity';
import {
  PhotoModerationStatus,
  ProfilePhoto,
} from '../profiles/entities/profile-photo.entity';
import {
  MediaModerationStatus,
  ProfileMedia,
} from '../profiles/entities/profile-media.entity';
import { ProfilePrompt } from '../profiles/entities/profile-prompt.entity';
import { STORAGE } from '../profiles/storage/storage.interface';
import type { StorageService } from '../profiles/storage/storage.interface';
import { ageFromDateOfBirth } from '../profiles/util/age';
import { isFieldHidden } from '../profiles/util/hidden-fields';
import { SafetyService } from '../safety/safety.service';
import { HIDDEN_FROM_DISCOVERY, isDiscoverable } from '../safety/util/safety';
import { PlanTier, User } from '../users/entities/user.entity';
import {
  CreateSearchPassDto,
  LikeReceivedDto,
  LikesReceivedDto,
  SearchAvailabilityDto,
  SearchGridDto,
  SearchMediaDto,
  SearchProfileDto,
  SearchQueueDto,
  SearchViewerDto,
} from './dto/search.dto';
import { SearchPass, SearchPassType } from './entities/search-pass.entity';
import {
  Availability,
  availabilityFrom,
  AvailabilityOf,
  closeReasonFor,
  compareClosedLikes,
  compareOpenLikes,
  dropAbsent,
} from './util/likes';
import { buildSearchProfile, firstName } from './util/search-profile';
import { earliestMeetAt, HourSpan, isTimeZone, meetDay } from './util/search';
import type { RankerConfig } from './ranker/ranker-config';
import { RankerConfigService } from './ranker/ranker-config.service';
import {
  ageReach,
  Boost,
  combinedReach,
  distanceReach,
  motivationsCompatible,
  multiplierOf,
  orderFeed,
  overlapAhead,
  Placed,
  remainingHours,
  Scored,
  scoreOf,
} from './ranker/ranker';

export { Availability };
export type { AvailabilityOf };

/** How many profiles of the queue one response carries: the one on screen and the next two. */
export const SEARCH_QUEUE_PAGE = 3;

/**
 * How many cards the Premium grid carries at most. The grid is the queue (Search 02) -- every card at
 * once, no paging -- and every card carries its whole profile, so a bound is the response's size.
 * It is the availability re-check's own bound (`SearchAvailabilityRequestDto`, 100 ids), so every
 * card a grid shows can be re-checked in the one call a return makes. A queue longer than this is
 * listed for product (it has no ticketed answer); the first 100 in queue order are the grid.
 */
export const SEARCH_GRID_LIMIT = 100;

/** One person the viewer could meet, before their profile is loaded. */
interface Candidate {
  userId: string;
  /** Their active Check-In window -- null only for hours made without one (Epic 4's check-ins). */
  window: CheckInWindow | null;
  profile: Profile;
  meters: number;
  meetAt: Date;
}

/** An open like the viewer received, with what its card needs. */
interface OpenLike {
  likeId: string;
  senderId: string;
  message: string | null;
  meetAt: Date;
  meters: number;
}

/** A closed like the viewer has not been shown yet. */
interface ClosedLike {
  likeId: string;
  senderId: string;
  reason: LikeCloseReason;
  closedAt: Date;
  /** Closed by a match the VIEWER made: never shown, only marked seen. */
  ownMatch: boolean;
}

/** A user's Search filters, Check-In 05b's two switches included. */
type SearchFilters = Pick<
  FilterSettings,
  | 'meetGender'
  | 'minAge'
  | 'maxAge'
  | 'distanceKm'
  | 'ageDealbreaker'
  | 'distanceDealbreaker'
>;

interface ViewerState {
  window: CheckInWindow;
  spans: HourSpan[];
  lat: number;
  lng: number;
  profile: Profile;
  filters: SearchFilters;
  isPremium: boolean;
  /** The Check-In before this one: a `Not now` made in it lowers that person now (x0.7). */
  previousWindowId: string | null;
}

/** One load of the feed, ranked: what the queue and the grid serve, and its id. */
interface RankedFeed {
  feedRequestId: string;
  feed: Placed<Candidate>[];
  /** After the filters, before the gate: held-back profiles count. */
  eligibleCount: number;
}

/** The most items one `search_feed_ranked` carries: the most a single load can put on screen. */
const FEED_EVENT_ITEMS = 100;

/**
 * The Search tab's server side (SHOWUP-184): the feed, the profile payload, `Not now` /
 * `Not interested`, the coach flag and the open-likes count.
 *
 * THE FEED IS RANKED BY MATCHING 01 (SHOWUP-190), re-ranked on every load: the ticket's filters
 * 1-12, one weighted score, the ordering multipliers capped at x1.5, the fit gate, the Premium
 * liked-you block and the random picks -- every number from the versioned `RankerConfig`. Each load
 * records `search_feed_ranked` and returns its `feedRequestId`. The rules themselves are plain
 * functions in `ranker/ranker.ts`; this class gathers their inputs.
 *
 * Beyond the ticket's twelve, Search 01's own rules still hold: never someone the viewer already
 * liked, someone hidden from discovery, or a suspended or deleted account.
 */
@Injectable()
export class SearchService {
  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @Inject(STORAGE) private readonly storage: StorageService,
    private readonly safety: SafetyService,
    private readonly dates: DatesService,
    private readonly analytics: AnalyticsService,
    private readonly config: ConfigService,
    private readonly rankerConfig: RankerConfigService,
  ) {}

  /** The 1-up feed, and the first profiles of it in full. `zone` is the viewer's IANA zone. */
  async queue(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
    limit: number = SEARCH_QUEUE_PAGE,
  ): Promise<SearchQueueDto> {
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const [ranked, viewer] = await Promise.all([
      this.rankFeed(viewerId, now, 'one_up'),
      this.viewerTraits(viewerId, now),
    ]);
    const profiles = await this.loadProfiles(
      ranked.feed.slice(0, limit).map((p) => p.candidate),
      now,
      tz,
    );
    return {
      resultsCount: ranked.eligibleCount,
      profiles,
      viewer,
      feedRequestId: ranked.feedRequestId,
    };
  }

  /**
   * The Premium grid (Search 02): the same feed, the same rules and order, every card at once with
   * whether that person liked the viewer -- the liked-you block first on Premium. On load it holds
   * only people still available -- the filters admit nobody else -- so no card is closed on arrival.
   */
  async grid(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
  ): Promise<SearchGridDto> {
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const [ranked, viewer] = await Promise.all([
      this.rankFeed(viewerId, now, 'grid'),
      this.viewerTraits(viewerId, now),
    ]);
    const top = ranked.feed.slice(0, SEARCH_GRID_LIMIT);
    const profiles = await this.loadProfiles(
      top.map((p) => p.candidate),
      now,
      tz,
    );
    return {
      resultsCount: ranked.eligibleCount,
      cards: profiles.map((profile, i) => ({
        profile,
        likedYou: top[i].likedYou,
      })),
      viewer,
      feedRequestId: ranked.feedRequestId,
    };
  }

  /**
   * The availability re-check (Search 02 ⑲, reused by Search 03): one call for every card on
   * screen, read on each return from a profile. Per id `available` with the earliest hour still
   * ahead, `matched` or `no_overlap`, in the order asked.
   *
   * A like FROM one of them that can no longer become a date is closed here, at once, with that
   * reason: the card closes in front of the user, so it counts as seen, and the badge read next
   * agrees with what they see instead of waiting for the minute job. One already closed is marked
   * seen the same way.
   */
  async availability(
    viewerId: string,
    userIds: string[],
    zone: string | undefined,
    now: Date = new Date(),
  ): Promise<SearchAvailabilityDto[]> {
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const ids = [...new Set(userIds)].filter((id) => id !== viewerId);
    const statuses = await this.statusAmong(viewerId, ids, now);
    const gone = [...statuses]
      .filter(([, s]) => s.status !== Availability.Available)
      .map(([senderId, s]) => ({ senderId, status: s.status }));
    if (gone.length > 0) await this.closeReceivedFrom(viewerId, gone, now, now);
    return userIds.map((userId) => {
      const s = statuses.get(userId) ?? {
        status: Availability.NoOverlap,
        meetAt: null,
      };
      return s.status === Availability.Available && s.meetAt
        ? {
            userId,
            status: s.status,
            meetAt: s.meetAt.toISOString(),
            meetDay: meetDay(s.meetAt, now, tz),
          }
        : { userId, status: s.status };
    });
  }

  /** One person's availability for the viewer -- what a like is checked against when it lands. */
  async availabilityOf(
    viewerId: string,
    targetId: string,
    now: Date = new Date(),
  ): Promise<AvailabilityOf> {
    return (
      (await this.statusAmong(viewerId, [targetId], now)).get(targetId) ?? {
        status: Availability.NoOverlap,
        meetAt: null,
      }
    );
  }

  /**
   * The Likes tab (Search 03), read once per visit: the open likes the viewer received, earliest
   * meet time first, then the closed ones not yet seen on an earlier visit -- which this read marks
   * seen ("in the payload until the visit after the one where it was first shown").
   *
   * An open like found with no time left, or whose sender has a date, is closed HERE first: "a like
   * whose overlap ran out before the visit shows as an Out of time card", not as an open one the
   * minute job has not reached yet.
   *
   * A like closed because the VIEWER matched is never shown -- "Too late, {name} has a date with
   * someone else" would be untrue. It is marked seen and left out.
   */
  async likesReceived(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
  ): Promise<LikesReceivedDto> {
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const { open, gone } = await this.openReceived(viewerId, now);
    if (gone.length > 0)
      await this.closeReceivedFrom(viewerId, gone, now, null);

    const closed = await this.closedUnseen(viewerId);
    const shown = closed.filter((c) => !c.ownMatch);
    if (closed.length > 0) {
      await this.db.query(
        `UPDATE "likes" SET "closed_seen_at" = $2, "updated_at" = now()
          WHERE "id" = ANY($1::uuid[]) AND "closed_seen_at" IS NULL`,
        [closed.map((c) => c.likeId), now],
      );
    }

    const openIds = open.map((o) => o.senderId);
    const shownIds = shown.map((c) => c.senderId);
    const [profiles, windows, firstPhotos, viewer] = await Promise.all([
      this.db
        .getRepository(Profile)
        .find({ where: { userId: In([...openIds, ...shownIds]) } }),
      this.activeWindowsOf(openIds, now),
      this.firstPhotoUrls(shownIds),
      this.viewerTraits(viewerId, now),
    ]);
    const profileById = new Map(profiles.map((p) => [p.userId, p]));

    const candidates: Candidate[] = [...open]
      .sort(compareOpenLikes)
      .flatMap((o) => {
        const profile = profileById.get(o.senderId);
        return profile
          ? [
              {
                userId: o.senderId,
                window: windows.get(o.senderId) ?? null,
                profile,
                meters: o.meters,
                meetAt: o.meetAt,
              },
            ]
          : [];
      });
    const messageOf = new Map(open.map((o) => [o.senderId, o.message]));
    const openCards: LikeReceivedDto[] = (
      await this.loadProfiles(candidates, now, tz)
    ).map((p) =>
      dropAbsent({
        userId: p.userId,
        name: p.name,
        age: p.age,
        photo: p.photos[0],
        message: messageOf.get(p.userId) ?? undefined,
        profile: p,
      }),
    );

    const closedCards: LikeReceivedDto[] = [...shown]
      .sort(compareClosedLikes)
      .flatMap((c) => {
        const profile = profileById.get(c.senderId);
        if (!profile) return [];
        const photo = firstPhotos.get(c.senderId);
        return [
          dropAbsent({
            userId: c.senderId,
            name: firstName(profile.displayName),
            // Hidden means not sent, here as in the profile payload.
            age: isFieldHidden(profile.hiddenFields, 'age')
              ? undefined
              : (ageFromDateOfBirth(profile.dateOfBirth) ?? undefined),
            photo: photo ? { url: photo } : undefined,
            closed: c.reason,
          }),
        ];
      });
    return {
      openCount: openCards.length,
      closedCount: closedCards.length,
      cards: [...openCards, ...closedCards],
      viewer,
    };
  }

  /** The viewer's own vibes and interests -- empty lists, never absent, when there are none. */
  private async viewerTraits(
    viewerId: string,
    now: Date,
  ): Promise<SearchViewerDto> {
    const [window, profile] = await Promise.all([
      this.activeWindow(viewerId, now),
      this.db.getRepository(Profile).findOne({ where: { userId: viewerId } }),
    ]);
    return {
      vibes: window?.vibes ?? [],
      interests: profile?.interests ?? [],
    };
  }

  /**
   * `Not now` or `Not interested`. Idempotent: a retried tap lands on the row already there.
   *
   * `not_now` is stored against the viewer's current Check-In, so the next one brings the profile
   * back without any expiry job. With no active Check-In there is no Search to say it on.
   */
  async pass(
    viewerId: string,
    dto: CreateSearchPassDto,
    now: Date = new Date(),
  ): Promise<void> {
    if (dto.targetUserId === viewerId) {
      throw new BadRequestException('You cannot pass on yourself');
    }
    const target = await this.db
      .getRepository(User)
      .exists({ where: { id: dto.targetUserId } });
    if (!target) throw new NotFoundException('User not found');

    let windowId: string | null = null;
    if (dto.passType === SearchPassType.NotNow) {
      const window = await this.activeWindow(viewerId, now);
      if (!window) {
        throw new BadRequestException('Not now needs an active Check-In');
      }
      windowId = window.id;
    }
    // ON CONFLICT with no target: either partial unique index is "the same decision again".
    await this.db.query(
      `INSERT INTO "search_passes" ("viewer_id", "target_id", "pass_type", "window_id")
       VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING`,
      [viewerId, dto.targetUserId, dto.passType, windowId],
    );
    // `Not interested` on someone who liked the viewer DECLINES their like, for good (Search 02,
    // 03). A decision, not a closing: no reason, no `like_withdrawn`, and the liker is not told.
    // `Not now` leaves it open -- "their like stays open until it closes".
    if (dto.passType === SearchPassType.NotInterested) {
      await this.db.query(
        `UPDATE "likes" SET "status" = $3, "updated_at" = now()
          WHERE "sender_id" = $1 AND "receiver_id" = $2 AND "status" = $4`,
        [dto.targetUserId, viewerId, LikeStatus.Declined, LikeStatus.Active],
      );
    }
  }

  /** `Don't show again` on the actions coach -- the only act that sets the flag. */
  async hideCoach(userId: string): Promise<void> {
    await this.db
      .getRepository(User)
      .update({ id: userId }, { searchCoachHidden: true });
  }

  /**
   * The open likes the user has received: the Likes tab badge and the Search likes chip, which
   * must always show the same number (epic rule 10) -- and the open cards on the Likes list, which
   * is why both come from [openReceived] ("The Likes badge equals the open cards on the list"). A
   * like with no time left is not open, even before the job closes it.
   */
  async likesOpenCount(
    userId: string,
    now: Date = new Date(),
  ): Promise<number> {
    return (await this.openReceived(userId, now)).open.length;
  }

  /**
   * The active likes the viewer received, split into those that can still become a date and those
   * that cannot (about to close, with the reason their status gives).
   *
   * Open = from someone who can still be met: not blocked either way, not an account that is gone
   * or hidden, not someone the viewer marked `Not interested` (that declines the like now; the check
   * keeps one from before that), with an hour both Check-Ins still hold, and with a place to measure
   * the distance from -- which a committed Check-In always has.
   */
  private async openReceived(
    viewerId: string,
    now: Date,
  ): Promise<{
    open: OpenLike[];
    gone: Array<{ senderId: string; status: Availability }>;
  }> {
    const blocked = await this.safety.blockedUserIdsFor(viewerId);
    const rows = await this.db.query<
      Array<{ id: string; sender_id: string; message: string | null }>
    >(
      `SELECT l."id", l."sender_id", l."message"
         FROM "likes" l
         JOIN "users" u ON u."id" = l."sender_id"
        WHERE l."receiver_id" = $1
          AND l."status" = $2
          AND u."status" <> ALL($3::"users_status_enum"[])
          AND u."moderation_standing" <> ALL($4::"moderation_standing_enum"[])
          AND l."sender_id" <> ALL($5::uuid[])
          AND NOT EXISTS (
            SELECT 1 FROM "search_passes" p
             WHERE p."viewer_id" = $1 AND p."target_id" = l."sender_id"
               AND p."pass_type" = 'not_interested'
          )`,
      [
        viewerId,
        LikeStatus.Active,
        INELIGIBLE_USER_STATUSES,
        HIDDEN_FROM_DISCOVERY,
        blocked,
      ],
    );
    if (rows.length === 0) return { open: [], gone: [] };
    const senders = rows.map((r) => r.sender_id);
    const [statuses, meters] = await Promise.all([
      this.statusAmong(viewerId, senders, now),
      this.metersFrom(viewerId, senders, now),
    ]);
    const open: OpenLike[] = [];
    const gone: Array<{ senderId: string; status: Availability }> = [];
    for (const r of rows) {
      const s = statuses.get(r.sender_id);
      if (!s || s.status !== Availability.Available || !s.meetAt) {
        gone.push({
          senderId: r.sender_id,
          status: s?.status ?? Availability.NoOverlap,
        });
        continue;
      }
      const m = meters.get(r.sender_id);
      if (m == null) continue;
      open.push({
        likeId: r.id,
        senderId: r.sender_id,
        message: r.message,
        meetAt: s.meetAt,
        meters: m,
      });
    }
    return { open, gone };
  }

  /**
   * Closes the active likes FROM these people TO the viewer, each with the reason its status gives
   * (`matched` · `no_overlap`), firing `like_withdrawn` once per like closed. With `seenAt` -- the
   * viewer is shown the card closed right now (the re-check, a refused confirm) -- likes from them
   * that were already closed are marked seen at the same moment.
   */
  async closeReceivedFrom(
    viewerId: string,
    from: Array<{ senderId: string; status: Availability }>,
    now: Date,
    seenAt: Date | null,
  ): Promise<void> {
    for (const reason of [LikeCloseReason.Matched, LikeCloseReason.NoOverlap]) {
      const senders = from
        .filter((f) => closeReasonFor(f.status) === reason)
        .map((f) => f.senderId);
      if (senders.length === 0) continue;
      const likes = await this.db.query<Array<{ id: string }>>(
        `SELECT "id" FROM "likes"
          WHERE "receiver_id" = $1 AND "sender_id" = ANY($2::uuid[]) AND "status" = $3`,
        [viewerId, senders, LikeStatus.Active],
      );
      await this.closeLikes(
        likes.map((l) => l.id),
        reason,
        null,
        now,
        seenAt,
      );
    }
    if (seenAt && from.length > 0) {
      await this.db.query(
        `UPDATE "likes" SET "closed_seen_at" = $3, "updated_at" = now()
          WHERE "receiver_id" = $1 AND "sender_id" = ANY($2::uuid[])
            AND "status" = $4 AND "closed_seen_at" IS NULL`,
        [viewerId, from.map((f) => f.senderId), seenAt, LikeStatus.Withdrawn],
      );
    }
  }

  /**
   * Closes open likes without a decision -- state-guarded, so a like already closed is left alone
   * and a retry closes nothing twice -- and fires `like_withdrawn` once per like actually closed,
   * for its sender. The one closing path for the minute job, the re-check, the Likes list and a like
   * that lands on someone no longer available.
   */
  async closeLikes(
    likeIds: string[],
    reason: LikeCloseReason,
    matchId: string | null,
    now: Date,
    seenAt: Date | null = null,
  ): Promise<number> {
    if (likeIds.length === 0) return 0;
    // A CTE so the driver hands back the rows: a bare UPDATE ... RETURNING comes back as a
    // [rows, count] pair from `query`.
    const closed = await this.db.query<
      Array<{ senderId: string; receiverId: string; openedAt: Date }>
    >(
      `WITH closed AS (
         UPDATE "likes"
            SET "status" = $6, "closed_reason" = $2::"likes_closed_reason_enum",
                "closed_at" = $3, "closed_seen_at" = $4, "match_id" = $5, "updated_at" = now()
          WHERE "id" = ANY($1::uuid[]) AND "status" = $7
          RETURNING "sender_id" AS "senderId", "receiver_id" AS "receiverId",
                    "opened_at" AS "openedAt"
       ) SELECT * FROM closed`,
      [
        likeIds,
        reason,
        now,
        seenAt,
        matchId,
        LikeStatus.Withdrawn,
        LikeStatus.Active,
      ],
    );
    for (const c of closed) {
      void this.analytics.trackServerEvent({
        userId: c.senderId,
        ...likeWithdrawnEvent({ matchId, reason }),
      });
    }
    await this.reportExpired(closed, reason, now);
    return closed.length;
  }

  /**
   * `like_expired` for likes just closed unanswered: `target_matched` when the person liked has a
   * match; `check_in_ended` when either person's hours are over; otherwise `out_of_time`, the shared
   * hours ran out. Not when the SENDER's own match closed it -- their withdrawal, not the target
   * leaving it unanswered -- and never for a declined like, which does not close here.
   */
  private async reportExpired(
    closed: Array<{ senderId: string; receiverId: string; openedAt: Date }>,
    reason: LikeCloseReason,
    now: Date,
  ): Promise<void> {
    if (closed.length === 0) return;
    const people = [
      ...new Set(closed.flatMap((c) => [c.senderId, c.receiverId])),
    ];
    const [withMatch, spans] = await Promise.all([
      reason === LikeCloseReason.Matched
        ? this.db
            .query<Array<{ user_id: string }>>(
              `SELECT "user_a_id" AS "user_id" FROM "matches"
                WHERE "status" = $2 AND "user_a_id" = ANY($1::uuid[])
               UNION
               SELECT "user_b_id" FROM "matches"
                WHERE "status" = $2 AND "user_b_id" = ANY($1::uuid[])`,
              [people, MatchStatus.Active],
            )
            .then((r) => new Set(r.map((x) => x.user_id)))
        : new Set<string>(),
      reason === LikeCloseReason.NoOverlap
        ? this.liveSpansOf(people, now)
        : new Map<string, HourSpan[]>(),
    ]);
    for (const c of closed) {
      let expired: 'check_in_ended' | 'target_matched' | 'out_of_time' | null;
      if (reason === LikeCloseReason.Matched) {
        expired =
          !withMatch.has(c.senderId) && withMatch.has(c.receiverId)
            ? 'target_matched'
            : null;
      } else {
        expired =
          (spans.get(c.senderId)?.length ?? 0) === 0 ||
          (spans.get(c.receiverId)?.length ?? 0) === 0
            ? 'check_in_ended'
            : 'out_of_time';
      }
      if (!expired) continue;
      void this.analytics.trackServerEvent({
        userId: c.senderId,
        ...likeExpiredEvent({
          hoursOpen:
            (now.getTime() - new Date(c.openedAt).getTime()) / 3_600_000,
          reason: expired,
        }),
      });
    }
  }

  /** Whether the viewer can still meet each of these people -- `availabilityFrom` decides. */
  private async statusAmong(
    viewerId: string,
    ids: string[],
    now: Date,
  ): Promise<Map<string, AvailabilityOf>> {
    const out = new Map<string, AvailabilityOf>();
    if (ids.length === 0) return out;
    const [spans, matched, locked, blocked, users, profiles] =
      await Promise.all([
        this.liveSpansOf([viewerId, ...ids], now),
        this.matchedInWindow(ids, now),
        this.dates.lockedUserIdsAmong(ids).then((l) => new Set(l)),
        this.safety.blockedUserIdsFor(viewerId).then((b) => new Set(b)),
        this.db.getRepository(User).find({ where: { id: In(ids) } }),
        this.db.getRepository(Profile).find({ where: { userId: In(ids) } }),
      ]);
    const userById = new Map(users.map((u) => [u.id, u]));
    const profileById = new Map(profiles.map((p) => [p.userId, p]));
    const mine = spans.get(viewerId) ?? [];
    for (const id of ids) {
      const user = userById.get(id);
      const profile = profileById.get(id);
      const reachable =
        user != null &&
        profile != null &&
        profile.isVisible &&
        !INELIGIBLE_USER_STATUSES.includes(user.status) &&
        isDiscoverable(user.moderationStanding) &&
        isDiscoverable(profile.moderationStanding) &&
        !blocked.has(id);
      out.set(
        id,
        availabilityFrom({
          reachable,
          hasDate: matched.has(id) || locked.has(id),
          meetAt: earliestMeetAt(mine, spans.get(id) ?? [], now),
        }),
      );
    }
    return out;
  }

  /** Of these people, those with an open like to the viewer: the grid's `Liked you` badge. */
  private async openLikersAmong(
    viewerId: string,
    ids: string[],
  ): Promise<Set<string>> {
    if (ids.length === 0) return new Set();
    const rows = await this.db.getRepository(Like).find({
      where: {
        receiverId: viewerId,
        senderId: In(ids),
        status: LikeStatus.Active,
      },
      select: { senderId: true },
    });
    return new Set(rows.map((r) => r.senderId));
  }

  /**
   * The distance from the viewer to each of these people, from the places their live hours were
   * committed with -- the nearest, as the queue measures it. Someone with no placed hour has none.
   */
  private async metersFrom(
    viewerId: string,
    ids: string[],
    now: Date,
  ): Promise<Map<string, number>> {
    if (ids.length === 0) return new Map();
    const rows = await this.db.query<
      Array<{ user_id: string; meters: number }>
    >(
      `SELECT c."user_id", min(ST_Distance(c."location", v."location")) AS "meters"
         FROM "check_ins" c
         JOIN LATERAL (
           SELECT "location" FROM "check_ins"
            WHERE "user_id" = $1 AND "status" = $3 AND "availability_end" > $4
              AND "location" IS NOT NULL
            ORDER BY "availability_start" ASC
            LIMIT 1
         ) v ON true
        WHERE c."user_id" = ANY($2::uuid[])
          AND c."status" = $3
          AND c."availability_end" > $4
          AND c."location" IS NOT NULL
        GROUP BY c."user_id"`,
      [viewerId, ids, CheckInStatus.Available, now],
    );
    return new Map(rows.map((r) => [r.user_id, Number(r.meters)]));
  }

  /** The closed likes the viewer received and has not been shown yet. */
  private async closedUnseen(viewerId: string): Promise<ClosedLike[]> {
    const blocked = await this.safety.blockedUserIdsFor(viewerId);
    const rows = await this.db.query<
      Array<{
        id: string;
        sender_id: string;
        closed_reason: LikeCloseReason;
        closed_at: Date;
        own_match: boolean;
      }>
    >(
      `SELECT l."id", l."sender_id", l."closed_reason", l."closed_at",
              COALESCE(m."user_a_id" = $1 OR m."user_b_id" = $1, false) AS "own_match"
         FROM "likes" l
         JOIN "users" u ON u."id" = l."sender_id"
         LEFT JOIN "matches" m ON m."id" = l."match_id"
        WHERE l."receiver_id" = $1
          AND l."status" = $2
          AND l."closed_reason" IS NOT NULL
          AND l."closed_seen_at" IS NULL
          AND u."status" <> ALL($3::"users_status_enum"[])
          AND u."moderation_standing" <> ALL($4::"moderation_standing_enum"[])
          AND l."sender_id" <> ALL($5::uuid[])
          AND NOT EXISTS (
            SELECT 1 FROM "search_passes" p
             WHERE p."viewer_id" = $1 AND p."target_id" = l."sender_id"
               AND p."pass_type" = 'not_interested'
          )`,
      [
        viewerId,
        LikeStatus.Withdrawn,
        INELIGIBLE_USER_STATUSES,
        HIDDEN_FROM_DISCOVERY,
        blocked,
      ],
    );
    return rows.map((r) => ({
      likeId: r.id,
      senderId: r.sender_id,
      reason: r.closed_reason,
      closedAt: new Date(r.closed_at),
      ownMatch: r.own_match,
    }));
  }

  /** Each person's committed Check-In that is still inside its 48 hours, if any. */
  private async activeWindowsOf(
    ids: string[],
    now: Date,
  ): Promise<Map<string, CheckInWindow>> {
    if (ids.length === 0) return new Map();
    const windows = await this.db.getRepository(CheckInWindow).find({
      where: {
        userId: In(ids),
        committedAt: Not(IsNull()),
        closesAt: MoreThan(now),
      },
      order: { committedAt: 'ASC' },
    });
    // Ascending, so the latest commit per person is the one left in the map.
    return new Map(windows.map((w) => [w.userId, w]));
  }

  /** Each person's first photo that may be shown, as a loadable URL -- the closed card's picture. */
  private async firstPhotoUrls(ids: string[]): Promise<Map<string, string>> {
    if (ids.length === 0) return new Map();
    const photos = await this.db.getRepository(ProfilePhoto).find({
      where: {
        userId: In(ids),
        moderationStatus: Not(PhotoModerationStatus.Rejected),
      },
      order: { position: 'ASC', createdAt: 'ASC' },
    });
    const out = new Map<string, string>();
    for (const p of photos) {
      if (!out.has(p.userId) && isDiscoverable(p.moderationStanding)) {
        out.set(p.userId, this.storage.publicUrl(p.storageKey));
      }
    }
    return out;
  }

  /**
   * The committed hours still ahead of each user -- what "can these two still meet" is decided
   * from, for a match (Search 01) and for closing a like `no_overlap` (Search 03). One query for any
   * number of people.
   *
   * Hours of an active Check-In window, AND hours created directly through `POST /me/check-ins`
   * (Epic 4), which have no window. That endpoint still exists and still means "I am available",
   * so a match must still honour it. The Search QUEUE is stricter -- it needs a committed Check-In,
   * because Search does (epic rule 1) -- but whether two people can meet is a question about hours.
   */
  async liveSpansOf(
    userIds: string[],
    now: Date = new Date(),
  ): Promise<Map<string, HourSpan[]>> {
    const out = new Map<string, HourSpan[]>();
    if (userIds.length === 0) return out;
    const rows = await this.db.query<
      Array<{ user_id: string; start: Date; end: Date }>
    >(
      `SELECT c."user_id", c."availability_start" AS "start", c."availability_end" AS "end"
         FROM "check_ins" c
         LEFT JOIN "check_in_windows" w ON w."id" = c."check_in_window_id"
        WHERE c."user_id" = ANY($1::uuid[])
          AND c."status" = $2
          AND c."availability_end" > $3
          AND (
            c."check_in_window_id" IS NULL
            OR (w."committed_at" IS NOT NULL AND w."closes_at" > $3)
          )`,
      [userIds, CheckInStatus.Available, now],
    );
    for (const r of rows) {
      const spans = out.get(r.user_id) ?? [];
      spans.push({ start: new Date(r.start), end: new Date(r.end) });
      out.set(r.user_id, spans);
    }
    return out;
  }

  /** The earliest hour two people can still meet, or null: no match can form without one. */
  async earliestMeetBetween(
    a: string,
    b: string,
    now: Date = new Date(),
  ): Promise<Date | null> {
    const spans = await this.liveSpansOf([a, b], now);
    return earliestMeetAt(spans.get(a) ?? [], spans.get(b) ?? [], now);
  }

  /** Dev menu: switch the plan. The controller refuses it in production. */
  async setPlanTier(userId: string, planTier: PlanTier): Promise<void> {
    await this.db.getRepository(User).update({ id: userId }, { planTier });
  }

  /** The viewer's committed Check-In that is still inside its 48 hours, if any. */
  private activeWindow(
    userId: string,
    now: Date,
  ): Promise<CheckInWindow | null> {
    return this.db.getRepository(CheckInWindow).findOne({
      where: { userId, committedAt: Not(IsNull()), closesAt: MoreThan(now) },
      order: { committedAt: 'DESC' },
    });
  }

  private async viewerState(
    viewerId: string,
    now: Date,
  ): Promise<ViewerState | null> {
    const window = await this.activeWindow(viewerId, now);
    if (!window) return null;
    const rows = await this.db.query<
      Array<{
        start: Date;
        end: Date;
        lat: number | null;
        lng: number | null;
      }>
    >(
      `SELECT "availability_start" AS "start", "availability_end" AS "end",
              ST_Y("location"::geometry) AS "lat", ST_X("location"::geometry) AS "lng"
         FROM "check_ins"
        WHERE "check_in_window_id" = $1 AND "status" = $2 AND "availability_end" > $3`,
      [window.id, CheckInStatus.Available, now],
    );
    const located = rows.find((r) => r.lat != null && r.lng != null);
    // No hours left, or no place to measure a distance from: nobody can be offered.
    if (rows.length === 0 || !located) return null;
    const profile = await this.db
      .getRepository(Profile)
      .findOne({ where: { userId: viewerId } });
    if (!profile) return null;
    const [saved, user, previous] = await Promise.all([
      this.db
        .getRepository(FilterSettings)
        .findOne({ where: { userId: viewerId } }),
      this.db.getRepository(User).findOne({
        where: { id: viewerId },
        select: { id: true, planTier: true },
      }),
      window.committedAt
        ? this.db.getRepository(CheckInWindow).findOne({
            where: {
              userId: viewerId,
              committedAt: LessThan(window.committedAt),
            },
            order: { committedAt: 'DESC' },
            select: { id: true },
          })
        : null,
    ]);
    return {
      window,
      spans: rows.map((r) => ({
        start: new Date(r.start),
        end: new Date(r.end),
      })),
      lat: Number(located.lat),
      lng: Number(located.lng),
      profile,
      filters: saved ?? defaultFilters(profile),
      isPremium: user?.planTier === PlanTier.Premium,
      previousWindowId: previous?.id ?? null,
    };
  }

  /**
   * One load of the feed (Matching 01, SHOWUP-190), re-ranked from scratch: filter, score, multiply,
   * gate, order, random picks -- then `search_feed_ranked`, and the id the app sends back with its
   * card, like and pass events. A viewer who cannot search (mid-date, matched, no live Check-In or no
   * place) gets an empty feed and no record: nothing was ranked.
   */
  private async rankFeed(
    viewerId: string,
    now: Date,
    layout: 'one_up' | 'grid',
  ): Promise<RankedFeed> {
    const feedRequestId = randomUUID();
    const config = await this.rankerConfig.current(now.getTime());
    const pool = await this.scoredPool(viewerId, now, config);
    if (!pool) return { feedRequestId, feed: [], eligibleCount: 0 };
    const feed = orderFeed(
      pool.scored,
      pool.viewer.isPremium,
      Math.random,
      config,
    );
    void this.analytics.trackServerEvent({
      userId: viewerId,
      ...searchFeedRankedEvent({
        feedRequestId,
        rankerVersion: config.ranker_version,
        layout,
        isPremium: pool.viewer.isPremium,
        eligibleCount: feed.length,
        items: feed.slice(0, FEED_EVENT_ITEMS).map(feedItem),
      }),
    });
    return { feedRequestId, feed, eligibleCount: feed.length };
  }

  /**
   * Steps 1-3: everyone the ticket's twelve filters admit, scored and multiplied. The pool is cut in
   * the database by place, live hours and both people's distances (each widened by the soft range
   * where it applies); the rest are checked here on that small set, by the functions in `ranker.ts`.
   */
  private async scoredPool(
    viewerId: string,
    now: Date,
    config: RankerConfig,
  ): Promise<{ viewer: ViewerState; scored: Scored<Candidate>[] } | null> {
    // Filter 8: a person mid-date neither searches nor is shown (07a blocks the tab; this is the
    // server's half of that), nor someone whose match this Check-In is still open.
    if (await this.dates.isLockedFromMatching(viewerId)) return null;
    const viewer = await this.viewerState(viewerId, now);
    if (!viewer) return null;
    if (await this.matchedInWindow([viewerId], now).then((s) => s.size > 0)) {
      return null;
    }

    // "With `filters_dealbreakers` off, every user is treated as dealbreaker on (today's behaviour).
    // The soft range only exists once the flag is on." Each side by its OWN setting.
    const flag =
      this.config.get<boolean>('features.filtersDealbreakers') ?? false;
    const softAge = (f: SearchFilters) => flag && !f.ageDealbreaker;
    const softDistance = (f: SearchFilters) => flag && !f.distanceDealbreaker;
    const widen = 1 + config.soft_range.soft_distance_pct / 100;
    const viewerReachM =
      viewer.filters.distanceKm *
      1000 *
      (softDistance(viewer.filters) ? widen : 1);

    // Everyone with live hours in an active Check-In within the viewer's reach AND the viewer within
    // theirs (filter 6, both ways, in PostGIS). Someone who never saved filters is searched with the
    // Check-In 05 default distance and the switch off, as `defaultFilters` says.
    const rows = await this.db.query<
      Array<{
        user_id: string;
        window_id: string;
        start: Date;
        end: Date;
        meters: number;
      }>
    >(
      `SELECT c."user_id", c."check_in_window_id" AS "window_id",
              c."availability_start" AS "start", c."availability_end" AS "end",
              ST_Distance(c."location", ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography) AS "meters"
         FROM "check_ins" c
         JOIN "check_in_windows" w ON w."id" = c."check_in_window_id"
         LEFT JOIN "filter_settings" f ON f."user_id" = c."user_id"
        WHERE c."user_id" <> $1
          AND c."status" = $4
          AND c."availability_end" > $5
          AND c."location" IS NOT NULL
          AND w."committed_at" IS NOT NULL
          AND w."closes_at" > $5
          AND ST_DWithin(c."location", ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography, $6)
          AND ST_DWithin(c."location", ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography,
                COALESCE(f."distance_km", $7) * 1000
                  * CASE WHEN $8::boolean AND NOT COALESCE(f."distance_dealbreaker", false)
                    THEN $9::double precision ELSE 1.0 END)`,
      [
        viewerId,
        viewer.lng,
        viewer.lat,
        CheckInStatus.Available,
        now,
        viewerReachM,
        FILTER_DISTANCE_DEFAULT,
        flag,
        widen,
      ],
    );
    if (rows.length === 0) return { viewer, scored: [] };

    const byUser = new Map<
      string,
      { windowId: string; meters: number; spans: HourSpan[] }
    >();
    for (const r of rows) {
      const entry = byUser.get(r.user_id) ?? {
        windowId: r.window_id,
        meters: Number(r.meters),
        spans: [],
      };
      entry.meters = Math.min(entry.meters, Number(r.meters));
      entry.spans.push({ start: new Date(r.start), end: new Date(r.end) });
      byUser.set(r.user_id, entry);
    }
    const ids = [...byUser.keys()];

    const [
      users,
      profiles,
      filters,
      windows,
      blocked,
      liked,
      passed,
      passedViewer,
      locked,
      matched,
      pastPartners,
      likers,
      checkIns,
      pastDates,
      notNowBefore,
    ] = await Promise.all([
      this.db.getRepository(User).find({ where: { id: In(ids) } }),
      // Filter 3: a complete profile. (No verification check in MVP.)
      this.db.getRepository(Profile).find({
        where: { userId: In(ids), isVisible: true, isComplete: true },
      }),
      this.db
        .getRepository(FilterSettings)
        .find({ where: { userId: In(ids) } }),
      this.db.getRepository(CheckInWindow).find({
        where: { id: In([...new Set(rows.map((r) => r.window_id))]) },
      }),
      // Filter 7: an explicit block, either direction.
      this.safety.blockedUserIdsFor(viewerId).then((b) => new Set(b)),
      // Search 01: open or matched likes only: a like that closed without a decision (the hours ran
      // out, or the other person matched first) brings that person back at a later Check-In. A
      // DECLINED one keeps them out, as it did while it was open: the liker's Search does not change
      // when they are declined ("the liker is not told").
      this.db
        .getRepository(Like)
        .find({
          where: {
            senderId: viewerId,
            status: In([
              LikeStatus.Active,
              LikeStatus.Matched,
              LikeStatus.Declined,
            ]),
          },
          select: { receiverId: true },
        })
        .then((l) => new Set(l.map((x) => x.receiverId))),
      // Filters 7 and 9, the viewer's side: `Not interested` ever, `Not now` in this Check-In.
      this.passedTargets(viewerId, viewer.window.id),
      // Filter 7, their side: they said `Not interested` to the viewer.
      this.db
        .getRepository(SearchPass)
        .find({
          where: {
            targetId: viewerId,
            viewerId: In(ids),
            passType: SearchPassType.NotInterested,
          },
          select: { viewerId: true },
        })
        .then((p) => new Set(p.map((x) => x.viewerId))),
      this.dates.lockedUserIdsAmong(ids).then((l) => new Set(l)),
      this.matchedInWindow(ids, now),
      this.pastDatePartners(viewerId, ids, now),
      this.openLikersAmong(viewerId, ids),
      this.committedCheckIns(ids),
      this.pastDatesOf(ids, now),
      viewer.previousWindowId
        ? this.db
            .getRepository(SearchPass)
            .find({
              where: {
                viewerId,
                passType: SearchPassType.NotNow,
                windowId: viewer.previousWindowId,
              },
              select: { targetId: true },
            })
            .then((p) => new Set(p.map((x) => x.targetId)))
        : new Set<string>(),
    ]);
    const userById = new Map(users.map((u) => [u.id, u]));
    const profileById = new Map(profiles.map((p) => [p.userId, p]));
    const filtersById = new Map(filters.map((f) => [f.userId, f]));
    const windowById = new Map(windows.map((w) => [w.id, w]));
    const viewerAge = ageFromDateOfBirth(viewer.profile.dateOfBirth);

    const scored: Scored<Candidate>[] = [];
    for (const [userId, entry] of byUser) {
      const user = userById.get(userId);
      const profile = profileById.get(userId);
      const window = windowById.get(entry.windowId);
      if (!user || !profile || !window) continue;
      if (
        INELIGIBLE_USER_STATUSES.includes(user.status) ||
        !isDiscoverable(user.moderationStanding) ||
        !isDiscoverable(profile.moderationStanding)
      ) {
        continue;
      }
      // Filter 10: a past date partner only if BOTH answered `would_meet_again: true` -- and no
      // rating stores that answer yet (`dates` keeps a 1-5 rating), so none is eligible.
      // Filter 11 (Incognito) and filter 7's review "don't match me again" have no data at all yet:
      // there is no Incognito setting and no such review answer, so they hide nobody. All three are
      // listed for product.
      if (
        blocked.has(userId) ||
        passedViewer.has(userId) ||
        liked.has(userId) ||
        passed.has(userId) ||
        locked.has(userId) ||
        matched.has(userId) ||
        pastPartners.has(userId)
      ) {
        continue;
      }
      // Filter 12.
      if (!motivationsCompatible(viewer.window.motivation, window.motivation)) {
        continue;
      }
      // Filter 1: at least `min_remaining_h` of their chosen hours still ahead.
      if (remainingHours(entry.spans, now) < config.filters.min_remaining_h) {
        continue;
      }
      // Filter 2: at least `min_overlap_h` shared, with a whole meet hour in it -- the hour the card
      // shows and a like is checked against (Search 03 closes a like with none).
      const overlap = overlapAhead(viewer.spans, entry.spans, now);
      const meetAt = earliestMeetAt(viewer.spans, entry.spans, now);
      if (
        !overlap ||
        overlap.remainingH < config.filters.min_overlap_h ||
        !meetAt
      ) {
        continue;
      }
      const theirs = filtersById.get(userId) ?? defaultFilters(profile);
      // Filter 4: gender, both ways, always hard -- no dealbreaker, no soft range.
      if (
        !wantsGender(viewer.filters, profile) ||
        !wantsGender(theirs, viewer.profile)
      ) {
        continue;
      }
      // Filters 5 and 6, both ways, each side by its own switch. A hidden age still counts here:
      // hiding is display only, never eligibility. An unknown age admits nobody.
      const age = ageFromDateOfBirth(profile.dateOfBirth);
      if (age == null || viewerAge == null) continue;
      const reach = combinedReach([
        ageReach(age, viewer.filters, softAge(viewer.filters), config),
        ageReach(viewerAge, theirs, softAge(theirs), config),
        distanceReach(
          entry.meters,
          viewer.filters.distanceKm,
          softDistance(viewer.filters),
          config,
        ),
        distanceReach(
          entry.meters,
          theirs.distanceKm,
          softDistance(theirs),
          config,
        ),
      ]);
      if (reach === 'out') continue;

      const parts = scoreOf(
        {
          motivation: [viewer.window.motivation, window.motivation],
          vibes: [viewer.window.vibes, window.vibes],
          interests: [viewer.profile.interests, profile.interests],
          familyAhead: [viewer.profile.familyAhead, profile.familyAhead],
          livingAhead: [viewer.profile.livingAhead, profile.livingAhead],
          overlap,
          pastDates: pastDates.get(userId) ?? { booked: 0, attended: 0 },
          meters: entry.meters,
          radiiKm: [viewer.filters.distanceKm, theirs.distanceKm],
        },
        config,
      );
      const likedYou = likers.has(userId);
      const boosts: Boost[] = [];
      if (likedYou) boosts.push('liked_you');
      if ((checkIns.get(userId) ?? 0) <= config.multipliers.new_user_checkins) {
        boosts.push('new_user');
      }
      if (notNowBefore.has(userId)) boosts.push('not_now');
      const softMatch = reach === 'soft';
      scored.push({
        candidate: { userId, window, profile, meters: entry.meters, meetAt },
        profileId: profile.id,
        parts,
        final:
          parts.score *
          multiplierOf(boosts, softMatch, viewer.isPremium, config),
        boosts,
        softMatch,
        likedYou,
      });
    }
    return { viewer, scored };
  }

  /**
   * Filter 10's people: anyone the viewer had a date with whose time has passed (a cancelled one was
   * never a date). Only both answering `would_meet_again: true` would bring them back, and nothing
   * stores that answer yet.
   */
  private async pastDatePartners(
    viewerId: string,
    ids: string[],
    now: Date,
  ): Promise<Set<string>> {
    if (ids.length === 0) return new Set();
    const rows = await this.db.query<Array<{ user_id: string }>>(
      `SELECT CASE WHEN "user_a_id" = $1 THEN "user_b_id" ELSE "user_a_id" END AS "user_id"
         FROM "dates"
        WHERE ("user_a_id" = $1 AND "user_b_id" = ANY($2::uuid[])
            OR "user_b_id" = $1 AND "user_a_id" = ANY($2::uuid[]))
          AND "scheduled_at" < $3 AND "status" <> $4`,
      [viewerId, ids, now, DateStatus.Cancelled],
    );
    return new Set(rows.map((r) => r.user_id));
  }

  /** Committed Check-Ins per person, this one included: `new_user` is the first two. */
  private async committedCheckIns(ids: string[]): Promise<Map<string, number>> {
    if (ids.length === 0) return new Map();
    const rows = await this.db.query<Array<{ user_id: string; n: number }>>(
      `SELECT "user_id", count(*)::int AS "n" FROM "check_in_windows"
        WHERE "user_id" = ANY($1::uuid[]) AND "committed_at" IS NOT NULL
        GROUP BY "user_id"`,
      [ids],
    );
    return new Map(rows.map((r) => [r.user_id, r.n]));
  }

  /**
   * Reliability's inputs per person: dates booked whose time has passed (not cancelled --
   * cancellations and no-shows are not inputs yet) and dates both people confirmed happened.
   */
  private async pastDatesOf(
    ids: string[],
    now: Date,
  ): Promise<Map<string, { booked: number; attended: number }>> {
    if (ids.length === 0) return new Map();
    const rows = await this.db.query<
      Array<{ user_id: string; booked: number; attended: number }>
    >(
      `SELECT u AS "user_id",
              count(*) FILTER (WHERE "scheduled_at" < $2 AND "status" <> $3)::int AS "booked",
              count(*) FILTER (WHERE "a_confirmed_happened" AND "b_confirmed_happened")::int AS "attended"
         FROM (
           SELECT "user_a_id" AS u, "scheduled_at", "status", "a_confirmed_happened", "b_confirmed_happened"
             FROM "dates" WHERE "user_a_id" = ANY($1::uuid[])
           UNION ALL
           SELECT "user_b_id" AS u, "scheduled_at", "status", "a_confirmed_happened", "b_confirmed_happened"
             FROM "dates" WHERE "user_b_id" = ANY($1::uuid[])
         ) d GROUP BY u`,
      [ids, now, DateStatus.Cancelled],
    );
    return new Map(
      rows.map((r) => [r.user_id, { booked: r.booked, attended: r.attended }]),
    );
  }

  /** Who the viewer has passed on: `Not interested` ever, and `Not now` in this Check-In. */
  private async passedTargets(
    viewerId: string,
    windowId: string,
  ): Promise<Set<string>> {
    const rows = await this.db.getRepository(SearchPass).find({
      where: [
        { viewerId, passType: SearchPassType.NotInterested },
        { viewerId, passType: SearchPassType.NotNow, windowId },
      ],
      select: { targetId: true },
    });
    return new Set(rows.map((r) => r.targetId));
  }

  /**
   * Of the given users, those with a match made during their current Check-In: 1 match = 1 date,
   * so they are no longer searching. (A match whose date was created locks them anyway; this also
   * covers one whose date could not be scheduled.)
   *
   * `updated_at`, not `created_at`: a pair has one `matches` row for life (`uq_matches_pair`), so
   * the same two people matching again in a later Check-In REACTIVATE the old row. An expired or
   * cancelled match is not `active`, so its updates never count.
   */
  private async matchedInWindow(
    userIds: string[],
    now: Date,
  ): Promise<Set<string>> {
    if (userIds.length === 0) return new Set();
    const rows = await this.db.query<Array<{ user_id: string }>>(
      `SELECT DISTINCT w."user_id"
         FROM "check_in_windows" w
         JOIN "matches" m
           ON (m."user_a_id" = w."user_id" OR m."user_b_id" = w."user_id")
          AND m."status" = $3
          AND m."updated_at" >= w."committed_at"
        WHERE w."user_id" = ANY($1::uuid[])
          AND w."committed_at" IS NOT NULL
          AND w."closes_at" > $2`,
      [userIds, now, MatchStatus.Active],
    );
    return new Set(rows.map((r) => r.user_id));
  }

  /** The full payload for the first profiles of the queue, in queue order. */
  private async loadProfiles(
    top: Candidate[],
    now: Date,
    zone: string,
  ): Promise<SearchProfileDto[]> {
    if (top.length === 0) return [];
    const ids = top.map((c) => c.userId);
    const [photos, prompts, media, attended] = await Promise.all([
      this.db.getRepository(ProfilePhoto).find({
        where: {
          userId: In(ids),
          moderationStatus: Not(PhotoModerationStatus.Rejected),
        },
        order: { position: 'ASC', createdAt: 'ASC' },
      }),
      this.db.getRepository(ProfilePrompt).find({
        where: { userId: In(ids) },
        order: { position: 'ASC' },
      }),
      // "Show unless rejected" -- the photos' post-moderation rule, decided for media on
      // 21 Oct 2026 (Search 01). Nothing reviews recordings yet, so in practice every one shows.
      this.db.getRepository(ProfileMedia).find({
        where: {
          userId: In(ids),
          moderationStatus: Not(MediaModerationStatus.Rejected),
        },
      }),
      this.attendedCounts(ids),
    ]);

    return top.map((c) => {
      const mediaOf = (kind: 'voice' | 'video'): SearchMediaDto | null => {
        const m = media.find(
          (x) =>
            x.userId === c.userId &&
            x.kind === kind &&
            isDiscoverable(x.moderationStanding),
        );
        return m
          ? {
              url: this.storage.publicUrl(m.storageKey),
              durationMs: m.durationMs,
              promptId: m.mediaPromptId,
            }
          : null;
      };
      return buildSearchProfile({
        profile: c.profile,
        photoUrls: photos
          .filter(
            (p) =>
              p.userId === c.userId && isDiscoverable(p.moderationStanding),
          )
          .map((p) => this.storage.publicUrl(p.storageKey)),
        prompts: prompts
          .filter((p) => p.userId === c.userId)
          .map((p) => ({ topicId: p.topicId, answer: p.answer })),
        voice: mediaOf('voice'),
        video: mediaOf('video'),
        checkIn: {
          motivation: c.window?.motivation ?? null,
          vibes: c.window?.vibes ?? null,
          // Their Check-In is active by construction of the queue and the likes list, so the
          // highlight shows.
          uplift: c.window?.uplift ?? null,
          district: c.window?.district ?? null,
        },
        distanceMeters: c.meters,
        meetAt: c.meetAt,
        meetDay: meetDay(c.meetAt, now, zone),
        attendedCount: attended.get(c.userId) ?? 0,
      });
    });
  }

  /**
   * `attended_count`: dates BOTH people confirmed happened. Never a rate, and a missed date is not
   * counted, shown or recorded (epic rule 4). The confirmation mechanic (Arrive, QR scan) is its
   * own story; until it ships this is the number the existing confirm columns hold.
   */
  async attendedCounts(userIds: string[]): Promise<Map<string, number>> {
    if (userIds.length === 0) return new Map();
    const rows = await this.db.query<Array<{ user_id: string; n: number }>>(
      `SELECT u AS "user_id", count(*)::int AS "n" FROM (
         SELECT "user_a_id" AS u FROM "dates"
          WHERE "a_confirmed_happened" AND "b_confirmed_happened" AND "user_a_id" = ANY($1::uuid[])
         UNION ALL
         SELECT "user_b_id" AS u FROM "dates"
          WHERE "a_confirmed_happened" AND "b_confirmed_happened" AND "user_b_id" = ANY($1::uuid[])
       ) d GROUP BY u`,
      [userIds],
    );
    return new Map(rows.map((r) => [r.user_id, r.n]));
  }
}

/** The filters a user who never saved any is searched with -- the Check-In 05 defaults, switches off. */
function defaultFilters(profile: Profile): SearchFilters {
  return {
    meetGender: checkInGenderDefault(profile.gender, profile.orientation),
    minAge: FILTER_AGE_MIN,
    maxAge: null,
    distanceKm: FILTER_DISTANCE_DEFAULT,
    ageDealbreaker: false,
    distanceDealbreaker: false,
  };
}

/** Filter 4, one way: the person's gender is one the filters meet. Unknown gender admits nobody. */
function wantsGender(
  filters: Pick<FilterSettings, 'meetGender'>,
  person: Profile,
): boolean {
  return (
    person.gender != null &&
    (filters.meetGender as string[]).includes(person.gender)
  );
}

/** One placed profile as `search_feed_ranked.items` carries it, every number to four places. */
function feedItem(p: Placed<Candidate>): SearchFeedItem {
  const r = (v: number) => Math.round(v * 10_000) / 10_000;
  return {
    profile_id: p.profileId,
    position: p.position,
    score: r(p.parts.score),
    fit: r(p.parts.fit),
    motivation_fit: r(p.parts.motivationFit),
    vibe_fit: r(p.parts.vibeFit),
    profile_fit: r(p.parts.profileFit),
    time: r(p.parts.time),
    reliability: r(p.parts.reliability),
    distance: r(p.parts.distance),
    boosts: p.boosts,
    is_explore: p.isExplore,
    soft_match: p.softMatch,
  };
}
