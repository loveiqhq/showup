import { ApiProperty } from '@nestjs/swagger';
import { IsString, MaxLength, MinLength } from 'class-validator';

import { ProfilePrompt } from '../entities/profile-prompt.entity';

/**
 * The product cap on an answer, in graphemes. Enforced in `PromptsService` AND in both clients
 * (SHOWUP-158), all three counting an emoji or a flag as one (SHOWUP-191 item 6).
 */
export const PROMPT_MAX_CHARS = 160;

/**
 * A wall against an absurd body, in UTF-16 units -- not the cap. A validator counts UTF-16 units,
 * so the 160-grapheme cap is checked in the service, as the Check-in uplift's is. 160 graphemes
 * of family emoji run past 1,000 units; nobody writes that, and the column is sized to this.
 */
export const PROMPT_MAX_UNITS = 1000;

/** How many prompts one account may carry. */
export const PROMPTS_MAX = 3;

/** One saved prompt, as the owner sees it. */
export class PromptDto {
  @ApiProperty()
  id: string;

  @ApiProperty({
    example: 'first_date',
    description:
      'Stable topic id from the app\u2019s topic list \u2014 never the question\u2019s display text.',
  })
  topicId: string;

  @ApiProperty({ example: 'Talk about anything real.' })
  answer: string;

  @ApiProperty({
    type: 'integer',
    description:
      'Reading order on the profile, from 0. A new prompt lands last.',
  })
  position: number;

  static from(prompt: ProfilePrompt): PromptDto {
    return {
      id: prompt.id,
      topicId: prompt.topicId,
      answer: prompt.answer,
      position: prompt.position,
    };
  }
}

/**
 * The body of `PUT /me/prompts/{topicId}`.
 *
 * Only the answer: the topic is the path, which is what makes the call idempotent and is why the
 * screen\u2019s Save can be pressed twice without writing two rows.
 *
 * `MinLength(1)` after trimming is enforced in the service rather than here, because class-validator
 * checks the raw string and " " has length 1. Whitespace-only counts as empty on both clients and
 * it has to mean the same thing at the API. So is the 160 cap, for the reason on [PROMPT_MAX_UNITS].
 */
export class UpsertPromptDto {
  @ApiProperty({
    maxLength: PROMPT_MAX_UNITS,
    example: 'Talk about anything real.',
    description: `At most ${PROMPT_MAX_CHARS} characters (graphemes, so an emoji is one), after trimming.`,
  })
  @IsString()
  @MinLength(1)
  @MaxLength(PROMPT_MAX_UNITS)
  answer: string;
}
