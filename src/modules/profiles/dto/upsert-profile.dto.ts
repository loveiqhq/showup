import { ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsDateString,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

import { FLOW_POSITIONS } from '../util/flow-position';
import { MAX_SUBMITTED_HIDDEN_FIELDS } from '../util/hidden-fields';
import {
  DATING_LANGUAGES,
  EDUCATIONS,
  GENDERS,
  HEIGHT_CM_MAX,
  HEIGHT_CM_MIN,
  MAX_SUBMITTED_DATING_LANGUAGES,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
} from '../util/profile-details';
import type {
  DatingLanguage,
  Education,
  Gender,
  Orientation,
  Politics,
  Religion,
} from '../util/profile-details';

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

  // THE ANSWERS (SHOWUP-167 to SHOWUP-173). Each is a §1 value, never a display label, and NULL is
  // treated as "not supplied" by the service: Skip saves nothing and "never deletes a value saved
  // earlier" (flow README rule 0), so no screen in the flow can clear an answer.

  @ApiPropertyOptional({ enum: GENDERS, enumName: 'Gender' })
  @IsOptional()
  @IsIn([...GENDERS])
  gender?: Gender;

  @ApiPropertyOptional({
    type: 'integer',
    minimum: HEIGHT_CM_MIN,
    maximum: HEIGHT_CM_MAX,
    description: 'Whole centimetres.',
  })
  @IsOptional()
  @IsInt()
  @Min(HEIGHT_CM_MIN)
  @Max(HEIGHT_CM_MAX)
  heightCm?: number;

  @ApiPropertyOptional({ enum: ORIENTATIONS, enumName: 'Orientation' })
  @IsOptional()
  @IsIn([...ORIENTATIONS])
  orientation?: Orientation;

  @ApiPropertyOptional({
    enum: DATING_LANGUAGES,
    enumName: 'DatingLanguage',
    isArray: true,
    // Worded to read true on the DatingLanguage schema as well: @nestjs/swagger copies the first
    // property description onto the enum component it creates, and `enumSchema` cannot override it.
    description:
      'The languages someone is happy to date in (registry §1 dating_language). Send at least ' +
      'one; stored de-duplicated and in list order, whatever order is sent.',
  })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(MAX_SUBMITTED_DATING_LANGUAGES)
  @IsIn([...DATING_LANGUAGES], { each: true })
  datingLanguages?: DatingLanguage[];

  @ApiPropertyOptional({ enum: EDUCATIONS, enumName: 'Education' })
  @IsOptional()
  @IsIn([...EDUCATIONS])
  education?: Education;

  @ApiPropertyOptional({ enum: RELIGIONS, enumName: 'Religion' })
  @IsOptional()
  @IsIn([...RELIGIONS])
  religion?: Religion;

  @ApiPropertyOptional({ enum: POLITICS, enumName: 'Politics' })
  @IsOptional()
  @IsIn([...POLITICS])
  politics?: Politics;

  @ApiPropertyOptional({
    enum: FLOW_POSITIONS,
    enumName: 'FlowPosition',
    // Lands on the FlowPosition schema too (see datingLanguages), and is true there.
    description:
      'The §2 step just completed or skipped. Only ever moves forward: an earlier step is accepted ' +
      'and ignored, so going back a screen never rewinds the account.',
  })
  @IsOptional()
  @IsIn([...FLOW_POSITIONS])
  flowPosition?: string;

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
      'Accepted: age, height, gender, orientation, dating_language, education, religion, ' +
      'politics. Does NOT affect discovery visibility or matching; ' +
      'use isVisible for that.',
  })
  @IsOptional()
  @IsArray()
  @ArrayMaxSize(MAX_SUBMITTED_HIDDEN_FIELDS)
  @IsString({ each: true })
  hiddenFields?: string[];
}
