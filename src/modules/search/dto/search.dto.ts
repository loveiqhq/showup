import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsEnum, IsUUID } from 'class-validator';

import {
  CHECK_IN_MOTIVATIONS,
  CHECK_IN_VIBES,
} from '../../check-ins/util/check-in-flow';
import { answerSet } from '../../profiles/dto/profile.dto';
import {
  ACTIVITIES,
  ALCOHOL,
  DATING_LANGUAGES,
  DIETS,
  EDUCATIONS,
  GENDERS,
  INTEREST_TAGS,
  ORIENTATIONS,
  POLITICS,
  RELIGIONS,
  RHYTHMS,
  SMOKING,
  SOCIAL_BATTERIES,
} from '../../profiles/util/profile-details';
import { PlanTier } from '../../users/entities/user.entity';
import { SearchPassType } from '../entities/search-pass.entity';

// Every answer below is a STRING read tolerantly (`answerSet`), never a response enum: an enum is a
// promise to every installed app that no other value will arrive, and both generated decoders
// throw on one that does. The app maps the values it knows to its own labels, as it does on the
// profile screens.
//
// "Every section is optional" (Search 01): an absent key is an absent section. A HIDDEN value is
// absent too -- left out here, never sent for the app to suppress, so "neither value is in the API
// response" holds on the wire.

export class SearchPhotoDto {
  @ApiProperty({ description: 'Where the app loads the photo from' })
  url: string;
}

export class SearchPromptDto {
  @ApiProperty({
    description: "The topic's id from the app's prompt list, never its text",
  })
  topicId: string;

  @ApiProperty()
  answer: string;
}

export class SearchMediaDto {
  @ApiProperty()
  url: string;

  @ApiProperty({ type: 'integer' })
  durationMs: number;

  @ApiProperty({
    description: "The media prompt's id (section 20), never its text",
  })
  promptId: string;
}

/** `{name}'s essentials`, in the ticket's order. A hidden or unanswered detail has no key. */
export class SearchEssentialsDto {
  @ApiPropertyOptional({ type: 'integer' })
  heightCm?: number;

  @ApiPropertyOptional({ type: String, description: answerSet(GENDERS) })
  gender?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(ORIENTATIONS) })
  orientation?: string;

  @ApiPropertyOptional({
    type: [String],
    description: `In list order. ${answerSet(DATING_LANGUAGES)}`,
  })
  datingLanguages?: string[];

  @ApiPropertyOptional({ type: String, description: answerSet(EDUCATIONS) })
  education?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(RELIGIONS) })
  religion?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(POLITICS) })
  politics?: string;
}

/** `{name}'s everyday` -- only the answered groups have a key. */
export class SearchHowILiveDto {
  @ApiPropertyOptional({ type: String, description: answerSet(ACTIVITIES) })
  activity?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(DIETS) })
  diet?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(ALCOHOL) })
  alcohol?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(SMOKING) })
  smoking?: string;

  @ApiPropertyOptional({ type: String, description: answerSet(RHYTHMS) })
  rhythm?: string;

  @ApiPropertyOptional({
    type: String,
    description: answerSet(SOCIAL_BATTERIES),
  })
  social?: string;
}

/**
 * One profile on the Search tab (Search 01) -- and, unchanged, per grid card and likes card
 * (epic: "the search profile payload, reused per grid card and likes card").
 */
export class SearchProfileDto {
  @ApiProperty()
  userId: string;

  @ApiProperty({ description: 'First name' })
  name: string;

  @ApiPropertyOptional({
    type: 'integer',
    description:
      'Absent when the person hides their age: the name then stands alone',
  })
  age?: number;

  @ApiProperty({
    type: 'integer',
    description:
      'Rounded: 0 means under one km ("< 1 km"), otherwise whole km ("~N km"). Never exact.',
  })
  distanceKm: number;

  @ApiPropertyOptional({
    description: 'The neighbourhood their phone named at Find my date',
  })
  district?: string;

  @ApiProperty({
    type: String,
    format: 'date-time',
    description: 'The earliest hour both Check-Ins hold that has not started',
  })
  meetAt: string;

  @ApiProperty({
    type: String,
    description:
      "today or tomorrow, by the Check-In's 7am day in the zone the viewer sent. " +
      'Read tolerantly: a newer server may add values.',
  })
  meetDay: string;

  @ApiProperty({
    type: 'integer',
    description: 'Dates both people confirmed. 0 is shown as New in the App.',
  })
  attendedCount: number;

  @ApiPropertyOptional({
    type: String,
    description: answerSet(CHECK_IN_MOTIVATIONS),
  })
  motivation?: string;

  @ApiProperty({
    type: [String],
    description: `Vibe today, in list order. ${answerSet(CHECK_IN_VIBES)}`,
  })
  vibes: string[];

  @ApiPropertyOptional({
    description: 'Their Check-In highlight, while that Check-In is active',
  })
  uplift?: string;

  @ApiProperty({
    type: [SearchPhotoDto],
    description: 'In profile order; a rejected photo is never sent',
  })
  photos: SearchPhotoDto[];

  @ApiProperty({
    type: [SearchPromptDto],
    description: 'In profile order, 1 to 3',
  })
  prompts: SearchPromptDto[];

  @ApiPropertyOptional({ type: SearchMediaDto })
  voice?: SearchMediaDto;

  @ApiPropertyOptional({ type: SearchMediaDto })
  video?: SearchMediaDto;

  @ApiProperty({ type: SearchEssentialsDto })
  essentials: SearchEssentialsDto;

  @ApiProperty({ type: SearchHowILiveDto })
  howILive: SearchHowILiveDto;

  @ApiProperty({
    type: [String],
    description: `What they are into, in list order. ${answerSet(INTEREST_TAGS)}`,
  })
  interests: string[];
}

/** The Search queue: how many profiles it holds, and the next few, in order. */
/**
 * What the viewer brings to the comparison: their own Check-In vibes and their interests. The phone
 * marks a profile's shared vibes and interests against these, read in the same request as the queue,
 * so a launch straight onto Search -- before any profile screen has loaded -- marks them right.
 */
export class SearchViewerDto {
  @ApiProperty({
    type: [String],
    description: `The viewer's vibes in their active Check-In. ${answerSet(CHECK_IN_VIBES)}`,
  })
  vibes: string[];

  @ApiProperty({
    type: [String],
    description: `The viewer's interests. ${answerSet(INTEREST_TAGS)}`,
  })
  interests: string[];
}

export class SearchQueueDto {
  @ApiProperty({
    type: 'integer',
    description:
      'Every profile in the queue right now (search_viewed results_count)',
  })
  resultsCount: number;

  @ApiProperty({
    type: [SearchProfileDto],
    description: 'The first profiles of the queue, next one first',
  })
  profiles: SearchProfileDto[];

  @ApiProperty({
    type: SearchViewerDto,
    description: 'The viewer, for marking what each profile shares with them',
  })
  viewer: SearchViewerDto;
}

/** `Not now` or `Not interested`. */
export class CreateSearchPassDto {
  @ApiProperty()
  @IsUUID()
  targetUserId: string;

  @ApiProperty({ enum: SearchPassType, enumName: 'SearchPassType' })
  @IsEnum(SearchPassType)
  passType: SearchPassType;
}

/** The open-likes count -- the Likes tab badge and the Search likes chip read the same number. */
export class LikesOpenCountDto {
  @ApiProperty({ type: 'integer' })
  count: number;
}

/** Dev menu only: switch this account's plan. 404 in production. */
export class SetPlanTierDto {
  @ApiProperty({ enum: PlanTier, enumName: 'PlanTier' })
  @IsEnum(PlanTier)
  planTier: PlanTier;
}
