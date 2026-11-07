import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsEnum,
  IsInt,
  IsNotEmpty,
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
    type: 'integer',
    minimum: 1,
    maximum: 5,
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
    type: String,
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

/**
 * Body for a chat message (Date 02): the text, and the chip's reason when one was selected. A message
 * typed with no chip carries none; `change_time` / `change_place` are refused, they open a picker.
 */
export class SendChatMessageDto {
  @ApiPropertyOptional({
    enum: ChatReason,
    enumName: 'ChatReason',
    description: "The selected chip's reason (§40); absent for a message typed with no chip",
    example: ChatReason.RunningLate,
  })
  @IsOptional()
  @IsEnum(ChatReason)
  reason?: ChatReason;

  @ApiProperty({
    description: `The message, trimmed: 1 to ${CHAT_MESSAGE_MAX_LENGTH} characters. Private to the two people.`,
    minLength: 1,
    maxLength: CHAT_MESSAGE_MAX_LENGTH,
  })
  @Transform(({ value }: { value: unknown }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @IsNotEmpty()
  @MaxLength(CHAT_MESSAGE_MAX_LENGTH)
  body: string;
}

/** A single pre-date chat message as seen by a participant. */
export class ChatMessageDto {
  @ApiProperty()
  id: string;

  /** Null for a message typed with no chip selected (Date 02). */
  @ApiProperty({ enum: ChatReason, enumName: 'ChatReason', nullable: true })
  reason: ChatReason | null;

  @ApiProperty({
    type: String,
    nullable: true,
    description: 'The text. Null only on a SHOWUP-115 message sent with a reason alone',
  })
  body: string | null;

  @ApiProperty({ description: 'Whether you are the sender of this message' })
  fromYou: boolean;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty({
    type: Boolean,
    nullable: true,
    description: "Your message: whether the other person has read the chat since. Theirs: null",
  })
  read: boolean | null;

  /** `otherReadAt`: when the OTHER person last read the chat, for the receipt on the viewer's own messages. */
  static from(m: DateChatMessage, viewerId: string, otherReadAt: Date | null): ChatMessageDto {
    const fromYou = m.senderId === viewerId;
    return {
      id: m.id,
      reason: m.reason,
      body: m.body,
      fromYou,
      createdAt: m.createdAt,
      read: fromYou ? otherReadAt !== null && otherReadAt.getTime() >= m.createdAt.getTime() : null,
    };
  }
}
