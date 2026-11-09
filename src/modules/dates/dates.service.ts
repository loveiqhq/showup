import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import type { EndedInChatDto } from '../date-screen/dto/active-date.dto';
import { cantMakeItFrom } from './util/end-in-chat';
import { firstName } from '../search/util/search-profile';
import { Profile } from '../profiles/entities/profile.entity';
import { Match } from '../matching/entities/match.entity';
import { MatchStatus, orderedUserPair } from '../matching/util/match';
import { InjectRepository } from '@nestjs/typeorm';
import { Between, In, IsNull, Repository } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import {
  coordinateUnlockedEvent,
  dateCancelledEvent,
  dateCompletedEvent,
  dateConfirmedEvent,
  dateEndedInChatEvent,
} from '../analytics/events/date-events';
import { CheckInsService } from '../check-ins/check-ins.service';
import { LocationService } from '../location/location.service';
import { DateChatMessage } from './entities/date-chat-message.entity';
import { DateStatusChange } from './entities/date-status-change.entity';
import { DateEntity } from './entities/date.entity';
import { dueForUnlock, minutesBeforeStart } from './util/coordinate-unlock';
import { reviewWindowError } from './util/date-review';
import { dateLocksUser } from './util/date-lock';
import { DateStatus, transitionError } from './util/date-lifecycle';
import { noShowReportError } from './util/no-show';
import {
  ChatReason,
  SENDABLE_REASONS,
  chatWindowError,
  isUniqueViolation,
} from './util/pre-date-chat';

@Injectable()
export class DatesService {
  constructor(
    @InjectRepository(DateEntity)
    private readonly dates: Repository<DateEntity>,
    @InjectRepository(DateStatusChange)
    private readonly history: Repository<DateStatusChange>,
    @InjectRepository(DateChatMessage)
    private readonly chat: Repository<DateChatMessage>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    @InjectRepository(Match)
    private readonly matches: Repository<Match>,
    private readonly checkIns: CheckInsService,
    private readonly location: LocationService,
    private readonly analytics: AnalyticsService,
  ) {}

  /**
   * SHOWUP-51 — create a confirmed date for a freshly matched pair. The app proposes the time (the
   * start of the two people's overlapping availability) and a venue chosen to be fair to both
   * people rather than convenient for one of them; the date is created
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

    // The venue is chosen fairly between the two people. It previously used only `u1`'s location,
    // which meant the venue always landed next to whoever happened to send the like that completed
    // the match, while the other person crossed town. That was arbitrary (it depended purely on who
    // liked second) and systematically unfair to the same side every time.
    const [locA, locB] = await Promise.all([
      this.checkIns.activeLocation(u1),
      this.checkIns.activeLocation(u2),
    ]);
    let venue: { id: string } | null = null;
    if (locA && locB) {
      venue = await this.location.fairestVenue(locA, locB);
    } else {
      // Only one usable location: fall back to nearest. Both people must have an active check-in to
      // reach this point, and a location is mandatory at check-in, so this is defensive only.
      const only = locA ?? locB;
      if (only) venue = await this.location.nearestVenue(only);
    }

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
    // Server event (counted once per date), keyed to the canonical first participant.
    void this.analytics.trackServerEvent({
      userId: userAId,
      ...dateConfirmedEvent(date),
    });
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
   * Whether a user is currently locked out of matching. A person is locked from the moment they are
   * matched (a confirmed date exists) until they submit their OWN post-date review; there is no
   * timed auto-release. Used by discovery so a person on a live date neither searches nor is shown.
   */
  async isLockedFromMatching(userId: string): Promise<boolean> {
    const dates = await this.dates.find({
      where: [
        { userAId: userId, status: DateStatus.Confirmed },
        { userBId: userId, status: DateStatus.Confirmed },
      ],
    });
    return dates.some((d) => dateLocksUser(d, userId));
  }

  /** Of the given users, those currently locked out of matching (mid-date, review outstanding). */
  async lockedUserIdsAmong(userIds: string[]): Promise<string[]> {
    if (userIds.length === 0) return [];
    const rows = await this.dates.find({
      where: [
        { userAId: In(userIds), status: DateStatus.Confirmed },
        { userBId: In(userIds), status: DateStatus.Confirmed },
      ],
    });
    const target = new Set(userIds);
    const locked = new Set<string>();
    for (const d of rows) {
      if (target.has(d.userAId) && dateLocksUser(d, d.userAId)) {
        locked.add(d.userAId);
      }
      if (target.has(d.userBId) && dateLocksUser(d, d.userBId)) {
        locked.add(d.userBId);
      }
    }
    return [...locked];
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
    void this.analytics.trackServerEvent({
      userId,
      ...dateCancelledEvent(date),
    });
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

    // A date may only be reviewed AFTER it has finished — never before or during it.
    const reviewError = reviewWindowError(date.scheduledAt, new Date());
    if (reviewError) throw new BadRequestException(reviewError);

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
      void this.analytics.trackServerEvent({
        userId,
        ...dateCompletedEvent(date),
      });
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
   *
   * `clientId` (Date 02 J): a resend of a message the server already has -- its first answer was
   * lost -- is answered with that copy and adds nothing, so the match receives it exactly once. It is
   * looked up before the date's checks: a message that landed before the date ended was sent.
   */
  async sendChatMessage(
    dateId: string,
    userId: string,
    reason: ChatReason | null,
    body: string,
    clientId: string | null = null,
  ): Promise<DateChatMessage> {
    if (reason !== null && !SENDABLE_REASONS.has(reason)) {
      throw new BadRequestException(
        'This reason opens the change picker; it is never a message',
      );
    }
    const date = await this.participantDate(dateId, userId);
    const sent = (): Promise<DateChatMessage | null> =>
      clientId === null
        ? Promise.resolve(null)
        : this.chat.findOne({
            where: { dateId: date.id, senderId: userId, clientId },
          });
    const already = await sent();
    if (already) return already;
    if (date.status !== DateStatus.Confirmed) {
      throw new ConflictException('The chat has closed: the date is over');
    }
    const windowError = chatWindowError(date.scheduledAt);
    if (windowError) throw new BadRequestException(windowError);

    try {
      return await this.chat.save(
        this.chat.create({
          dateId: date.id,
          senderId: userId,
          reason,
          body,
          clientId,
        }),
      );
    } catch (error) {
      // Two sends of one message at once: the unique index let one in. Answer with it.
      const winner = isUniqueViolation(error) ? await sent() : null;
      if (winner) return winner;
      throw error;
    }
  }

  /**
   * The chat messages on a date, oldest first, and when the OTHER person last read the chat (the
   * receipt on the caller's own messages, Date 02). Only a participant may read them.
   */
  async listChatMessages(
    dateId: string,
    userId: string,
  ): Promise<{ messages: DateChatMessage[]; otherReadAt: Date | null }> {
    const date = await this.participantDate(dateId, userId);
    const messages = await this.chat.find({
      where: { dateId },
      order: { createdAt: 'ASC' },
    });
    const otherReadAt =
      date.userAId === userId ? date.bChatReadAt : date.aChatReadAt;
    return { messages, otherReadAt };
  }

  /**
   * Sends `coordinate_unlocked` for every date whose meet − 1:30 has come (Date 01, SHOWUP-193).
   * Run once a minute by the unlock job. Returns how many dates unlocked.
   *
   * ONCE PER DATE, HOWEVER MANY WORKERS RUN IT: the stamp is set by a conditional UPDATE that only
   * an unstamped row passes, and only the rows it returned are reported. Both people get the event.
   */
  async unlockDueCoordinates(now: Date = new Date()): Promise<number> {
    const candidates = await this.dates.find({
      where: {
        status: DateStatus.Confirmed,
        coordinateUnlockedAt: IsNull(),
        scheduledAt: Between(now, new Date(now.getTime() + 90 * 60_000)),
      },
    });
    const due = dueForUnlock(candidates, now);
    if (due.length === 0) return 0;
    const [stamped] = await this.dates.query<
      [
        Array<{ user_a_id: string; user_b_id: string; scheduled_at: Date }>,
        number,
      ]
    >(
      `UPDATE "dates" SET "coordinate_unlocked_at" = $1
        WHERE "id" = ANY($2::uuid[]) AND "coordinate_unlocked_at" IS NULL
        RETURNING "user_a_id", "user_b_id", "scheduled_at"`,
      [now, due],
    );
    for (const d of stamped) {
      const event = coordinateUnlockedEvent(
        minutesBeforeStart(new Date(d.scheduled_at), now),
      );
      for (const userId of [d.user_a_id, d.user_b_id]) {
        void this.analytics.trackServerEvent({ userId, ...event });
      }
    }
    return stamped.length;
  }

  /** Stamps the caller's read time on the date's chat (Date 01). Only a participant may. */
  async markChatRead(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    const date = await this.participantDate(dateId, userId);
    await this.dates.update(
      { id: date.id },
      date.userAId === userId ? { aChatReadAt: now } : { bChatReadAt: now },
    );
  }

  /**
   * The caller tapped "Open the chat" on this date's tutorial (Date 02). The first tap is the one
   * kept; later calls change nothing.
   */
  async markChatTutorialSeen(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    const date = await this.participantDate(dateId, userId);
    const isA = date.userAId === userId;
    if ((isA ? date.aChatTutorialAt : date.bChatTutorialAt) !== null) return;
    await this.dates.update(
      { id: date.id },
      isA ? { aChatTutorialAt: now } : { bChatTutorialAt: now },
    );
  }

  /**
   * Ends the date for both people from the chat (Date 02): only a `confirmed` date with an "I can't
   * make it today" message in its thread, at any time until it has ended -- there is no cut-off at the
   * meet time. No review follows and nobody's Show-up Rate moves: nothing here touches a rating, a
   * confirmation or a no-show field. Both people are released: the date lock by the status, and
   * Search's "has a date" by the pair's match, which is cancelled with it.
   * `date_ended_in_chat` is sent once; `date_cancelled` never is.
   */
  async endInChat(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<EndedInChatDto> {
    const date = await this.participantDate(dateId, userId);
    if (date.status !== DateStatus.Confirmed) {
      throw new ConflictException('This date has already ended');
    }
    const messages = await this.chat.find({
      where: { dateId: date.id },
      order: { createdAt: 'ASC' },
      select: { senderId: true, reason: true, createdAt: true },
    });
    const from = cantMakeItFrom(messages, userId);
    if (from === null) {
      throw new ConflictException(
        'A date can be ended from the chat only after an "I can\'t make it today" message',
      );
    }
    // Conditional on the status, so a second tap -- or the other person's -- ends it once.
    const ended = await this.dates.update(
      { id: date.id, status: DateStatus.Confirmed },
      { status: DateStatus.EndedInChat, endedById: userId, endedAt: now },
    );
    if (!ended.affected) {
      throw new ConflictException('This date has already ended');
    }
    // The pair's match ends with the date. Search, Likes and Instant read an active match from the
    // open Check-In as "has a date", so the date's status alone left both shut out until their
    // Check-Ins closed, up to 48 h (the SHOWUP-194 review). Dates are saved with no match id, so
    // the pair finds it.
    const [userAId, userBId] = orderedUserPair(date.userAId, date.userBId);
    await this.matches.update(
      { userAId, userBId, status: MatchStatus.Active },
      { status: MatchStatus.Cancelled },
    );
    await this.logChange(
      date.id,
      DateStatus.Confirmed,
      DateStatus.EndedInChat,
      userId,
    );
    void this.analytics.trackServerEvent({
      userId,
      ...dateEndedInChatEvent(
        date.id,
        from,
        minutesBeforeStart(date.scheduledAt, now),
      ),
    });

    const partnerId = date.userAId === userId ? date.userBId : date.userAId;
    const profile = await this.profiles.findOne({
      where: { userId: partnerId },
    });
    return {
      dateId: date.id,
      name: firstName(profile?.displayName ?? null),
      endedByYou: true,
    };
  }

  /** The caller has seen their outcome screen for a date ended in the chat (Date 02). Idempotent. */
  async markEndSeen(
    dateId: string,
    userId: string,
    now: Date = new Date(),
  ): Promise<void> {
    const date = await this.participantDate(dateId, userId);
    await this.dates.update(
      { id: date.id },
      date.userAId === userId ? { aEndSeenAt: now } : { bEndSeenAt: now },
    );
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
