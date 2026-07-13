import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { CheckInsService } from '../check-ins/check-ins.service';
import { LocationService } from '../location/location.service';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { DateStatus, transitionError } from './util/date-lifecycle';

@Injectable()
export class DatesService {
  constructor(
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(DateStatusChange)
    private readonly history: Repository<DateStatusChange>,
    private readonly checkIns: CheckInsService,
    private readonly location: LocationService,
  ) {}

  /**
   * SHOWUP-51 — create a confirmed date for a freshly matched pair. The app proposes the time (the
   * start of the two people's overlapping availability) and a nearby venue; the date is created
   * already "confirmed", so nobody has to accept it. Best-effort: if the pair already has a live
   * date, or they are not both available, or there is no usable time, it returns without creating one
   * (so it can never break the matching flow that calls it).
   */
  async createConfirmedDateForMatch(
    u1: string,
    u2: string,
  ): Promise<DateEntity | null> {
    const [userAId, userBId] = u1 < u2 ? [u1, u2] : [u2, u1];

    const existing = await this.dates.findOne({
      where: { userAId, userBId, status: DateStatus.Confirmed },
    });
    if (existing) return existing;

    const [ciA, ciB] = await Promise.all([
      this.checkIns.findActive(u1),
      this.checkIns.findActive(u2),
    ]);
    if (!ciA || !ciB) return null;

    const now = Date.now();
    const startMs = Math.max(
      ciA.availabilityStart.getTime(),
      ciB.availabilityStart.getTime(),
      now,
    );
    const endMs = Math.min(
      ciA.availabilityEnd.getTime(),
      ciB.availabilityEnd.getTime(),
    );
    if (startMs >= endMs) return null; // no overlapping time left

    const centre = await this.checkIns.activeLocation(u1);
    const venue = centre ? await this.location.nearestVenue(centre) : null;

    const date = await this.dates.save(
      this.dates.create({
        userAId,
        userBId,
        matchId: null,
        scheduledAt: new Date(startMs),
        venueId: venue?.id ?? null,
        status: DateStatus.Confirmed,
      }),
    );
    await this.logChange(date.id, null, DateStatus.Confirmed, null);
    return date;
  }

  /** The dates a user is part of, newest first. */
  async listForUser(userId: string): Promise<DateEntity[]> {
    return this.dates.find({
      where: [{ userAId: userId }, { userBId: userId }],
      order: { scheduledAt: 'DESC' },
    });
  }

  /**
   * SHOWUP-53 — cancel a date. Single cancellation type; it always records the canceller so the
   * scoring work (Epic 8) can penalise them. Only a participant may cancel, and only from a state
   * where cancelling is allowed.
   */
  async cancel(
    dateId: string,
    userId: string,
    reason?: string,
  ): Promise<DateEntity> {
    const date = await this.participantDate(dateId, userId);
    const error = transitionError(date.status, DateStatus.Cancelled);
    if (error) throw new BadRequestException(error);

    const from = date.status;
    date.cancelledById = userId;
    date.cancelReason = reason ?? null;
    date.cancelledAt = new Date();
    date.status = DateStatus.Cancelled;
    await this.dates.save(date);
    await this.logChange(date.id, from, DateStatus.Cancelled, userId);
    return date;
  }

  /**
   * SHOWUP-54 — confirm the date happened and rate it. Only when BOTH participants have confirmed
   * does the date move to "completed". Ratings feed the Show-Up score (Epic 8).
   */
  async confirmHappened(
    dateId: string,
    userId: string,
    rating: number,
  ): Promise<DateEntity> {
    const date = await this.participantDate(dateId, userId);
    if (date.status !== DateStatus.Confirmed) {
      throw new BadRequestException('This date is not open for confirmation');
    }

    if (date.userAId === userId) {
      date.aConfirmedHappened = true;
      date.aRating = rating;
    } else {
      date.bConfirmedHappened = true;
      date.bRating = rating;
    }

    if (date.aConfirmedHappened && date.bConfirmedHappened) {
      const from = date.status;
      date.status = DateStatus.Completed;
      await this.dates.save(date);
      await this.logChange(date.id, from, DateStatus.Completed, userId);
    } else {
      await this.dates.save(date);
    }
    return date;
  }

  /** Load a date and confirm the caller is one of its two participants. */
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

  /** Append one row to the permanent stage-change history. */
  private async logChange(
    dateId: string,
    from: DateStatus | null,
    to: DateStatus,
    by: string | null,
  ): Promise<void> {
    await this.history.save(
      this.history.create({
        dateId,
        fromStatus: from,
        toStatus: to,
        changedByUserId: by,
      }),
    );
  }
}
