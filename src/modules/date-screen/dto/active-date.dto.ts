import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

import { ChatReason } from '../../dates/util/pre-date-chat';

/** The agreed meeting place. `address` is absent for a venue stored without one. */
export class DatePlaceDto {
  @ApiProperty({ example: 'Cafe Krone' })
  name: string;

  @ApiPropertyOptional({ example: 'Oranienstrasse 42, Kreuzberg' })
  address?: string;
}

/**
 * The person the viewer is meeting, as far as the Date screen shows them. A hidden age, an unknown
 * district or distance, and a missing photo have no key: no key, no text on the screen.
 */
export class DateMatchDto {
  @ApiProperty()
  userId: string;

  @ApiProperty({ description: 'First name, as Search sends it' })
  name: string;

  @ApiPropertyOptional({ type: 'integer' })
  age?: number;

  @ApiPropertyOptional({ description: "Their Check-In's district" })
  district?: string;

  @ApiPropertyOptional({
    type: 'integer',
    description:
      'Whole km, 0 meaning under 1 km -- rounded as Search rounds it; never the exact distance',
  })
  distanceKm?: number;

  @ApiPropertyOptional({ description: 'Their first photo that may be shown' })
  photoUrl?: string;

  @ApiProperty({
    type: 'integer',
    description:
      'Dates both people confirmed happened (ShowUpCount). Never a rate',
  })
  attendedCount: number;
}

/** Both §30 motivations. The app shows the intent line only when they are equal. */
export class DateMotivationsDto {
  @ApiProperty({ type: String, nullable: true })
  you: string | null;

  @ApiProperty({ type: String, nullable: true })
  them: string | null;
}

/** The newest unread message from the match, and how many are unread. */
export class DateUnreadDto {
  @ApiProperty({ type: 'integer', minimum: 1 })
  count: number;

  @ApiProperty({ enum: ChatReason, enumName: 'ChatReason' })
  reason: ChatReason;

  @ApiProperty({ type: String, nullable: true })
  body: string | null;

  @ApiProperty({ format: 'date-time' })
  sentAt: string;
}

/**
 * `GET /me/date`: everything the Date screen (Date 01, SHOWUP-193) renders.
 *
 * TIME AND PLACE ARE THE LATEST AGREED VALUES. Today that is the date row itself; Date 05 adds
 * suggestions, which never replace these until agreed.
 */
export class ActiveDateDto {
  @ApiProperty()
  id: string;

  @ApiProperty({
    format: 'date-time',
    description: 'The latest agreed meet time',
  })
  meetAt: string;

  @ApiProperty({
    enum: ['today', 'tomorrow'],
    description:
      "In the viewer's time zone, by the Check-In's 7am day (as Search)",
  })
  meetDay: 'today' | 'tomorrow';

  @ApiProperty({
    format: 'date-time',
    description: 'meetAt - 90 minutes: when Coordinate opens',
  })
  unlockAt: string;

  @ApiProperty({ description: 'True from unlockAt on' })
  coordinateOpen: boolean;

  @ApiProperty({ type: DatePlaceDto, nullable: true })
  place: DatePlaceDto | null;

  @ApiProperty({ type: DateMatchDto })
  match: DateMatchDto;

  @ApiProperty({ type: DateMotivationsDto })
  motivations: DateMotivationsDto;

  @ApiProperty({
    type: [String],
    description: '§31 vibes both people chose, in the match’s order',
  })
  sharedVibes: string[];

  @ApiProperty({
    type: [String],
    description: 'Interest tags both people have, in the match’s order',
  })
  sharedInterests: string[];

  @ApiProperty({ type: DateUnreadDto, nullable: true })
  unread: DateUnreadDto | null;

  @ApiProperty({
    description:
      "The viewer's own arrival check-in. Always false until Date 03 adds the check-in store",
  })
  checkedIn: boolean;
}

/**
 * The reply to `GET /me/date`: `date` is null when the viewer has no active date. A body rather
 * than an empty 204, as the Check-In read's `active` flag is, so both generated clients read one
 * shape.
 */
export class ActiveDateResponseDto {
  @ApiProperty({ type: ActiveDateDto, nullable: true })
  date: ActiveDateDto | null;
}
