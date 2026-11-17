import { haversineMeters, type LatLng } from '../../location/location.geo';
import { localParts } from '../../search/util/search';
import {
  ChangePendingDto,
  ChangePlaceWasDto,
  ChangeStateDto,
  ChangeSuggestionDto,
} from '../dto/change.dto';
import {
  ChangeKind,
  ChangeStatus,
} from '../entities/date-change-suggestion.entity';
import { changeDeadline, cutoffPassed, type PlaceType } from './change-rules';

/** A suggestion row as far as the payload, the thread and the rules read it (no coordinates). */
export interface ChangeRow {
  id: string;
  senderId: string;
  kind: ChangeKind;
  valueTime: Date | null;
  placeId: string | null;
  placeName: string | null;
  placeType: PlaceType | null;
  placeAddress: string | null;
  wasTime: Date;
  wasPlaceName: string | null;
  status: ChangeStatus;
  deadline: Date;
  createdAt: Date;
  answeredAt: Date | null;
  expiredAt: Date | null;
  expiredBindingTime: Date | null;
  expiredBindingPlaceName: string | null;
}

/** Why a suggestion is refused with 409 (R7); null when it may be made. */
export type SuggestRefusal = 'ended' | 'cutoff' | 'checked_in';

export function suggestRefusal(input: {
  status: string;
  bindingAt: Date;
  now: Date;
  anyCommittedCheckIn: boolean;
}): SuggestRefusal | null {
  if (input.status !== 'confirmed') return 'ended';
  if (cutoffPassed(input.bindingAt, input.now)) return 'cutoff';
  if (input.anyCommittedCheckIn) return 'checked_in';
  return null;
}

/** Why `I agree` is refused with 409; null when the viewer may agree (R15, R22). */
export type AgreeRefusal = 'sender' | 'not_pending' | 'expired' | 'checked_in';

/**
 * Only the receiver of a PENDING suggestion agrees. A suggestion still pending past its deadline
 * (the expiry job runs once a minute) is already expired in effect, so it is refused rather than
 * agreed on a race; a committed check-in freezes the binding values as it freezes suggesting.
 */
export function agreeRefusal(input: {
  status: ChangeStatus;
  senderId: string;
  viewerId: string;
  deadline: Date;
  now: Date;
  anyCommittedCheckIn: boolean;
}): AgreeRefusal | null {
  if (input.status !== ChangeStatus.Pending) return 'not_pending';
  if (input.senderId === input.viewerId) return 'sender';
  if (input.now.getTime() >= input.deadline.getTime()) return 'expired';
  if (input.anyCommittedCheckIn) return 'checked_in';
  return null;
}

/** `change_suggested.replaces`: whose open same-kind suggestion this one replaced. */
export function replacesOf(
  replacedSenderId: string | null,
  senderId: string,
): 'none' | 'own' | 'theirs' {
  if (replacedSenderId === null) return 'none';
  return replacedSenderId === senderId ? 'own' : 'theirs';
}

/** A place as the binding-venue check reads it. */
export interface VenueIdentity {
  placeId: string | null;
  name: string;
  location: LatLng;
}

/** The distance within which a same-named venue without a place id is the same place. */
const SAME_PLACE_METERS = 50;

/**
 * Whether a candidate place IS the date's current meet place: by `place_id` when the venue has one,
 * otherwise by the same name within 50 m (a seeded venue has no id). A suggestion of it is refused,
 * and the places list leaves it out.
 */
export function isBindingVenue(
  venue: VenueIdentity | null,
  candidate: VenueIdentity,
): boolean {
  if (!venue) return false;
  if (venue.placeId) return venue.placeId === candidate.placeId;
  return (
    venue.name.trim().toLowerCase() === candidate.name.trim().toLowerCase() &&
    haversineMeters(venue.location, candidate.location) <= SAME_PLACE_METERS
  );
}

/** Local `HH:mm` of an instant in a zone, as a push or a clock reads it. */
export function clockLabel(instant: Date, zone: string): string {
  const p = localParts(instant, zone);
  const two = (n: number): string => String(n).padStart(2, '0');
  return `${two(p.hour)}:${two(p.minute)}`;
}

export function toPendingDto(
  row: ChangeRow,
  viewerId: string,
): ChangePendingDto {
  const dto: ChangePendingDto = {
    id: row.id,
    kind: row.kind,
    byYou: row.senderId === viewerId,
    wasTime: row.wasTime.toISOString(),
    wasPlace: row.wasPlaceName ? { name: row.wasPlaceName } : null,
    deadline: row.deadline.toISOString(),
    createdAt: row.createdAt.toISOString(),
  };
  if (row.kind === ChangeKind.Time && row.valueTime) {
    dto.time = row.valueTime.toISOString();
  }
  if (row.kind === ChangeKind.Place && row.placeId && row.placeName) {
    dto.place = {
      placeId: row.placeId,
      name: row.placeName,
      type: row.placeType as PlaceType,
      address: row.placeAddress ?? '',
    };
  }
  return dto;
}

/** A suggestion with its history, for the chat thread (system lines are derived from it, R9). */
export function toSuggestionDto(
  row: ChangeRow,
  viewerId: string,
): ChangeSuggestionDto {
  const dto: ChangeSuggestionDto = {
    ...toPendingDto(row, viewerId),
    status: row.status,
  };
  if (row.answeredAt) dto.answeredAt = row.answeredAt.toISOString();
  if (row.expiredAt) dto.expiredAt = row.expiredAt.toISOString();
  if (row.expiredBindingTime) {
    dto.expiredBindingTime = row.expiredBindingTime.toISOString();
  }
  if (row.expiredBindingPlaceName) {
    dto.expiredBindingPlace = { name: row.expiredBindingPlaceName };
  }
  return dto;
}

/**
 * The match's pending suggestion with the earliest deadline, time first on a tie (R8): what the
 * hoisted request card shows. Only the MATCH's own; null when there is none.
 */
export function pickChangeRequest(
  rows: readonly ChangeRow[],
  viewerId: string,
): ChangeRow | null {
  const open = rows
    .filter((r) => r.status === ChangeStatus.Pending && r.senderId !== viewerId)
    .sort(
      (a, b) =>
        a.deadline.getTime() - b.deadline.getTime() ||
        Number(a.kind === ChangeKind.Place) -
          Number(b.kind === ChangeKind.Place),
    );
  return open[0] ?? null;
}

/** The first suggestion of a kind that was agreed: its was-values are the original (R13). */
function firstAgreed(
  rows: readonly ChangeRow[],
  kind: ChangeKind,
): ChangeRow | undefined {
  return rows
    .filter(
      (r) =>
        r.kind === kind && r.status === ChangeStatus.Agreed && r.answeredAt,
    )
    .sort(
      (a, b) =>
        (a.answeredAt as Date).getTime() - (b.answeredAt as Date).getTime(),
    )[0];
}

/**
 * `change` on the Date screen payload (R7, R13). `placeWas` is the venue the first agreed place
 * change replaced, looked up by the caller; it is only used when a place change was agreed.
 */
export function changeStateOf(input: {
  rows: readonly ChangeRow[];
  viewerId: string;
  bindingAt: Date;
  slots: readonly Date[];
  anyCommittedCheckIn: boolean;
  placeWas: ChangePlaceWasDto | null;
}): ChangeStateDto {
  const state: ChangeStateDto = {
    cutoffAt: changeDeadline(input.bindingAt).toISOString(),
    allowed: !input.anyCommittedCheckIn,
    slots: input.slots.map((s) => s.toISOString()),
    pending: input.rows
      .filter((r) => r.status === ChangeStatus.Pending)
      .map((r) => toPendingDto(r, input.viewerId)),
  };
  const time = firstAgreed(input.rows, ChangeKind.Time);
  if (time) state.timeWas = time.wasTime.toISOString();
  if (firstAgreed(input.rows, ChangeKind.Place) && input.placeWas) {
    state.placeWas = input.placeWas;
  }
  return state;
}
