import {
  BadRequestException,
  Injectable,
  NotFoundException,
  UnprocessableEntityException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource, EntityManager, IsNull, Not } from 'typeorm';

import { AnalyticsService } from '../analytics/analytics.service';
import { checkInCreatedEvent } from '../analytics/events/server-events';
import { Profile } from '../profiles/entities/profile.entity';
import {
  CheckInStateDto,
  CommitCheckInDto,
  FilterSettingsDto,
  SaveCheckInAnswersDto,
  SaveFilterSettingsDto,
} from './dto/check-in-flow.dto';
import { CheckIn } from './entities/check-in.entity';
import { CheckInProfile } from './entities/check-in-profile.entity';
import { CheckInWindow } from './entities/check-in-window.entity';
import { ensureCheckInWindow } from './util/check-in-window';
import { FilterSettings } from './entities/filter-settings.entity';
import { CheckInStatus, DEFAULT_PREPARATION_MINUTES } from './util/check-in';
import {
  checkInClosesAt,
  CHECK_IN_VIBES,
  checkInGenderDefault,
  type CheckInVibe,
  FILTER_AGE_MAX,
  FILTER_AGE_MIN,
  FILTER_DISTANCE_DEFAULT,
  graphemeLength,
  HourSlot,
  normaliseUplift,
  slotsError,
  slotsToWindows,
  upcomingSlots,
  UPLIFT_CONTACT_ERROR,
  UPLIFT_CONTACT_ERROR_CODE,
  NO_HOURS_ERROR_CODE,
  UPLIFT_MAX_GRAPHEMES,
  upliftContact,
} from './util/check-in-flow';

/** §31 values de-duplicated and in list order -- the order `vibe_ids` is sent and stored in. */
export function canonicalVibes(vibes: CheckInVibe[]): CheckInVibe[] {
  const picked = new Set(vibes);
  return CHECK_IN_VIBES.filter((v) => picked.has(v));
}

/**
 * The 48hrs Check-In flow (SHOWUP-177, stories 178 to 183): the state the client opens with, the
 * intro-passed flag, the last-answer store, the filter settings and the commit.
 *
 * THE COMMIT IS THE ONLY WRITE OF A CHECK-IN. Nothing is kept as a draft (epic rule 3): the hours and
 * the uplift exist only on the device until `Find my date`. Motivation and Vibe are saved as "last
 * answers" on each accepted Continue; the filter settings are the user's own record, shared with
 * Search.
 */
@Injectable()
export class CheckInFlowService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
    private readonly analytics: AnalyticsService,
  ) {}

  /** What the client needs before the first Check-In screen. Opens a window if one is due. */
  async state(
    userId: string,
    now: Date = new Date(),
  ): Promise<CheckInStateDto> {
    return this.dataSource.transaction(async (em) => {
      const profile = await this.lockProfile(em, userId);
      const window = await ensureCheckInWindow(
        em,
        userId,
        profile?.flowPosition ?? null,
        now,
      );
      return this.stateOf(em, userId, profile, window);
    });
  }

  /** The intro's CTA: "Intro-passed flag, server-side -- set by A's CTA, survives a reinstall". */
  async markIntroPassed(userId: string): Promise<void> {
    await this.dataSource.query(
      `INSERT INTO "check_in_profiles" ("user_id", "intro_passed_at") VALUES ($1, now())
       ON CONFLICT ("user_id") DO UPDATE
         SET "intro_passed_at" = COALESCE("check_in_profiles"."intro_passed_at", now()),
             "updated_at" = now()`,
      [userId],
    );
  }

  /**
   * The last accepted Motivation and/or Vibe, saved on that step's Continue (stories 03 and 04).
   * Keyed to the user, not the window: it pre-selects the step next time.
   */
  async saveAnswers(userId: string, dto: SaveCheckInAnswersDto): Promise<void> {
    if (dto.motivation == null && dto.vibes == null) return;
    await this.dataSource.transaction(async (em) => {
      const repo = em.getRepository(CheckInProfile);
      const row =
        (await repo.findOne({ where: { userId } })) ??
        repo.create({
          userId,
          introPassedAt: null,
          lastMotivation: null,
          lastVibes: null,
        });
      if (dto.motivation != null) row.lastMotivation = dto.motivation;
      if (dto.vibes != null) row.lastVibes = canonicalVibes(dto.vibes);
      await repo.save(row);
    });
  }

  /** The filter settings -- story 05's Continue, and Search's filter sheet. 65 is stored as null. */
  async saveFilters(
    userId: string,
    dto: SaveFilterSettingsDto,
  ): Promise<FilterSettingsDto> {
    const maxAge =
      dto.maxAge == null || dto.maxAge >= FILTER_AGE_MAX ? null : dto.maxAge;
    if (maxAge != null && maxAge < dto.minAge) {
      throw new BadRequestException(
        'The maximum age cannot be below the minimum age',
      );
    }
    const repo = this.dataSource.getRepository(FilterSettings);
    const saved = await repo.save(
      repo.create({
        userId,
        meetGender: [...new Set(dto.meetGender)],
        minAge: dto.minAge,
        maxAge,
        distanceKm: dto.distanceKm,
      }),
    );
    return filterDto(saved);
  }

  /**
   * `Find my date` (story 06): the whole Check-In at once, idempotent per window.
   *
   * - A window already committed answers with the same state and writes nothing -- a retry after a
   *   timeout never makes a second Check-In (epic rule 10).
   * - The 48 hours start here: `closesAt` is set to 48 hours after the commit. An unfinished window
   *   has no clock, so it never expires under a flow left open; an unfinished window that is no
   *   longer the user's current one commits into the current one. Hours that have started are
   *   dropped silently, so a Check-In whose Today hours all passed is saved with Tomorrow only.
   * - The uplift is checked for contact info here too; a refusal is a 422 with the distinct error
   *   `uplift_contact_info`, which the client shows as the contact-info state, not a failed save.
   * - Earlier Check-Ins' hours still live are retired: one active Check-In at a time.
   */
  async commit(
    userId: string,
    dto: CommitCheckInDto,
    now: Date = new Date(),
  ): Promise<{ state: CheckInStateDto; created: boolean }> {
    const uplift = normaliseUplift(dto.uplift);
    if (uplift != null && graphemeLength(uplift) > UPLIFT_MAX_GRAPHEMES) {
      throw new BadRequestException(
        `The reason can be at most ${UPLIFT_MAX_GRAPHEMES} characters`,
      );
    }
    if (uplift != null && upliftContact(uplift) != null) {
      throw new UnprocessableEntityException({
        statusCode: 422,
        message: UPLIFT_CONTACT_ERROR,
        error: UPLIFT_CONTACT_ERROR_CODE,
      });
    }
    if ((dto.latitude == null) !== (dto.longitude == null)) {
      throw new BadRequestException(
        'Send both latitude and longitude, or neither',
      );
    }
    const slots: HourSlot[] = dto.slots.map((s) => ({
      startsAt: new Date(s.startsAt),
      tier: s.tier,
    }));
    const slotError = slotsError(slots, now);
    if (slotError) throw new BadRequestException(slotError);

    const result = await this.dataSource.transaction(async (em) => {
      const profile = await this.lockProfile(em, userId);
      const windows = em.getRepository(CheckInWindow);
      let window = await windows.findOne({
        where: { id: dto.windowId, userId },
      });
      if (!window) throw new NotFoundException('No such Check-In window');

      // NOT THE CURRENT WINDOW: the Check-In goes into the user's current one instead. With no clock
      // on an unfinished window this is rare -- an older unfinished window -- but a commit must
      // never land in a window the user's state no longer reads.
      if (!window.committedAt) {
        const current = await ensureCheckInWindow(
          em,
          userId,
          profile?.flowPosition ?? null,
          now,
        );
        if (current && current.id !== window.id) window = current;
      }
      if (window.committedAt) {
        return {
          state: await this.currentState(em, userId, profile, now),
          created: false,
        };
      }

      // EVERY PICKED HOUR HAS STARTED -- the flow sat open past them. Committing would unlock Search
      // with no hours at all, so it is refused. After the replay above: a retry of a Check-In that
      // saved still answers with it, however late it arrives.
      const upcoming = upcomingSlots(slots, now);
      if (upcoming.length === 0) {
        throw new BadRequestException({
          statusCode: 400,
          message:
            'Every picked hour has started. Pick an hour that is still ahead.',
          error: NO_HOURS_ERROR_CODE,
        });
      }

      // One active Check-In at a time: the hours of an earlier one that are still live end here.
      await em.query(
        `UPDATE "check_ins" SET "status" = $1, "updated_at" = now()
         WHERE "user_id" = $2 AND "status" = $3 AND "availability_end" > $4
           AND "check_in_window_id" IS DISTINCT FROM $5`,
        [
          CheckInStatus.Cancelled,
          userId,
          CheckInStatus.Available,
          now,
          window.id,
        ],
      );

      const checkIns = em.getRepository(CheckIn);
      for (const span of slotsToWindows(upcoming)) {
        await checkIns.save(
          checkIns.create({
            userId,
            status: CheckInStatus.Available,
            availabilityStart: span.start,
            availabilityEnd: span.end,
            preparationMinutes: DEFAULT_PREPARATION_MINUTES,
            windowId: window.id,
            tier: span.tier,
          }),
        );
      }
      if (dto.latitude != null && dto.longitude != null) {
        await em.query(
          `UPDATE "check_ins" SET "location" = ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
           WHERE "check_in_window_id" = $3`,
          [dto.longitude, dto.latitude, window.id],
        );
      }

      window.committedAt = now;
      // THE 48 HOURS START NOW, not when the window opened (see `ensureCheckInWindow`).
      window.closesAt = checkInClosesAt(now);
      window.motivation = dto.motivation;
      window.vibes = canonicalVibes(dto.vibes);
      window.uplift = uplift;
      // Only with a place: a district with no location would name somewhere nothing measures from.
      window.district =
        dto.latitude != null ? dto.district?.trim() || null : null;
      window = await windows.save(window);

      // The answers committed are the last answers, too.
      await em.query(
        `INSERT INTO "check_in_profiles" ("user_id", "last_motivation", "last_vibes")
         VALUES ($1, $2, $3)
         ON CONFLICT ("user_id") DO UPDATE
           SET "last_motivation" = EXCLUDED."last_motivation",
               "last_vibes" = EXCLUDED."last_vibes",
               "updated_at" = now()`,
        [userId, window.motivation, window.vibes],
      );
      return {
        state: await this.currentState(em, userId, profile, now),
        created: true,
      };
    });

    // `check_in_created` -- THE one commit event (story 06) -- once per Check-In, never on a replay.
    if (result.created) {
      void this.analytics.trackServerEvent({
        userId,
        ...checkInCreatedEvent({ hasLocation: dto.latitude != null }),
      });
    }

    return result;
  }

  /**
   * The commit's answer: the state as a GET would read it now, so `active` always describes the
   * CURRENT window -- built from the committed window instead, a replay of a Check-In whose window
   * has since closed would claim a live Check-In. Read inside the commit's transaction: read after
   * it, a failure there answered 500 for a Check-In that had saved.
   */
  private async currentState(
    em: EntityManager,
    userId: string,
    profile: Profile | null,
    now: Date,
  ): Promise<CheckInStateDto> {
    const window = await ensureCheckInWindow(
      em,
      userId,
      profile?.flowPosition ?? null,
      now,
    );
    return this.stateOf(em, userId, profile, window);
  }

  /** The user's profile, row-locked for the rest of the transaction (see `ensureCheckInWindow`). */
  private async lockProfile(
    em: EntityManager,
    userId: string,
  ): Promise<Profile | null> {
    // The same lock `ProfilesService.update` takes, so the two serialise on one row.
    return em.findOne(Profile, {
      where: { userId },
      lock: { mode: 'pessimistic_write' },
    });
  }

  private async stateOf(
    em: EntityManager,
    userId: string,
    profile: Profile | null,
    window: CheckInWindow | null,
  ): Promise<CheckInStateDto> {
    const checkInProfile = await em.findOne(CheckInProfile, {
      where: { userId },
    });
    const committedBefore =
      (await em.count(CheckInWindow, {
        where: { userId, committedAt: Not(IsNull()) },
      })) > 0;
    const saved = await em.findOne(FilterSettings, { where: { userId } });
    return {
      windowId: window?.id ?? null,
      introPassed: checkInProfile?.introPassedAt != null,
      active: window?.committedAt != null,
      firstCheckIn: !committedBefore,
      lastMotivation: checkInProfile?.lastMotivation ?? null,
      lastVibes: checkInProfile?.lastVibes ?? [],
      filters: saved
        ? filterDto(saved)
        : {
            meetGender: checkInGenderDefault(
              profile?.gender ?? null,
              profile?.orientation ?? null,
            ),
            minAge: FILTER_AGE_MIN,
            maxAge: null,
            distanceKm: FILTER_DISTANCE_DEFAULT,
          },
      filtersSaved: saved != null,
    };
  }
}

function filterDto(f: FilterSettings): FilterSettingsDto {
  return {
    meetGender: f.meetGender,
    minAge: f.minAge,
    maxAge: f.maxAge,
    distanceKm: f.distanceKm,
  };
}
