import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

import { DateEntity } from '../entities/date.entity';
import { DateStatus } from '../util/date-lifecycle';

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
