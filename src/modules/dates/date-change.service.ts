import { Injectable } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

import { changeSlots, localDay } from './util/change-slots';

/** The parts of a date the change rules read. */
export interface ChangeableDate {
  userAId: string;
  userBId: string;
  /** The binding meet time: the last agreed one, which is what `dates.scheduled_at` always holds. */
  scheduledAt: Date;
}

/** Date 05 (SHOWUP-196): reads behind the change-time and change-place surfaces. */
@Injectable()
export class DateChangeService {
  constructor(@InjectDataSource() private readonly db: DataSource) {}

  /**
   * The meet times `viewerId` can suggest for this date (R6): 15-minute starts after the binding
   * time that fall inside the MATCH's free hours (the other person's check-in runs from their latest
   * committed window that touch the date's local day in `tz`), before local midnight. Empty when
   * the match has no hours left that day, which hides `Change meet time` only.
   */
  async matchSlots(
    date: ChangeableDate,
    viewerId: string,
    tz: string,
  ): Promise<Date[]> {
    const matchId = date.userAId === viewerId ? date.userBId : date.userAId;
    const day = localDay(date.scheduledAt, tz);
    const runs = await this.db.query<Array<{ start: Date; end: Date }>>(
      `SELECT ci."availability_start" AS "start", ci."availability_end" AS "end"
         FROM "check_ins" ci
        WHERE ci."user_id" = $1
          AND ci."check_in_window_id" = (
                SELECT w."id" FROM "check_in_windows" w
                 WHERE w."user_id" = $1 AND w."committed_at" IS NOT NULL
                 ORDER BY w."committed_at" DESC LIMIT 1)
          AND ci."availability_end" > $2 AND ci."availability_start" < $3
        ORDER BY ci."availability_start"`,
      [matchId, day.start, day.end],
    );
    return changeSlots(
      date.scheduledAt,
      runs.map((r) => ({ start: new Date(r.start), end: new Date(r.end) })),
      tz,
    );
  }
}
