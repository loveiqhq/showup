import { chatOpensAt, ChatReason } from '../../dates/util/pre-date-chat';
import { SearchProfileDto } from '../../search/dto/search.dto';
import { roundedDistanceKm } from '../../search/util/search';
import {
  ActiveDateDto,
  DateMatchDto,
  DateUnreadDto,
} from '../dto/active-date.dto';

/** One chat message, as far as "is it unread" needs it. */
export interface DateMessageRow {
  senderId: string;
  reason: ChatReason | string | null;
  body: string | null;
  createdAt: Date;
}

/** Everything the Date screen's payload is built from, already loaded. */
export interface ActiveDateSource {
  dateId: string;
  /** The latest agreed meet time (today: the date row's own). */
  scheduledAt: Date;
  venue: { name: string; address: string | null } | null;
  /** The match, built by Search's own builder, so hidden fields are already absent. */
  partner: SearchProfileDto;
  /** Between the two people's latest located Check-Ins; null when either has none. */
  distanceMeters: number | null;
  viewer: { motivation: string | null; vibes: string[]; interests: string[] };
  unread: DateUnreadDto | null;
  checkedIn: boolean;
  meetDay: 'today' | 'tomorrow';
  now: Date;
}

/**
 * ONLY WHAT MATCHES IS SHOWN (07a rule 7), so the server sends the overlap rather than both lists:
 * the app never has to know the vibes or interests the two do not share. The match's order, as
 * Search's shared rows use.
 */
export function sharedInOrder(
  theirs: readonly string[],
  mine: readonly string[],
): string[] {
  const own = new Set(mine);
  return theirs.filter((x) => own.has(x));
}

/** The other person's messages after the viewer last read the thread; null when there are none. */
export function unreadOf(
  messages: readonly DateMessageRow[],
  viewerId: string,
  readAt: Date | null,
): DateUnreadDto | null {
  const after = readAt?.getTime() ?? Number.NEGATIVE_INFINITY;
  const unread = messages
    .filter((m) => m.senderId !== viewerId && m.createdAt.getTime() > after)
    .sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
  const newest = unread.at(-1);
  if (!newest) return null;
  return {
    count: unread.length,
    reason: (newest.reason as ChatReason | null) ?? null,
    body: newest.body,
    sentAt: newest.createdAt.toISOString(),
  };
}

/** The `GET /me/date` payload. Pure, so every rule above is tested without a database. */
export function toActiveDate(src: ActiveDateSource): ActiveDateDto {
  const unlockAt = chatOpensAt(src.scheduledAt);
  const p = src.partner;
  const match: DateMatchDto = {
    userId: p.userId,
    name: p.name,
    attendedCount: p.attendedCount,
  };
  if (p.age != null) match.age = p.age;
  if (p.district) match.district = p.district;
  if (src.distanceMeters != null)
    match.distanceKm = roundedDistanceKm(src.distanceMeters);
  const photo = p.photos[0]?.url;
  if (photo) match.photoUrl = photo;

  return {
    id: src.dateId,
    meetAt: src.scheduledAt.toISOString(),
    meetDay: src.meetDay,
    unlockAt: unlockAt.toISOString(),
    coordinateOpen: src.now.getTime() >= unlockAt.getTime(),
    place: src.venue
      ? src.venue.address
        ? { name: src.venue.name, address: src.venue.address }
        : { name: src.venue.name }
      : null,
    match,
    motivations: { you: src.viewer.motivation, them: p.motivation ?? null },
    sharedVibes: sharedInOrder(p.vibes ?? [], src.viewer.vibes),
    sharedInterests: sharedInOrder(p.interests ?? [], src.viewer.interests),
    unread: src.unread,
    checkedIn: src.checkedIn,
  };
}
