import { BadRequestException } from '@nestjs/common';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
  Min,
} from 'class-validator';

import { graphemeLength } from '../../check-ins/util/check-in-flow';

/** "The message is max 100 characters" (Search 01), counted as a person counts them. */
export const LIKE_MESSAGE_MAX = 100;

/** What a user sends to like someone. */
export class CreateLikeDto {
  @ApiProperty({ description: 'The id of the user being liked' })
  @IsUUID()
  targetUserId: string;

  @ApiPropertyOptional({
    description:
      'The optional message, at most 100 characters. Trimmed; empty or whitespace only is no ' +
      'message. Any sender may write one; only a Premium recipient can read it.',
  })
  @IsOptional()
  @IsString()
  // A payload guard only: the rule is 100 characters as a person counts them (`likeMessage`),
  // which an emoji family or an accented letter can be several code units of.
  @MaxLength(1000)
  message?: string;

  @ApiPropertyOptional({
    type: 'integer',
    description:
      "The profile's 1-based position in this Search visit, for like_sent's payload",
  })
  @IsOptional()
  @IsInt()
  @Min(1)
  position?: number;
}

/**
 * The message as stored: trimmed, and null when nothing is left ("an empty or whitespace-only text
 * sends as no message"). Over 100 characters is refused rather than cut -- the app already cuts a
 * paste at 100, so a longer one is a client bug, and silently storing a shortened message would
 * send words the person never saw.
 */
export function likeMessage(raw: string | null | undefined): string | null {
  const trimmed = (raw ?? '').trim();
  if (trimmed.length === 0) return null;
  if (graphemeLength(trimmed) > LIKE_MESSAGE_MAX) {
    throw new BadRequestException(
      `The message can be at most ${LIKE_MESSAGE_MAX} characters`,
    );
  }
  return trimmed;
}
