import { ChangePendingDto, ChangeStateDto } from '../../dates/dto/change.dto';
import { EndReason, partnerScreenFor } from '../../dates/util/date-end';
import { DateStatus } from '../../dates/util/date-lifecycle';
import { chatOpensAt, ChatReason } from '../../dates/util/pre-date-chat';
import { SearchProfileDto } from '../../search/dto/search.dto';
import { roundedDistanceKm } from '../../search/util/search';
import {
  ActiveDateDto,
  DateArrivalDto,
  DateMatchDto,
  DateUnreadDto,
  EndedOutcomeDto,
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
  /** The match's arrival as the viewer may see it (`arrivalOf`). */
  arrival: DateArrivalDto | null;
  /** The viewer has tapped "Open the chat" on this date's tutorial (Date 02). */
  chatTutorialSeen: boolean;
  /** The date's `coordinate_unlocked_at`: set once the chat has opened, by time or by a check-in. */
  coordinateUnlockedAt: Date | null;
  /**
   * Either person has a COMMITTED check-in on this date. A commit opens the chat for good even when
   * its stamp was lost, so the chat never relocks on a failed write (Date 03, 16 Nov).
   */
  anyCommittedCheckIn: boolean;
  meetDay: 'today' | 'tomorrow';
  /** Date 06 / 07: the end and cancel routes are on (`DATE_ENDS_ENABLED`). */
  endsEnabled: boolean;
  /** Date 07: the points a cancel costs, from the rate config. */
  cancelPenaltyPoints: number;
  /** Date 05: the change state and the hoisted request, built by `ChangeSuggestionsService`. */
  change?: ChangeStateDto;
  changeRequest?: ChangePendingDto | null;
  now: Date;
}

/** A date as far as "has this person still to see that it ended in the chat" needs it. */
export interface EndedRow {
  id: string;
  status: DateStatus;
  userAId: string;
  userBId: string;
  endedById: string | null;
  /** The latest agreed meet time: every outcome carries it as `meetAt`. */
  scheduledAt: Date;
  /** Date 06: set when `status` is `ended`. */
  endReason?: EndReason | null;
  /** Date 07: who cancelled, and the points it cost (null on a cancel from before Date 07). */
  cancelledById?: string | null;
  penaltyPoints?: number | null;
  aEndSeenAt: Date | null;
  bEndSeenAt: Date | null;
}

/**
 * The outcome `viewerId` is still to see, or null once they have seen it or when the date did not end
 * (R3). One shape for every reason: `chat` (Date 02: G for the one who ended it, H for the match), and
 * `met` / `partner_no_show` / `uncomfortable` (Date 06), and `cancelled` (Date 07: the canceller sees
 * `endedByYou`, the match sees H). The match of a Date 06 end also gets the Date 08
 * screen, from `end_reason` alone. `partnerName` is the other person's first name.
 */
export function endedOutcomeOf(
  row: EndedRow,
  viewerId: string,
  partnerName: string,
): EndedOutcomeDto | null {
  if (row.status === DateStatus.Cancelled) {
    // Only a Date 07 cancel (it stores its penalty) is reported; older cancels were never shown.
    if (row.penaltyPoints == null || !row.cancelledById) return null;
    const cancelSeen =
      row.userAId === viewerId ? row.aEndSeenAt : row.bEndSeenAt;
    if (cancelSeen) return null;
    return {
      dateId: row.id,
      name: partnerName,
      endedByYou: row.cancelledById === viewerId,
      reason: 'cancelled',
      meetAt: row.scheduledAt.toISOString(),
    };
  }
  if (
    row.status !== DateStatus.EndedInChat &&
    row.status !== DateStatus.Ended &&
    row.status !== DateStatus.TimedOut
  ) {
    return null;
  }
  if (
    (row.status === DateStatus.Ended || row.status === DateStatus.TimedOut) &&
    !row.endReason
  ) {
    return null;
  }
  const seen = row.userAId === viewerId ? row.aEndSeenAt : row.bEndSeenAt;
  if (seen) return null;
  const endedByYou = row.endedById === viewerId;
  const out: EndedOutcomeDto = {
    dateId: row.id,
    name: partnerName,
    endedByYou,
    reason: row.status === DateStatus.EndedInChat ? 'chat' : row.endReason!,
    meetAt: row.scheduledAt.toISOString(),
  };
  // Date 06: only the match of an end gets Date 08. Date 09: nobody ended it, so both get it.
  if (
    (row.status === DateStatus.Ended && !endedByYou) ||
    row.status === DateStatus.TimedOut
  ) {
    out.partnerScreen = partnerScreenFor(row.endReason!);
  }
  return out;
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
    coordinateOpen:
      src.now.getTime() >= unlockAt.getTime() ||
      src.coordinateUnlockedAt != null ||
      src.anyCommittedCheckIn,
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
    arrival: src.arrival,
    chatTutorialSeen: src.chatTutorialSeen,
    endsEnabled: src.endsEnabled,
    cancelPenaltyPoints: src.cancelPenaltyPoints,
    ...(src.change ? { change: src.change } : {}),
    ...(src.changeRequest !== undefined
      ? { changeRequest: src.changeRequest }
      : {}),
  };
}
