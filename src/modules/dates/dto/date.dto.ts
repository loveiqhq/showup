import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

import { DateChatMessage } from '../entities/date-chat-message.entity';
import { DateEntity } from '../entities/date.entity';
import { DateStatus } from '../util/date-lifecycle';
import { CHAT_MESSAGE_MAX_LENGTH, ChatReason } from '../util/pre-date-chat';

/** Body for confirming a date happened, with a rating. */
export class ConfirmHappenedDto {
  @ApiProperty({
    description: 'Your rating of the date, from 1 to 5',
    example: 5,
  })
  @IsInt()
  @Min(1)
  @Max(5)
  rating: number;
}

/** Body for cancelling a date. */
export class CancelDateDto {
  @ApiPropertyOptional({
    description: 'Optional reason for cancelling (max 500 characters)',
  })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  reason?: string;
}

/** A date as seen by one of its participants. */
export class DateDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ description: 'The other person on this date' })
  otherUserId: string;

  @ApiProperty()
  scheduledAt: Date;

  @ApiProperty({
    nullable: true,
    description: 'The suggested venue, if one was chosen',
  })
  venueId: string | null;

  @ApiProperty({ enum: DateStatus })
  status: DateStatus;

  @ApiProperty({ description: 'Whether you have confirmed the date happened' })
  youConfirmedHappened: boolean;

  @ApiProperty({
    description: 'Whether the other person has confirmed the date happened',
  })
  otherConfirmedHappened: boolean;

  static from(d: DateEntity, viewerId: string): DateDto {
    const viewerIsA = d.userAId === viewerId;
    return {
      id: d.id,
      otherUserId: viewerIsA ? d.userBId : d.userAId,
      scheduledAt: d.scheduledAt,
      venueId: d.venueId,
      status: d.status,
      youConfirmedHappened: viewerIsA
        ? d.aConfirmedHappened
        : d.bConfirmedHappened,
      otherConfirmedHappened: viewerIsA
        ? d.bConfirmedHappened
        : d.aConfirmedHappened,
    };
  }
}

/** Body for sending a pre-date chat message (SHOWUP-115): a preset reason + optional free text. */
export class SendChatMessageDto {
  @ApiProperty({
    enum: ChatReason,
    description: 'The preset reason you picked to open this message',
    example: ChatReason.RunningLate,
  })
  @IsEnum(ChatReason)
  reason: ChatReason;

  @ApiPropertyOptional({
    description: `Optional free-text note attached to the reason (max ${CHAT_MESSAGE_MAX_LENGTH} characters)`,
  })
  @IsOptional()
  @IsString()
  @MaxLength(CHAT_MESSAGE_MAX_LENGTH)
  body?: string;
}

/** A single pre-date chat message as seen by a participant. */
export class ChatMessageDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ enum: ChatReason })
  reason: ChatReason;

  @ApiProperty({
    nullable: true,
    description: 'The optional free-text note, if the sender added one',
  })
  body: string | null;

  @ApiProperty({ description: 'Whether you are the sender of this message' })
  fromYou: boolean;

  @ApiProperty()
  createdAt: Date;

  static from(m: DateChatMessage, viewerId: string): ChatMessageDto {
    return {
      id: m.id,
      reason: m.reason,
      body: m.body,
      fromYou: m.senderId === viewerId,
      createdAt: m.createdAt,
    };
  }
}
