import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, LessThanOrEqual, MoreThan, Repository } from 'typeorm';

import { LatLng } from '../location/location.geo';
import { CreateCheckInDto } from './dto/create-check-in.dto';
import { CheckIn } from './entities/check-in.entity';
import {
  availabilityWindowError,
  CheckInStatus,
  DEFAULT_PREPARATION_MINUTES,
  isActiveCheckIn,
  preparationTimeError,
} from './util/check-in';

/** A nearby, available user surfaced by proximity search — derived data only, never coordinates. */
export interface NearbyCheckIn {
  userId: string;
  /** Distance from the search centre, rounded to the nearest 100 m (never exact coordinates). */
  distanceMeters: number;
  availabilityStart: Date;
  availabilityEnd: Date;
}

@Injectable()
export class CheckInsService {
  constructor(
    @InjectRepository(CheckIn)
    private readonly checkIns: Repository<CheckIn>,
    @InjectDataSource()
    private readonly dataSource: DataSource,
  ) {}

  /**
   * Create a check-in for the user, after validating the availability window and confirming the
   * user has no live/upcoming check-in already (one at a time).
   */
  async create(userId: string, dto: CreateCheckInDto): Promise<CheckIn> {
    const start = new Date(dto.availabilityStart);
    const end = new Date(dto.availabilityEnd);
    const now = new Date();

    const error = availabilityWindowError(start, end, now);
    if (error) throw new BadRequestException(error);

    const preparationMinutes =
      dto.preparationMinutes ?? DEFAULT_PREPARATION_MINUTES;
    const prepError = preparationTimeError(preparationMinutes);
    if (prepError) throw new BadRequestException(prepError);

    const { latitude, longitude } = dto;
    if ((latitude == null) !== (longitude == null)) {
      throw new BadRequestException(
        'latitude and longitude must be provided together',
      );
    }

    // Block stacking: any non-cancelled, not-yet-ended check-in counts as already active.
    const existing = await this.checkIns.findOne({
      where: {
        userId,
        status: CheckInStatus.Available,
        availabilityEnd: MoreThan(now),
      },
    });
    if (existing) {
      throw new BadRequestException('You already have an active check-in');
    }

    const checkIn = await this.checkIns.save(
      this.checkIns.create({
        userId,
        status: CheckInStatus.Available,
        availabilityStart: start,
        availabilityEnd: end,
        preparationMinutes,
      }),
    );

    // Store the location via PostGIS (kept out of the ORM entity on purpose — see the migration).
    if (latitude != null && longitude != null) {
      await this.dataSource.query(
        `UPDATE check_ins SET location = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography WHERE id = $3`,
        [longitude, latitude, checkIn.id],
      );
    }

    return checkIn;
  }

  /** The user's currently-active check-in (available and inside its window), or null. */
  async findActive(userId: string): Promise<CheckIn | null> {
    const now = new Date();
    const candidates = await this.checkIns.find({
      where: { userId, status: CheckInStatus.Available },
      order: { availabilityEnd: 'DESC' },
    });
    return candidates.find((c) => isActiveCheckIn(c, now)) ?? null;
  }

  /**
   * SHOWUP-42 — users who are available RIGHT NOW within `radiusMeters` of a search centre.
   *
   * Filters: status available, a location is set, the availability window is currently open, the
   * account is not suspended/deleted/leaving, and the user is not in the exclusion list (the viewer
   * themselves plus any blocked users). Ordered nearest-first. Returns derived distance only —
   * never coordinates.
   *
   * Note: the block list is passed in. A user-blocking table does not exist yet (Epic 12 / Safety);
   * until it does, callers pass an empty `blockedUserIds`. The query is already block-ready.
   */
  async findNearbyAvailable(params: {
    center: LatLng;
    radiusMeters: number;
    now?: Date;
    /** The viewer's own id, so they don't match themselves. */
    excludeUserId?: string;
    /** Users the viewer has blocked / been blocked by (Epic 12 will supply this). */
    blockedUserIds?: string[];
  }): Promise<NearbyCheckIn[]> {
    const now = params.now ?? new Date();
    const excluded = [
      ...(params.excludeUserId ? [params.excludeUserId] : []),
      ...(params.blockedUserIds ?? []),
    ];

    const center = `ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography`;
    const rows = await this.dataSource.query<
      Array<{
        userId: string;
        distanceMeters: string | number;
        availabilityStart: string;
        availabilityEnd: string;
      }>
    >(
      `SELECT c.user_id AS "userId",
              round(ST_Distance(c.location, ${center})::numeric, -2)::float8 AS "distanceMeters",
              c.availability_start AS "availabilityStart",
              c.availability_end   AS "availabilityEnd"
         FROM check_ins c
         JOIN users u ON u.id = c.user_id
        WHERE c.status = 'available'
          AND c.location IS NOT NULL
          AND c.availability_start <= $3
          AND c.availability_end   >  $3
          AND u.status NOT IN ('suspended', 'deleted', 'deletion_pending')
          AND c.user_id <> ALL($4::uuid[])
          AND ST_DWithin(c.location, ${center}, $5)
        ORDER BY ST_Distance(c.location, ${center}) ASC`,
      [
        params.center.lng,
        params.center.lat,
        now,
        excluded,
        params.radiusMeters,
      ],
    );

    return rows.map((r) => ({
      userId: r.userId,
      distanceMeters: Number(r.distanceMeters),
      availabilityStart: new Date(r.availabilityStart),
      availabilityEnd: new Date(r.availabilityEnd),
    }));
  }

  /** The user's current check-in location (lat/lng), or null if they are not available now. */
  async activeLocation(userId: string): Promise<LatLng | null> {
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

  /** Cancel a check-in. Only the owner may cancel their own. */
  async cancel(userId: string, id: string): Promise<CheckIn> {
    const checkIn = await this.checkIns.findOne({ where: { id } });
    if (!checkIn) throw new NotFoundException('Check-in not found');
    if (checkIn.userId !== userId) {
      throw new ForbiddenException('You can only cancel your own check-in');
    }
    checkIn.status = CheckInStatus.Cancelled;
    return this.checkIns.save(checkIn);
  }

  /**
   * Mark past-window available check-ins as expired. This is the body the background job (SHOWUP-40)
   * will run once Redis/BullMQ (Epic 17) is wired. It is housekeeping only: matching and `findActive`
   * already treat past-window check-ins as inactive via `isActiveCheckIn`, so correctness does not
   * depend on this running, and running it twice is harmless (idempotent). Returns the count expired.
   */
  async expireDue(now: Date = new Date()): Promise<number> {
    const result = await this.checkIns.update(
      {
        status: CheckInStatus.Available,
        availabilityEnd: LessThanOrEqual(now),
      },
      { status: CheckInStatus.Expired },
    );
    return result.affected ?? 0;
  }
}
