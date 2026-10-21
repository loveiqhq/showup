import { EntityManager, IsNull, MoreThan } from 'typeorm';

import { FLOW_POSITION_COMPLETE } from '../../profiles/util/flow-position';
import { CheckInWindow } from '../entities/check-in-window.entity';

/**
 * The user's current Check-In window: an unfinished one, or a finished one still inside its 48
 * hours. Opens a new, unfinished one when there is neither -- but only once profile creation is
 * complete, because a window is "opened when profile creation completes" (story 01).
 *
 * THE 48 HOURS START AT `Find my date`, NOT HERE. Decided by the product owner on 21 October 2026:
 * a Check-In left halfway starts again from the beginning next time, so the window is not counted
 * until the Check-In is finished. An unfinished window therefore has no closing time at all
 * (`closesAt` null) and stays the user's window however long they take; the commit sets
 * `closesAt` to 48 hours after itself. Until then the window was opened here with its 48 hours
 * already running, so a Check-In finished 40 hours after it was first opened went inactive 8
 * hours later -- before the hours it had just picked.
 *
 * MUST RUN UNDER THE USER'S PROFILE ROW LOCK (`SELECT … FOR UPDATE` on `profiles`). Two requests
 * racing here would otherwise open two windows. Every caller holds it: `ProfilesService.update`
 * (which opens the first window when the flow position reaches the end) and this service.
 */
export async function ensureCheckInWindow(
  em: EntityManager,
  userId: string,
  flowPosition: string | null,
  now: Date = new Date(),
): Promise<CheckInWindow | null> {
  const windows = em.getRepository(CheckInWindow);
  // Either clause: SQL compares a null `closes_at` as unknown, so an unfinished window is found by
  // the first and never by the second.
  const current = await windows.findOne({
    where: [
      { userId, committedAt: IsNull() },
      { userId, closesAt: MoreThan(now) },
    ],
    order: { openedAt: 'DESC' },
  });
  if (current) return current;
  if (flowPosition !== FLOW_POSITION_COMPLETE) return null;
  return windows.save(
    windows.create({
      userId,
      openedAt: now,
      closesAt: null,
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    }),
  );
}
