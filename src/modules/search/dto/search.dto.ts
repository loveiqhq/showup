import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { ArrayMaxSize, IsArray, IsEnum, IsUUID } from 'class-validator';

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

// ── Search 02, the Premium grid (SHOWUP-185) ─────────────────────────────────────────────────────

/**
 * One card of the Premium grid: the Search 01 profile, whole, and whether this person liked the
 * viewer. The card draws the first photo, name, age, distance, meet time, motivation and
 * `attendedCount`; the rest is what the profile shows when the card is opened -- carried here so it
 * opens with no second request, and so its photos and recordings load only then.
 */
export class SearchGridCardDto {
  @ApiProperty({ type: SearchProfileDto })
  profile: SearchProfileDto;

  @ApiProperty({
    description:
      'This person has an open like to the viewer: the Liked you badge, and Like on their profile ' +
      'becomes Confirm date',
  })
  likedYou: boolean;
}

/** The Premium grid: the whole queue, in queue order, with only people still available. */
export class SearchGridDto {
  @ApiProperty({
    type: 'integer',
    description:
      'Every profile in the queue right now (search_viewed results_count)',
  })
  resultsCount: number;

  @ApiProperty({
    type: [SearchGridCardDto],
    description: 'In queue order, at most 60',
  })
  cards: SearchGridCardDto[];

  @ApiProperty({ type: SearchViewerDto })
  viewer: SearchViewerDto;
}

/** The availability re-check: every card on screen, in one call. */
export class SearchAvailabilityRequestDto {
  @ApiProperty({ type: [String], maxItems: 100 })
  @IsArray()
  @ArrayMaxSize(100)
  @IsUUID('all', { each: true })
  userIds: string[];
}

/** One person's status, as the re-check reads it. */
export class SearchAvailabilityDto {
  @ApiProperty()
  userId: string;

  @ApiProperty({
    type: String,
    description:
      'available, matched (they have a date with someone else) or no_overlap (no time left to ' +
      'meet). Read tolerantly: a newer server may add values, and an app treats one it does not ' +
      'know as no change.',
  })
  status: string;

  @ApiPropertyOptional({
    type: String,
    format: 'date-time',
    description:
      'The earliest hour both can still meet in -- only when available',
  })
  meetAt?: string;

  @ApiPropertyOptional({
    type: String,
    description: 'today or tomorrow for meetAt, in the zone the viewer sent',
  })
  meetDay?: string;
}

export class SearchAvailabilityListDto {
  @ApiProperty({
    type: [SearchAvailabilityDto],
    description: 'One per id asked for, in the order asked',
  })
  statuses: SearchAvailabilityDto[];
}

// ── Search 03, the Likes tab (SHOWUP-186) ────────────────────────────────────────────────────────

/**
 * One like the viewer received. OPEN: everything the card shows, and `profile` to open it with.
 * CLOSED (`closed` set): the name and the photo only -- the card has no actions, no distance and no
 * meet time, because there is no time left to meet.
 */
export class LikeReceivedDto {
  @ApiProperty()
  userId: string;

  @ApiProperty({ description: 'First name' })
  name: string;

  @ApiPropertyOptional({
    type: 'integer',
    description: 'Absent when the person hides their age',
  })
  age?: number;

  @ApiPropertyOptional({
    type: SearchPhotoDto,
    description: 'Their first photo',
  })
  photo?: SearchPhotoDto;

  @ApiPropertyOptional({
    type: String,
    description:
      'Absent on an open like. matched (Too late) or no_overlap (Out of time) on a closed one. ' +
      'Read tolerantly.',
  })
  closed?: string;

  @ApiPropertyOptional({
    description:
      'Their optional message, as written -- open likes only. A Premium reader only: this list is.',
  })
  message?: string;

  @ApiPropertyOptional({
    type: SearchProfileDto,
    description: 'Open likes only: the Search 01 profile the card opens',
  })
  profile?: SearchProfileDto;
}

/**
 * The Likes tab, read once per visit: open likes, earliest meet time first, then the closed ones not
 * yet seen on an earlier visit. Reading it marks those closed ones seen.
 */
export class LikesReceivedDto {
  @ApiProperty({
    type: 'integer',
    description:
      'Open likes on the list -- the badge, and likes_received_viewed open_count',
  })
  openCount: number;

  @ApiProperty({
    type: 'integer',
    description:
      'Closed cards on the list -- likes_received_viewed closed_count',
  })
  closedCount: number;

  @ApiProperty({ type: [LikeReceivedDto] })
  cards: LikeReceivedDto[];

  @ApiProperty({ type: SearchViewerDto })
  viewer: SearchViewerDto;
}

/** Dev menu only: switch this account's plan. 404 in production. */
export class SetPlanTierDto {
  @ApiProperty({ enum: PlanTier, enumName: 'PlanTier' })
  @IsEnum(PlanTier)
  planTier: PlanTier;
}
