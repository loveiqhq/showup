import {
  BadRequestException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource, In, MoreThan, Not, IsNull } from 'typeorm';

import { CheckInStatus } from '../check-ins/entities/check-in.entity';
import { CheckInWindow } from '../check-ins/entities/check-in-window.entity';
import { FilterSettings } from '../check-ins/entities/filter-settings.entity';
import {
  checkInGenderDefault,
  FILTER_AGE_MIN,
  FILTER_DISTANCE_DEFAULT,
} from '../check-ins/util/check-in-flow';
import { DatesService } from '../dates/dates.service';
import { Like } from '../matching/entities/like.entity';
import {
  INELIGIBLE_USER_STATUSES,
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
import { SafetyService } from '../safety/safety.service';
import { HIDDEN_FROM_DISCOVERY, isDiscoverable } from '../safety/util/safety';
import { PlanTier, User } from '../users/entities/user.entity';
import {
  CreateSearchPassDto,
  SearchMediaDto,
  SearchProfileDto,
  SearchQueueDto,
} from './dto/search.dto';
import { SearchPass, SearchPassType } from './entities/search-pass.entity';
import { buildSearchProfile } from './util/search-profile';
import { earliestMeetAt, HourSpan, isTimeZone, meetDay } from './util/search';

/** How many profiles of the queue one response carries: the one on screen and the next two. */
export const SEARCH_QUEUE_PAGE = 3;

/** One person the viewer could meet, before their profile is loaded. */
interface Candidate {
  userId: string;
  window: CheckInWindow;
  profile: Profile;
  meters: number;
  meetAt: Date;
}

interface ViewerState {
  window: CheckInWindow;
  spans: HourSpan[];
  lat: number;
  lng: number;
  profile: Profile;
  filters: Pick<
    FilterSettings,
    'meetGender' | 'minAge' | 'maxAge' | 'distanceKm'
  >;
}

/**
 * The Search tab's server side (SHOWUP-184): the queue, the profile payload, `Not now` /
 * `Not interested`, the coach flag and the open-likes count.
 *
 * THE QUEUE IS DELIBERATELY MINIMAL. Its composition and order belong to their own ticket, not
 * written yet (epic, "Not ticketed yet"). What is here is what the ticket's rules require of any
 * queue, and nothing it would have to undo:
 *
 * - People with a committed, active Check-In whose hours still overlap the viewer's by a whole
 *   hour that has not started. This is what discovery got wrong: it matched on hours open NOW,
 *   so two people free tonight never saw each other this afternoon.
 * - Both people's filters, each way: who they want to meet, the age range, the distance.
 * - Never someone blocked either way, already liked by the viewer, `Not interested` ever, `Not now`
 *   in this Check-In, mid-date (locked until review), matched in this Check-In, hidden from
 *   discovery, or with an incomplete or invisible profile.
 * - Earliest meet hour first, then the nearest, then a stable tie-break.
 */
@Injectable()
export class SearchService {
  constructor(
    @InjectDataSource() private readonly db: DataSource,
    @Inject(STORAGE) private readonly storage: StorageService,
    private readonly safety: SafetyService,
    private readonly dates: DatesService,
  ) {}

  /** The queue, and the first profiles of it in full. `zone` is the viewer's IANA zone. */
  async queue(
    viewerId: string,
    zone: string | undefined,
    now: Date = new Date(),
    limit: number = SEARCH_QUEUE_PAGE,
  ): Promise<SearchQueueDto> {
    const tz = isTimeZone(zone) ? zone : 'UTC';
    const ranked = await this.rankedQueue(viewerId, now);
    const profiles = await this.loadProfiles(ranked.slice(0, limit), now, tz);
    return { resultsCount: ranked.length, profiles };
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
  }

  /** `Don't show again` on the actions coach -- the only act that sets the flag. */
  async hideCoach(userId: string): Promise<void> {
    await this.db
      .getRepository(User)
      .update({ id: userId }, { searchCoachHidden: true });
  }

  /**
   * The open likes the user has received: the Likes tab badge and the Search likes chip, which
   * must always show the same number (epic rule 10).
   *
   * Open = still active, from someone who can still be met: not blocked either way, not an account
   * that is gone or hidden, and not someone the user has marked `Not interested` -- a like from
   * them can never become a date, and closed cards are never counted.
   */
  async likesOpenCount(userId: string): Promise<number> {
    const blocked = await this.safety.blockedUserIdsFor(userId);
    const rows = await this.db.query<Array<{ n: number }>>(
      `SELECT count(*)::int AS n
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
        userId,
        LikeStatus.Active,
        INELIGIBLE_USER_STATUSES,
        HIDDEN_FROM_DISCOVERY,
        blocked,
      ],
    );
    return rows[0]?.n ?? 0;
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
    const saved = await this.db
      .getRepository(FilterSettings)
      .findOne({ where: { userId: viewerId } });
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
    };
  }

  private async rankedQueue(viewerId: string, now: Date): Promise<Candidate[]> {
    // A person mid-date neither searches nor is shown (07a blocks the tab; this is the server's
    // half of that).
    if (await this.dates.isLockedFromMatching(viewerId)) return [];
    const viewer = await this.viewerState(viewerId, now);
    if (!viewer) return [];
    if (await this.matchedInWindow([viewerId], now).then((s) => s.size > 0)) {
      return [];
    }

    // Everyone with live hours in an active Check-In within the VIEWER's distance. Their own
    // distance is applied below, with the rest of their filters.
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
        WHERE c."user_id" <> $1
          AND c."status" = $4
          AND c."availability_end" > $5
          AND c."location" IS NOT NULL
          AND w."committed_at" IS NOT NULL
          AND w."closes_at" > $5
          AND ST_DWithin(c."location", ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography, $6)`,
      [
        viewerId,
        viewer.lng,
        viewer.lat,
        CheckInStatus.Available,
        now,
        viewer.filters.distanceKm * 1000,
      ],
    );
    if (rows.length === 0) return [];

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
      locked,
      matched,
    ] = await Promise.all([
      this.db.getRepository(User).find({ where: { id: In(ids) } }),
      this.db.getRepository(Profile).find({
        where: { userId: In(ids), isVisible: true, isComplete: true },
      }),
      this.db
        .getRepository(FilterSettings)
        .find({ where: { userId: In(ids) } }),
      this.db.getRepository(CheckInWindow).find({
        where: { id: In([...new Set(rows.map((r) => r.window_id))]) },
      }),
      this.safety.blockedUserIdsFor(viewerId).then((b) => new Set(b)),
      // Open or matched likes only: a like that closed without a decision (the hours ran out, or
      // the other person matched first) brings that person back at a later Check-In.
      this.db
        .getRepository(Like)
        .find({
          where: {
            senderId: viewerId,
            status: In([LikeStatus.Active, LikeStatus.Matched]),
          },
          select: { receiverId: true },
        })
        .then((l) => new Set(l.map((x) => x.receiverId))),
      this.passedTargets(viewerId, viewer.window.id),
      this.dates.lockedUserIdsAmong(ids).then((l) => new Set(l)),
      this.matchedInWindow(ids, now),
    ]);
    const userById = new Map(users.map((u) => [u.id, u]));
    const profileById = new Map(profiles.map((p) => [p.userId, p]));
    const filtersById = new Map(filters.map((f) => [f.userId, f]));
    const windowById = new Map(windows.map((w) => [w.id, w]));
    const viewerAge = ageFromDateOfBirth(viewer.profile.dateOfBirth);

    const out: Candidate[] = [];
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
      if (
        blocked.has(userId) ||
        liked.has(userId) ||
        passed.has(userId) ||
        locked.has(userId) ||
        matched.has(userId)
      ) {
        continue;
      }
      const theirs = filtersById.get(userId) ?? defaultFilters(profile);
      if (
        !wants(
          viewer.filters,
          profile,
          ageFromDateOfBirth(profile.dateOfBirth),
        ) ||
        !wants(theirs, viewer.profile, viewerAge) ||
        entry.meters > theirs.distanceKm * 1000
      ) {
        continue;
      }
      const meetAt = earliestMeetAt(viewer.spans, entry.spans, now);
      if (!meetAt) continue;
      out.push({ userId, window, profile, meters: entry.meters, meetAt });
    }
    return out.sort(
      (a, b) =>
        a.meetAt.getTime() - b.meetAt.getTime() ||
        a.meters - b.meters ||
        a.userId.localeCompare(b.userId),
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
          motivation: c.window.motivation,
          vibes: c.window.vibes,
          // Their Check-In is active by construction of the queue, so the highlight shows.
          uplift: c.window.uplift,
          district: c.window.district,
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

/** The filters a user who never saved any is searched with -- the Check-In 05 defaults. */
function defaultFilters(
  profile: Profile,
): Pick<FilterSettings, 'meetGender' | 'minAge' | 'maxAge' | 'distanceKm'> {
  return {
    meetGender: checkInGenderDefault(profile.gender, profile.orientation),
    minAge: FILTER_AGE_MIN,
    maxAge: null,
    distanceKm: FILTER_DISTANCE_DEFAULT,
  };
}

/**
 * Whether filters admit a person: their gender and age. A HIDDEN age still counts here -- hiding
 * is display only, never eligibility (CLAUDE.md, "Hidden age"). Unknown gender or age admits
 * nobody: a filter cannot be satisfied by a value that does not exist.
 */
function wants(
  filters: Pick<FilterSettings, 'meetGender' | 'minAge' | 'maxAge'>,
  person: Profile,
  age: number | null,
): boolean {
  if (person.gender == null || age == null) return false;
  if (!(filters.meetGender as string[]).includes(person.gender)) return false;
  return (
    age >= filters.minAge && (filters.maxAge == null || age <= filters.maxAge)
  );
}
