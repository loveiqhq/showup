import { ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsDateString,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';

import { HIDEABLE_FIELDS } from '../util/hidden-fields';

/** Fields a user may set on their own profile (all optional; used by POST and PATCH). */
export class UpsertProfileDto {
  @ApiPropertyOptional({ example: 'Leo' })
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  displayName?: string;

  @ApiPropertyOptional({
    example: '1998-04-23',
    description: 'YYYY-MM-DD; must be 18+',
  })
  @IsOptional()
  @IsDateString()
  dateOfBirth?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(40)
  gender?: string;

  @ApiPropertyOptional()
  @IsOptional()
  @IsString()
  @MaxLength(40)
  lookingFor?: string;

  @ApiPropertyOptional({
    description: 'Whether the profile is visible in discovery',
  })
  @IsOptional()
  @IsBoolean()
  isVisible?: boolean;

  /**
   * Typed as a plain string array rather than an OpenAPI enum on purpose. The vocabulary grows as
   * profile-field controls are built, and a generated client that models it as a closed enum can
   * fail to deserialise a response containing a value added after that client shipped. A string
   * array lets an older app ignore a field it does not know about, which is the correct behaviour
   * for a visibility flag.
   */
  @ApiPropertyOptional({
    type: [String],
    example: ['age'],
    description:
      'Registry field_ids the user does not want displayed on their profile. ' +
      'Replaces the whole set — send the full list, not a delta. ' +
      'Currently accepted: age. Does NOT affect discovery visibility or matching; ' +
      'use isVisible for that.',
  })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(HIDEABLE_FIELDS.length)
  @IsString({ each: true })
  hiddenFields?: string[];
}
