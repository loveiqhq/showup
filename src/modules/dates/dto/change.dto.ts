import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBoolean,
  IsEnum,
  IsISO8601,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator';

import {
  ChangeEntry,
  ChangeKind,
  ChangeStatus,
} from '../entities/date-change-suggestion.entity';
import { PLACE_TYPES, type PlaceType } from '../util/change-rules';

/** How a suggestion reached the server: the Coordinate card's `I agree` or the one in the thread. */
export const CHANGE_SURFACES = ['coordinate_card', 'thread'] as const;
export type ChangeSurface = (typeof CHANGE_SURFACES)[number];

/** Body of `POST /dates/:id/suggestions` (Date 05, SHOWUP-196). */
export class SuggestChangeDto {
  @ApiProperty({ enum: ChangeKind, enumName: 'ChangeKind' })
  @IsEnum(ChangeKind)
  kind: ChangeKind;

  @ApiPropertyOptional({
    format: 'date-time',
    description:
      "kind `time`: the new meet time, one of the `change.slots` of the date payload (the match's free hours).",
  })
  @IsOptional()
  @IsISO8601()
  time?: string;

  @ApiPropertyOptional({
    description:
      'kind `place`: the id of a place from `GET /dates/:id/change/places`; the server fetches the place itself, so nothing else about it is trusted from the phone.',
  })
  @IsOptional()
  @IsString()
  @MaxLength(255)
  placeId?: string;

  @ApiProperty({
    enum: ChangeEntry,
    enumName: 'ChangeEntry',
    description:
      'Where the person started the suggestion (analytics `entry`, §42)',
  })
  @IsEnum(ChangeEntry)
  entry: ChangeEntry;

  @ApiPropertyOptional({
    description:
      'kind `place`: picked from a search result rather than the nearby list',
  })
  @IsOptional()
  @IsBoolean()
  fromSearch?: boolean;

  @ApiProperty({
    format: 'uuid',
    description:
      'The id the phone gave this Suggest tap, the same on every resend: a resend is answered with the stored suggestion and fires nothing',
  })
  @IsUUID()
  clientId: string;

  @ApiPropertyOptional({
    description:
      "The person's IANA time zone, for the slot list (as `GET /me/date?tz=`). Absent: UTC, which only matters near local midnight.",
    example: 'Europe/Berlin',
  })
  @IsOptional()
  @IsString()
  @MaxLength(64)
  tz?: string;
}

/** Body of `POST /dates/:id/suggestions/:sid/agree`. */
export class AgreeChangeDto {
  @ApiProperty({ enum: CHANGE_SURFACES })
  @IsEnum(CHANGE_SURFACES)
  surface: ChangeSurface;
}

/** A suggested place, as the snapshot taken when it was suggested. */
export class ChangePlaceDto {
  @ApiProperty()
  placeId: string;

  @ApiProperty()
  name: string;

  @ApiProperty({ enum: PLACE_TYPES })
  type: PlaceType;

  @ApiProperty()
  address: string;
}

/** The name of a place that was, or is, binding. */
export class ChangeWasPlaceDto {
  @ApiProperty()
  name: string;
}

/** A pending suggestion, as the Date screen's `change.pending` and `changeRequest` carry it. */
export class ChangePendingDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ enum: ChangeKind, enumName: 'ChangeKind' })
  kind: ChangeKind;

  @ApiProperty({ description: 'You sent it' })
  byYou: boolean;

  @ApiPropertyOptional({
    format: 'date-time',
    description: 'kind `time`: the suggested meet time',
  })
  time?: string;

  @ApiPropertyOptional({
    type: ChangePlaceDto,
    description: 'kind `place`: the suggested place',
  })
  place?: ChangePlaceDto;

  @ApiProperty({
    format: 'date-time',
    description: 'The meet time in force when this was suggested',
  })
  wasTime: string;

  @ApiProperty({
    type: ChangeWasPlaceDto,
    nullable: true,
    description: 'The meet place in force when this was suggested',
  })
  wasPlace: ChangeWasPlaceDto | null;

  @ApiProperty({
    format: 'date-time',
    description: 'Meet time - 0:30; unanswered by then, it expires',
  })
  deadline: string;

  @ApiProperty({ format: 'date-time' })
  createdAt: string;
}

/** A suggestion with its history, for the chat thread's cards and system lines. */
export class ChangeSuggestionDto extends ChangePendingDto {
  @ApiProperty({ enum: ChangeStatus, enumName: 'ChangeStatus' })
  status: ChangeStatus;

  @ApiPropertyOptional({
    format: 'date-time',
    description: 'When it was agreed',
  })
  answeredAt?: string;

  @ApiPropertyOptional({ format: 'date-time', description: 'When it expired' })
  expiredAt?: string;

  @ApiPropertyOptional({
    format: 'date-time',
    description: 'Expired: the meet time that stayed binding',
  })
  expiredBindingTime?: string;

  @ApiPropertyOptional({
    type: ChangeWasPlaceDto,
    description: 'Expired: the meet place that stayed binding',
  })
  expiredBindingPlace?: ChangeWasPlaceDto;
}

/** The place the Date screen shows as `Changed - was {original}`. */
export class ChangePlaceWasDto {
  @ApiProperty()
  name: string;

  @ApiPropertyOptional()
  address?: string;
}

/** `change` on the `GET /me/date` payload (Date 05). */
export class ChangeStateDto {
  @ApiProperty({
    format: 'date-time',
    description: 'Meet time - 0:30: from here nobody can suggest or agree',
  })
  cutoffAt: string;

  @ApiProperty({
    description: 'False once either person has a committed arrival check-in',
  })
  allowed: boolean;

  @ApiProperty({
    type: [String],
    format: 'date-time',
    description:
      "Meet times you can suggest: 15-minute starts inside the match's free hours, after the meet time, before local midnight. Empty hides `Change meet time` only.",
  })
  slots: string[];

  @ApiPropertyOptional({
    format: 'date-time',
    description: 'The meet time before the first agreed time change',
  })
  timeWas?: string;

  @ApiPropertyOptional({
    type: ChangePlaceWasDto,
    description: 'The meet place before the first agreed place change',
  })
  placeWas?: ChangePlaceWasDto;

  @ApiProperty({ type: [ChangePendingDto] })
  pending: ChangePendingDto[];
}
