import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, In, Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import {
  likeSentEvent,
  likeWithdrawnEvent,
  matchCreatedEvent,
} from '../analytics/events/server-events';
import { CheckInsService } from '../check-ins/check-ins.service';
import { DatesService } from '../dates/dates.service';
import { LatLng } from '../location/location.geo';
import { Profile } from '../profiles/entities/profile.entity';
import { SafetyService } from '../safety/safety.service';
import { SearchService } from '../search/search.service';
import { earliestMeetAt } from '../search/util/search';
import { isDiscoverable } from '../safety/util/safety';
import { User } from '../users/entities/user.entity';
import { CreateLikeDto, likeMessage } from './dto/create-like.dto';
import { Like } from './entities/like.entity';
import { Match } from './entities/match.entity';
import {
  INELIGIBLE_USER_STATUSES,
  LikeStatus,
  MatchStatus,
  orderedUserPair,
} from './util/match';

const DEFAULT_DISCOVERY_RADIUS_METERS = 50_000;

@Injectable()
export class MatchingService {
  constructor(
    @InjectRepository(Like) private readonly likes: Repository<Like>,
    @InjectRepository(Match) private readonly matches: Repository<Match>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectDataSource() private readonly dataSource: DataSource,
    private readonly checkIns: CheckInsService,
    private readonly safety: SafetyService,
    private readonly dates: DatesService,
    private readonly analytics: AnalyticsService,
    private readonly search: SearchService,
  ) {}

  /**
   * Send a like (Search 01). Idempotent per viewer and profile: a retry of a like already saved
   * answers with that like and fires nothing again, so a background retry can never make a second
   * one.
   *
   * A match needs mutual interest AND an hour both Check-Ins can still meet in (`earliestMeetAt`)
   * -- not, as before, both people being free at this very minute, which never matched two people
   * free tonight. Without one the like simply stays open, and closes `no_overlap` when the job
   * finds the hours gone.
   *
   * A like that closed without a decision opens again when it is sent again: a new Check-In is a
   * new chance, and the Search queue shows that person again once their like is closed.
   */
  async sendLike(
    senderId: string,
    dto: CreateLikeDto,
  ): Promise<{ like: Like; match?: Match }> {
    const targetId = dto.targetUserId;
    if (targetId === senderId) {
      throw new BadRequestException('You cannot like yourself');
    }

    // Locked out of matching until they review their current date — no looking for others meanwhile.
    if (await this.dates.isLockedFromMatching(senderId)) {
      throw new BadRequestException(
        'Finish reviewing your current date before matching again',
      );
    }

    const target = await this.users.findOne({ where: { id: targetId } });
    if (!target) throw new NotFoundException('User not found');
    if (
      INELIGIBLE_USER_STATUSES.includes(target.status) ||
      !isDiscoverable(target.moderationStanding)
    ) {
      throw new BadRequestException('You cannot like this user');
    }
    // Safety (Epic 12, SHOWUP-77): a block in either direction prevents a like.
    if (await this.safety.isBlockedEitherWay(senderId, targetId)) {
      throw new BadRequestException('You cannot like this user');
    }

    // ANY sender may attach a message; only a Premium RECIPIENT may read it (Search 01, 03).
    const message = likeMessage(dto.message);
    const { like, created } = await this.saveLike(senderId, targetId, message);

    if (created) {
      const attended =
        (await this.search.attendedCounts([targetId])).get(targetId) ?? 0;
      void this.analytics.trackServerEvent({
        userId: senderId,
        ...likeSentEvent({
          hasMessage: message != null,
          targetAttendedCount: attended,
          position: dto.position,
        }),
      });
    }
    if (like.status !== LikeStatus.Active) return { like };

    // A match needs the interest to be mutual...
    const reciprocal = await this.likes.findOne({
      where: {
        senderId: targetId,
        receiverId: senderId,
        status: LikeStatus.Active,
      },
    });
    if (!reciprocal) return { like };

    // ...and an hour both can still meet in.
    if (!(await this.search.earliestMeetBetween(senderId, targetId))) {
      return { like };
    }

    const made = await this.createMatch(senderId, targetId, [
      like.id,
      reciprocal.id,
    ]);
    // Lost the race: one of the two took another match first, which closed these likes.
    if (!made) {
      return {
        like: (await this.likes.findOne({ where: { id: like.id } })) ?? like,
      };
    }
    const { match, withdrawn } = made;
    void this.analytics.trackServerEvent({
      userId: senderId,
      ...matchCreatedEvent({ matchId: match.id }),
    });
    for (const w of withdrawn) {
      void this.analytics.trackServerEvent({
        userId: w.senderId,
        ...likeWithdrawnEvent({ matchId: match.id, reason: 'matched' }),
      });
    }
    like.status = LikeStatus.Matched;
    like.matchId = match.id;

    // Auto-create the confirmed date for this new match (Epic 7, SHOWUP-51). Best-effort — it must
    // never break matching, so any failure or "not enough info to suggest a date" is swallowed.
    await this.dates
      .createConfirmedDateForMatch(senderId, targetId)
      .catch(() => undefined);

    return { like, match };
  }

  /**
   * The like row, created or reopened. `created` is true when a like was SENT by this call -- a new
   * row or a closed one opened again -- and false for a retry of one already open or matched.
   */
  private async saveLike(
    senderId: string,
    receiverId: string,
    message: string | null,
  ): Promise<{ like: Like; created: boolean }> {
    const existing = await this.likes.findOne({
      where: { senderId, receiverId },
    });
    if (existing?.status === LikeStatus.Withdrawn) {
      existing.status = LikeStatus.Active;
      existing.closedReason = null;
      existing.closedAt = null;
      existing.matchId = null;
      existing.message = message;
      return { like: await this.likes.save(existing), created: true };
    }
    if (existing) return { like: existing, created: false };
    // Two retries can race past the lookup; the unique constraint decides, and the loser reads
    // the winner's row instead of answering 500.
    const inserted = await this.dataSource.query<Array<{ id: string }>>(
      `INSERT INTO "likes" ("sender_id", "receiver_id", "status", "message")
       VALUES ($1, $2, $3, $4)
       ON CONFLICT ON CONSTRAINT "uq_likes_sender_receiver" DO NOTHING
       RETURNING "id"`,
      [senderId, receiverId, LikeStatus.Active, message],
    );
    const like = await this.likes.findOneOrFail({
      where: { senderId, receiverId },
    });
    return { like, created: inserted.length > 0 };
  }

  /**
   * Creates the match and closes every other open like involving either person, in one
   * transaction -- 1 match = 1 date.
   *
   * SERIALISED PER PERSON. Both users' rows are locked, always in id order so two transactions can
   * never deadlock, and the two likes are re-read under the lock: if either has closed meanwhile
   * (one of the pair took another match first), nothing is created and null is returned. Without
   * the lock, two mutual likes arriving together for one person made two matches.
   *
   * WHICH LIKES CLOSE: every open like with either person at either end ("either person's other
   * like became mutual first", `like_withdrawn`). Their recipients see them as Too late.
   */
  private async createMatch(
    u1: string,
    u2: string,
    likeIds: string[],
  ): Promise<{ match: Match; withdrawn: Array<{ senderId: string }> } | null> {
    const [userAId, userBId] = orderedUserPair(u1, u2);
    return this.dataSource.transaction(async (manager) => {
      await manager.query(
        `SELECT "id" FROM "users" WHERE "id" = ANY($1::uuid[]) ORDER BY "id" FOR UPDATE`,
        [[userAId, userBId]],
      );
      const open = await manager.count(Like, {
        where: { id: In(likeIds), status: LikeStatus.Active },
      });
      if (open !== likeIds.length) return null;

      // One row per pair for life (`uq_matches_pair`): the same two people matching again in a
      // later Check-In reactivate it.
      let match = await manager.findOne(Match, {
        where: { userAId, userBId },
      });
      if (!match) {
        match = manager.create(Match, {
          userAId,
          userBId,
          status: MatchStatus.Active,
        });
      } else {
        match.status = MatchStatus.Active;
      }
      match = await manager.save(match);

      await manager.update(
        Like,
        { id: In(likeIds) },
        { status: LikeStatus.Matched, matchId: match.id },
      );
      // A CTE so the driver hands back the rows: a bare UPDATE ... RETURNING comes back as a
      // [rows, count] pair from `query`.
      const withdrawn = await manager.query<Array<{ senderId: string }>>(
        `WITH closed AS (
           UPDATE "likes"
              SET "status" = 'withdrawn', "closed_reason" = 'matched', "closed_at" = now(),
                  "match_id" = $1, "updated_at" = now()
            WHERE "status" = 'active'
              AND ("sender_id" = ANY($2::uuid[]) OR "receiver_id" = ANY($2::uuid[]))
              AND "id" <> ALL($3::uuid[])
            RETURNING "sender_id" AS "senderId"
         ) SELECT * FROM closed`,
        [match.id, [userAId, userBId], likeIds],
      );
      return { match, withdrawn };
    });
  }

  /**
   * Closes every open like whose two Check-Ins have no meet hour left (`no_overlap`, Search 03):
   * the recipient sees it as Out of time, and it stops counting on the badge. Run once a minute by
   * the like-expiry job. State-guarded, so a retry or a double run closes nothing twice.
   */
  async withdrawNoOverlap(now: Date = new Date()): Promise<number> {
    const open = await this.likes.find({
      where: { status: LikeStatus.Active },
      select: { id: true, senderId: true, receiverId: true },
    });
    if (open.length === 0) return 0;
    const people = [
      ...new Set(open.flatMap((l) => [l.senderId, l.receiverId])),
    ];
    const spans = await this.search.liveSpansOf(people, now);
    const gone = open.filter(
      (l) =>
        earliestMeetAt(
          spans.get(l.senderId) ?? [],
          spans.get(l.receiverId) ?? [],
          now,
        ) == null,
    );
    if (gone.length === 0) return 0;
    const closed = await this.dataSource.query<Array<{ senderId: string }>>(
      `WITH closed AS (
         UPDATE "likes"
            SET "status" = 'withdrawn', "closed_reason" = 'no_overlap', "closed_at" = $2,
                "updated_at" = now()
          WHERE "id" = ANY($1::uuid[]) AND "status" = 'active'
          RETURNING "sender_id" AS "senderId"
       ) SELECT * FROM closed`,
      [gone.map((l) => l.id), now],
    );
    for (const c of closed) {
      void this.analytics.trackServerEvent({
        userId: c.senderId,
        ...likeWithdrawnEvent({ matchId: null, reason: 'no_overlap' }),
      });
    }
    return closed.length;
  }

  /**
   * The discovery feed: nearby, available, eligible profiles the viewer has not already liked.
   * Centres on the viewer's own active check-in location. Returns derived distance, never coordinates.
   */
  async getDiscovery(
    userId: string,
    radiusMeters: number = DEFAULT_DISCOVERY_RADIUS_METERS,
  ): Promise<Array<{ profile: Profile; distanceMeters: number }>> {
    // A person is out of the pool while they still owe a review on a live date (locked until review).
    if (await this.dates.isLockedFromMatching(userId)) return [];

    const center = await this.activeCenter(userId);
    if (!center) return []; // Not checked in / not available → nothing to discover.

    const nearby = await this.checkIns.findNearbyAvailable({
      center,
      radiusMeters,
      excludeUserId: userId,
      // Safety (Epic 12, SHOWUP-77): hide anyone blocked in either direction.
      blockedUserIds: await this.safety.blockedUserIdsFor(userId),
    });
    if (nearby.length === 0) return [];

    const likedRows = await this.likes.find({
      where: { senderId: userId },
      select: { receiverId: true },
    });
    const alreadyLiked = new Set(likedRows.map((r) => r.receiverId));

    const distanceByUser = new Map(
      nearby.map((n) => [n.userId, n.distanceMeters]),
    );
    const candidateIds = nearby
      .map((n) => n.userId)
      .filter((id) => !alreadyLiked.has(id));
    if (candidateIds.length === 0) return [];

    // Hide anyone who is themselves locked (mid-date with a review still outstanding).
    const lockedIds = new Set(
      await this.dates.lockedUserIdsAmong(candidateIds),
    );
    const openCandidateIds = candidateIds.filter((id) => !lockedIds.has(id));
    if (openCandidateIds.length === 0) return [];

    // Only surface complete, visible profiles — a profile is not discoverable until it is complete.
    const profiles = await this.profiles.find({
      where: {
        userId: In(openCandidateIds),
        isVisible: true,
        isComplete: true,
      },
    });
    const byUser = new Map(profiles.map((p) => [p.userId, p]));

    // Preserve the nearest-first ordering from the proximity search.
    return openCandidateIds
      .map((id) => byUser.get(id))
      .filter((p): p is Profile => Boolean(p))
      .map((p) => ({
        profile: p,
        distanceMeters: distanceByUser.get(p.userId) ?? 0,
      }));
  }

  /** The viewer's active matches. */
  async listMatches(userId: string): Promise<Match[]> {
    return this.matches.find({
      where: [
        { userAId: userId, status: MatchStatus.Active },
        { userBId: userId, status: MatchStatus.Active },
      ],
      order: { createdAt: 'DESC' },
    });
  }

  /** The viewer's current check-in location (lat/lng), or null if they are not available now. */
  private async activeCenter(userId: string): Promise<LatLng | null> {
    const now = new Date();
    const rows = await this.dataSource.query<
      Array<{ lat: string | number; lng: string | number }>
    >(
      `SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng
         FROM check_ins
        WHERE user_id = $1
          AND status = 'available'
          AND location IS NOT NULL
          AND availability_start <= $2
          AND availability_end   >  $2
        ORDER BY availability_end DESC
        LIMIT 1`,
      [userId, now],
    );
    if (rows.length === 0) return null;
    return { lat: Number(rows[0].lat), lng: Number(rows[0].lng) };
  }
}
