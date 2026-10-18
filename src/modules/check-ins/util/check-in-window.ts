import { EntityManager, MoreThan } from 'typeorm';

import { FLOW_POSITION_COMPLETE } from '../../profiles/util/flow-position';
import { CheckInWindow } from '../entities/check-in-window.entity';
import { CHECK_IN_WINDOW_HOURS } from './check-in-flow';

const HOUR_MS = 60 * 60 * 1000;

/**
 * The user's current Check-In window: the latest one that has not closed. Opens a new one when the
 * last has closed (or none ever opened) -- but only once profile creation is complete, because a
 * window is "opened when profile creation completes" (story 01).
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
  const current = await windows.findOne({
    where: { userId, closesAt: MoreThan(now) },
    order: { openedAt: 'DESC' },
  });
  if (current) return current;
  if (flowPosition !== FLOW_POSITION_COMPLETE) return null;
  return windows.save(
    windows.create({
      userId,
      openedAt: now,
      closesAt: new Date(now.getTime() + CHECK_IN_WINDOW_HOURS * HOUR_MS),
      committedAt: null,
      motivation: null,
      vibes: null,
      uplift: null,
    }),
  );
}
