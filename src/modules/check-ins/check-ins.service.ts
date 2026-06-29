import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { LessThanOrEqual, MoreThan, Repository } from 'typeorm';

import { CreateCheckInDto } from './dto/create-check-in.dto';
import { CheckIn } from './entities/check-in.entity';
import {
  availabilityWindowError,
  CheckInStatus,
  isActiveCheckIn,
} from './util/check-in';

@Injectable()
export class CheckInsService {
  constructor(
    @InjectRepository(CheckIn)
    private readonly checkIns: Repository<CheckIn>,
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

    return this.checkIns.save(
      this.checkIns.create({
        userId,
        status: CheckInStatus.Available,
        availabilityStart: start,
        availabilityEnd: end,
      }),
    );
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
