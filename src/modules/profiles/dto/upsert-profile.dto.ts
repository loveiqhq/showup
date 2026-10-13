import { applyDecorators } from '@nestjs/common';
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
  ACTIVITIES,
  ALCOHOL,
  DATING_LANGUAGES,
  DIETS,
  EDUCATIONS,
  FUTURE_FAMILY,
  FUTURE_LIVING,
  FUTURE_PARTNERSHIP,
  GENDERS,
  HEIGHT_CM_MAX,
  HEIGHT_CM_MIN,
  INTEREST_TAGS,
  LIFE_FAMILY,
  LIFE_LIVING,
  LIFE_PARTNERSHIP,
  MAX_INTERESTS,
  MAX_SUBMITTED_DATING_LANGUAGES,
  MAX_SUBMITTED_LIFE_VALUES,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
  RHYTHMS,
  SMOKING,
  SOCIAL_BATTERIES,
} from '../util/profile-details';
import type {
  Activity,
  Alcohol,
  DatingLanguage,
  Diet,
  Education,
  FutureFamily,
  FutureLiving,
  FuturePartnership,
  Gender,
  InterestTag,
  LifeFamily,
  LifeLiving,
  LifePartnership,
  Orientation,
  Politics,
  Religion,
  Rhythm,
  Smoking,
  SocialBattery,
} from '../util/profile-details';

/**
 * A §4 life answer: a set that may be EMPTY. Continue on Life and Future "saves an unanswered
 * question as empty" (Profile 22), so `[]` is a real answer here, unlike dating languages and
 * interests. Exclusive combinations are refused by the service, which knows each question's set.
 */
function LifeAnswer(
  values: readonly string[],
  enumName: string,
  questionId: string,
): PropertyDecorator {
  return applyDecorators(
    ApiPropertyOptional({
      enum: values,
      enumName,
      isArray: true,
      description:
        `Registry §4 ${questionId}. May be empty (walked past without an answer); stored ` +
        'de-duplicated and in list order. An exclusive value combined with another is refused.',
    }),
    IsOptional(),
    IsArray(),
    ArrayMaxSize(MAX_SUBMITTED_LIFE_VALUES),
    IsIn([...values], { each: true }),
  );
}

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
    minItems: 1,
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

  // INTERESTS (SHOWUP-174). "How I live": one §1 value each. Sent only for the groups answered --
  // an unanswered group is not supplied, so it keeps whatever was saved before (rule 0).

  @ApiPropertyOptional({ enum: ACTIVITIES, enumName: 'Activity' })
  @IsOptional()
  @IsIn([...ACTIVITIES])
  activity?: Activity;

  @ApiPropertyOptional({ enum: DIETS, enumName: 'Diet' })
  @IsOptional()
  @IsIn([...DIETS])
  diet?: Diet;

  @ApiPropertyOptional({ enum: ALCOHOL, enumName: 'Alcohol' })
  @IsOptional()
  @IsIn([...ALCOHOL])
  alcohol?: Alcohol;

  @ApiPropertyOptional({ enum: SMOKING, enumName: 'Smoking' })
  @IsOptional()
  @IsIn([...SMOKING])
  smoking?: Smoking;

  @ApiPropertyOptional({ enum: RHYTHMS, enumName: 'Rhythm' })
  @IsOptional()
  @IsIn([...RHYTHMS])
  rhythm?: Rhythm;

  @ApiPropertyOptional({ enum: SOCIAL_BATTERIES, enumName: 'SocialBattery' })
  @IsOptional()
  @IsIn([...SOCIAL_BATTERIES])
  social?: SocialBattery;

  @ApiPropertyOptional({
    enum: INTEREST_TAGS,
    enumName: 'InterestTag',
    isArray: true,
    // Both bounds in the contract, so a generated client can know `[]` is refused (400), not only
    // the validator below.
    minItems: 1,
    maxItems: MAX_INTERESTS,
    // Lands on the InterestTag schema too (see datingLanguages), and is true there.
    description:
      'Registry §3 interest tags, the "What I am into" chips. Between one and ten; stored de-duplicated ' +
      'and in list order, whatever order is sent.',
  })
  @IsOptional()
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(MAX_INTERESTS)
  @IsIn([...INTEREST_TAGS], { each: true })
  interests?: InterestTag[];

  // LIFE (SHOWUP-175) and FUTURE (SHOWUP-176). Matching only: returned to the owner and to no one
  // else. Life's Continue sends all three questions, an unanswered one as []; Future's sends only
  // the questions answered. Either may leave out a question it cannot fully draw, and the server
  // then keeps it, as for any field not supplied.

  @LifeAnswer(LIFE_PARTNERSHIP, 'LifePartnership', 'partnership')
  partnership?: LifePartnership[];

  @LifeAnswer(LIFE_FAMILY, 'LifeFamily', 'family')
  family?: LifeFamily[];

  @LifeAnswer(LIFE_LIVING, 'LifeLiving', 'living')
  living?: LifeLiving[];

  @LifeAnswer(FUTURE_FAMILY, 'FutureFamily', 'family_ahead')
  familyAhead?: FutureFamily[];

  @LifeAnswer(FUTURE_PARTNERSHIP, 'FuturePartnership', 'partnership_ahead')
  partnershipAhead?: FuturePartnership[];

  @LifeAnswer(FUTURE_LIVING, 'FutureLiving', 'living_ahead')
  livingAhead?: FutureLiving[];

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
