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
import { DateChatMessage } from './entities/date-chat-message.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { DateStatus, transitionError } from './util/date-lifecycle';
import { noShowReportError } from './util/no-show';
import { ChatReason, chatWindowError } from './util/pre-date-chat';

@Injectable()
export class DatesService {
  constructor(
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(DateStatusChange)
    private readonly history: Repository<DateStatusChange>,
    @InjectRepository(DateChatMessage)
    private readonly chat: Repository<DateChatMessage>,
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

  /**
   * Report that the other person did not show up. This is a DATE OUTCOME, deliberately separate from
   * the report / safety flow: it moves the date to "no-show reported" and records who reported it in
   * the stage history. Only a participant may report, only from a still-confirmed date, and only once
   * the date is actually due. Downstream effects (score, dispute, any block) are handled later, and
   * the late-vs-no-show grace rule is a separate decision.
   */
  async reportNoShow(dateId: string, userId: string): Promise<DateEntity> {
    const date = await this.participantDate(dateId, userId);
    const error = transitionError(date.status, DateStatus.NoShowReported);
    if (error) throw new BadRequestException(error);
    const windowError = noShowReportError(date.scheduledAt, new Date());
    if (windowError) throw new BadRequestException(windowError);

    const from = date.status;
    date.status = DateStatus.NoShowReported;
    await this.dates.save(date);
    await this.logChange(date.id, from, DateStatus.NoShowReported, userId);
    return date;
  }

  /**
   * SHOWUP-115 — send a pre-date chat message. The sender always picks a preset reason and may add
   * optional free text. Only a participant may send, only on a still-live (confirmed) date, and only
   * once the chat window has opened (~1.5 h before). The reason tag is what later feeds analytics
   * (Epic 11); the free-text body stays private between the two people.
   */
  async sendChatMessage(
    dateId: string,
    userId: string,
    reason: ChatReason,
    body?: string,
  ): Promise<DateChatMessage> {
    const date = await this.participantDate(dateId, userId);
    if (date.status !== DateStatus.Confirmed) {
      throw new BadRequestException(
        'The chat is only open for an upcoming date',
      );
    }
    const windowError = chatWindowError(date.scheduledAt);
    if (windowError) throw new BadRequestException(windowError);

    return this.chat.save(
      this.chat.create({
        dateId: date.id,
        senderId: userId,
        reason,
        body: body ?? null,
      }),
    );
  }

  /** SHOWUP-115 — the chat messages on a date, oldest first. Only a participant may read them. */
  async listChatMessages(
    dateId: string,
    userId: string,
  ): Promise<DateChatMessage[]> {
    await this.participantDate(dateId, userId);
    return this.chat.find({
      where: { dateId },
      order: { createdAt: 'ASC' },
    });
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
