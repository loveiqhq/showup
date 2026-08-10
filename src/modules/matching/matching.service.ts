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
  matchCreatedEvent,
} from '../analytics/events/server-events';
import { CheckInsService } from '../check-ins/check-ins.service';
import { DatesService } from '../dates/dates.service';
import { LatLng } from '../location/location.geo';
import { Profile } from '../profiles/entities/profile.entity';
import { SafetyService } from '../safety/safety.service';
import { isDiscoverable } from '../safety/util/safety';
import { User, UserStatus } from '../users/entities/user.entity';
import { CreateLikeDto } from './dto/create-like.dto';
import { Like } from './entities/like.entity';
import { Match } from './entities/match.entity';
import { LikeStatus, MatchStatus, orderedUserPair } from './util/match';

/** Accounts that can never be liked or surfaced. */
const INELIGIBLE_STATUSES = [
  UserStatus.Suspended,
  UserStatus.Deleted,
  UserStatus.DeletionPending,
];

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
  ) {}

  /**
   * Send a like. Idempotent (a repeat like reuses the existing one). If the interest is mutual AND
   * both people are currently available, a match is created; otherwise the like simply persists.
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
      INELIGIBLE_STATUSES.includes(target.status) ||
      !isDiscoverable(target.moderationStanding)
    ) {
      throw new BadRequestException('You cannot like this user');
    }
    // Safety (Epic 12, SHOWUP-77): a block in either direction prevents a like.
    if (await this.safety.isBlockedEitherWay(senderId, targetId)) {
      throw new BadRequestException('You cannot like this user');
    }
    // TODO(Epic 9 / Payments): enforce premium before accepting a message, and enforce like limits.

    // Idempotent: reuse an existing like rather than erroring on the unique constraint.
    let like = await this.likes.findOne({
      where: { senderId, receiverId: targetId },
    });
    if (!like) {
      like = await this.likes.save(
        this.likes.create({
          senderId,
          receiverId: targetId,
          status: LikeStatus.Active,
          message: dto.message ?? null,
        }),
      );
    }

    void this.analytics.trackServerEvent({
      userId: senderId,
      ...likeSentEvent({ hasMessage: (dto.message ?? null) != null }),
    });

    // A match needs the interest to be mutual...
    const reciprocal = await this.likes.findOne({
      where: { senderId: targetId, receiverId: senderId },
    });
    if (!reciprocal) return { like };

    // ...and both people to be available right now (no match when not available). If not, the
    // likes persist and a match can form later, rather than being lost.
    const [senderActive, targetActive] = await Promise.all([
      this.checkIns.findActive(senderId),
      this.checkIns.findActive(targetId),
    ]);
    if (!senderActive || !targetActive) return { like };

    const match = await this.createMatch(senderId, targetId, [
      like.id,
      reciprocal.id,
    ]);
    void this.analytics.trackServerEvent({
      userId: senderId,
      ...matchCreatedEvent({ matchId: match.id }),
    });
    like.status = LikeStatus.Matched;

    // Auto-create the confirmed date for this new match (Epic 7, SHOWUP-51). Best-effort — it must
    // never break matching, so any failure or "not enough info to suggest a date" is swallowed.
    await this.dates
      .createConfirmedDateForMatch(senderId, targetId)
      .catch(() => undefined);

    return { like, match };
  }

  /** Create the match (canonical pair) and mark both likes matched, in one transaction. */
  private async createMatch(
    u1: string,
    u2: string,
    likeIds: string[],
  ): Promise<Match> {
    const [userAId, userBId] = orderedUserPair(u1, u2);
    return this.dataSource.transaction(async (manager) => {
      let match = await manager.findOne(Match, {
        where: { userAId, userBId },
      });
      if (!match) {
        match = await manager.save(
          manager.create(Match, {
            userAId,
            userBId,
            status: MatchStatus.Active,
          }),
        );
      }
      await manager.update(
        Like,
        { id: In(likeIds) },
        { status: LikeStatus.Matched },
      );
      return match;
    });
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
