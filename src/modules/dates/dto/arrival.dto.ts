import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsEnum,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsOptional,
  IsUUID,
  Max,
  Min,
} from 'class-validator';

import { ArrivalEntry } from '../util/arrival';

/**
 * Body for "I'm here" (Date 03, SHOWUP-195). The point is stored for the record and never sent
 * back, logged or put in an event.
 */
export class CreateArrivalCheckInDto {
  @ApiProperty({ minimum: -90, maximum: 90, example: 52.5186 })
  @IsLatitude()
  latitude: number;

  @ApiProperty({ minimum: -180, maximum: 180, example: 13.3761 })
  @IsLongitude()
  longitude: number;

  @ApiPropertyOptional({
    description: 'Horizontal accuracy of the fix in metres',
    minimum: 0,
    maximum: 100000,
  })
  @IsOptional()
  @IsNumber({ allowNaN: false, allowInfinity: false })
  @Min(0)
  @Max(100000)
  accuracyM?: number;

  @ApiProperty({
    enum: ArrivalEntry,
    enumName: 'ArrivalEntry',
    description: 'Where the person tapped "I\'m here"',
  })
  @IsEnum(ArrivalEntry)
  entry: ArrivalEntry;

  @ApiProperty({
    format: 'uuid',
    description:
      'The id the phone gave this tap, so a resend of the same tap is stored once',
  })
  @IsUUID('4')
  clientId: string;
}

/** A stored check-in as the person who made it sees it: the time and whether Close has happened. */
export class ArrivalCheckInDto {
  @ApiProperty({ format: 'date-time' })
  checkedInAt: string;

  @ApiProperty({ description: 'True once the check-in was committed (Close)' })
  committed: boolean;
}
