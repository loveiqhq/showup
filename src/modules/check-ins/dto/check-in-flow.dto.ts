import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  ArrayUnique,
  IsArray,
  IsDateString,
  IsIn,
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator';

import {
  CHECK_IN_MOTIVATIONS,
  CHECK_IN_VIBES,
  type CheckInMotivation,
  type CheckInVibe,
  FILTER_AGE_MAX,
  FILTER_AGE_MIN,
  FILTER_DISTANCE_MAX,
  FILTER_DISTANCE_MIN,
  MAX_SLOTS,
  MAX_VIBES,
  MEET_GENDERS,
  type MeetGender,
  SLOT_TIERS,
  type SlotTier,
} from '../util/check-in-flow';

/** A user's filter settings -- who they want to see. One store with Search (story 05). */
export class FilterSettingsDto {
  @ApiProperty({
    enum: MEET_GENDERS,
    enumName: 'MeetGender',
    isArray: true,
    minItems: 1,
    maxItems: 4,
    description:
      'Registry §10 meet_gender, the §1 gender set: who they want to meet. At least one. Class 2 -- ' +
      'never in a tracking payload.',
  })
  meetGender: MeetGender[];

  @ApiProperty({
    type: 'integer',
    minimum: FILTER_AGE_MIN,
    maximum: FILTER_AGE_MAX,
  })
  minAge: number;

  @ApiProperty({
    type: 'integer',
    nullable: true,
    minimum: FILTER_AGE_MIN,
    maximum: FILTER_AGE_MAX - 1,
    description: 'The oldest age to see, or null for 65+ -- no upper limit.',
  })
  maxAge: number | null;

  @ApiProperty({
    type: 'integer',
    minimum: FILTER_DISTANCE_MIN,
    maximum: FILTER_DISTANCE_MAX,
  })
  distanceKm: number;
}

/**
 * Everything the Check-In needs to know before its first screen: the window, where the gate
 * reopens, whether date search is unlocked, and what the steps pre-select.
 */
export class CheckInStateDto {
  @ApiProperty({
    type: 'string',
    format: 'uuid',
    nullable: true,
    description:
      'The current Check-In window -- `window_id` on every Check-In event. Null only before profile ' +
      'creation is complete; one opens then and lasts 48 hours.',
  })
  windowId: string | null;

  @ApiProperty({
    description:
      'Whether the intro CTA has ever been tapped. The gate reopens at Today once it has, at the ' +
      'intro before that.',
  })
  introPassed: boolean;

  @ApiProperty({
    description:
      'Whether the current window holds a committed Check-In. True unlocks date search; false is the ' +
      '"date search not available" Search tab.',
  })
  active: boolean;

  @ApiProperty({
    description: 'True until the user has committed a Check-In once.',
  })
  firstCheckIn: boolean;

  @ApiProperty({
    enum: CHECK_IN_MOTIVATIONS,
    enumName: 'CheckInMotivation',
    nullable: true,
    description:
      'Registry §30. The last motivation accepted with Continue, which pre-selects the step; null on ' +
      'a first Check-In.',
  })
  lastMotivation: CheckInMotivation | null;

  @ApiProperty({
    enum: CHECK_IN_VIBES,
    enumName: 'CheckInVibe',
    isArray: true,
    description:
      'Registry §31, in list order. The last vibes accepted with Continue; empty on a first Check-In.',
  })
  lastVibes: CheckInVibe[];

  @ApiProperty({
    type: FilterSettingsDto,
    description:
      'The filter settings the Filters step opens with: the saved ones, or -- for a user with none ' +
      'yet -- the first-Check-In pre-fill from the profile (story 05), any age, 10 km.',
  })
  filters: FilterSettingsDto;

  @ApiProperty({
    description:
      'Whether `filters` are saved settings rather than the profile pre-fill.',
  })
  filtersSaved: boolean;
}

/** The last accepted answer of a step, saved on its Continue (stories 03 and 04). */
export class SaveCheckInAnswersDto {
  @ApiPropertyOptional({
    enum: CHECK_IN_MOTIVATIONS,
    enumName: 'CheckInMotivation',
  })
  @IsOptional()
  @IsIn([...CHECK_IN_MOTIVATIONS])
  motivation?: CheckInMotivation;

  @ApiPropertyOptional({
    enum: CHECK_IN_VIBES,
    enumName: 'CheckInVibe',
    isArray: true,
    minItems: 1,
    maxItems: MAX_VIBES,
    description: 'One to five; stored de-duplicated and in list order.',
  })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(MAX_VIBES)
  @ArrayUnique()
  @IsIn([...CHECK_IN_VIBES], { each: true })
  vibes?: CheckInVibe[];
}

/** Saving the filter settings (story 05's Continue, and Search's filter sheet). */
export class SaveFilterSettingsDto {
  @ApiProperty({
    enum: MEET_GENDERS,
    enumName: 'MeetGender',
    isArray: true,
    minItems: 1,
    maxItems: 4,
  })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(4)
  @ArrayUnique()
  @IsIn([...MEET_GENDERS], { each: true })
  meetGender: MeetGender[];

  @ApiProperty({
    type: 'integer',
    minimum: FILTER_AGE_MIN,
    maximum: FILTER_AGE_MAX,
  })
  @IsInt()
  @Min(FILTER_AGE_MIN)
  @Max(FILTER_AGE_MAX)
  minAge: number;

  @ApiProperty({
    type: 'integer',
    nullable: true,
    minimum: FILTER_AGE_MIN,
    maximum: FILTER_AGE_MAX,
    description:
      'Null, or 65, for no upper limit -- stored as null either way.',
  })
  @IsOptional()
  @IsInt()
  @Min(FILTER_AGE_MIN)
  @Max(FILTER_AGE_MAX)
  maxAge: number | null;

  @ApiProperty({
    type: 'integer',
    minimum: FILTER_DISTANCE_MIN,
    maximum: FILTER_DISTANCE_MAX,
  })
  @IsInt()
  @Min(FILTER_DISTANCE_MIN)
  @Max(FILTER_DISTANCE_MAX)
  distanceKm: number;
}

/** One picked hour of the availability grid (story 02). */
export class CheckInSlotDto {
  @ApiProperty({
    example: '2026-10-13T17:00:00.000Z',
    description:
      'The instant the hour starts. The client works it out from the device clock and the 7am day ' +
      'boundary; the server drops an hour that has already started.',
  })
  @IsDateString()
  startsAt: string;

  @ApiProperty({
    enum: SLOT_TIERS,
    enumName: 'CheckInSlotTier',
    description: 'preferred: the 1st tap. available: the 2nd tap.',
  })
  @IsIn([...SLOT_TIERS])
  tier: SlotTier;
}

/** `Find my date` -- the whole Check-In, committed at once (story 06). */
export class CommitCheckInDto {
  @ApiProperty({
    type: 'string',
    format: 'uuid',
    description:
      'The window the flow ran in. The commit is idempotent per window: a retry never creates a ' +
      'second Check-In. If it has expired, the Check-In goes into the current window.',
  })
  @IsUUID()
  windowId: string;

  @ApiProperty({
    type: CheckInSlotDto,
    isArray: true,
    maxItems: MAX_SLOTS,
    description: 'Today and Tomorrow, every picked hour with its tier.',
  })
  @IsArray()
  @ArrayMaxSize(MAX_SLOTS)
  @ValidateNested({ each: true })
  @Type(() => CheckInSlotDto)
  slots: CheckInSlotDto[];

  @ApiProperty({ enum: CHECK_IN_MOTIVATIONS, enumName: 'CheckInMotivation' })
  @IsIn([...CHECK_IN_MOTIVATIONS])
  motivation: CheckInMotivation;

  @ApiProperty({
    enum: CHECK_IN_VIBES,
    enumName: 'CheckInVibe',
    isArray: true,
    minItems: 1,
    maxItems: MAX_VIBES,
  })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(MAX_VIBES)
  @ArrayUnique()
  @IsIn([...CHECK_IN_VIBES], { each: true })
  vibes: CheckInVibe[];

  // The 100-grapheme limit is checked in the service, because a validator counts UTF-16 units. This
  // is only a wall against an absurd body.
  @ApiPropertyOptional({
    type: 'string',
    nullable: true,
    maxLength: 1000,
    description:
      'The reason to be picked: at most 100 characters (graphemes), never an email, a phone number or ' +
      'a link. Trimmed; empty is stored as no uplift.',
  })
  @IsOptional()
  @IsString()
  @MaxLength(1000)
  uplift?: string | null;

  @ApiPropertyOptional({
    minimum: -90,
    maximum: 90,
    description:
      'Where the phone is, if location is allowed. Sent with longitude or not at all.',
  })
  @IsOptional()
  @IsNumber()
  @Min(-90)
  @Max(90)
  latitude?: number;

  @ApiPropertyOptional({
    minimum: -180,
    maximum: 180,
    description:
      'Where the phone is, if location is allowed. Sent with latitude or not at all.',
  })
  @IsOptional()
  @IsNumber()
  @Min(-180)
  @Max(180)
  longitude?: number;
}
