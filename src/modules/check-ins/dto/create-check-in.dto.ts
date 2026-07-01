import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsDateString,
  IsInt,
  IsNumber,
  IsOptional,
  Max,
  Min,
} from 'class-validator';

/** Fields a user provides to check in: the window they're available for a date. */
export class CreateCheckInDto {
  @ApiProperty({
    example: '2026-07-01T18:00:00.000Z',
    description: 'ISO 8601 start of the availability window',
  })
  @IsDateString()
  availabilityStart: string;

  @ApiProperty({
    example: '2026-07-01T22:00:00.000Z',
    description: 'ISO 8601 end of the availability window',
  })
  @IsDateString()
  availabilityEnd: string;

  @ApiPropertyOptional({
    example: 30,
    description:
      'Minutes the user needs to get ready before heading out (15–60). ' +
      'Defaults to 30 if omitted. Used with travel time to check a date is reachable.',
  })
  @IsOptional()
  @IsInt()
  preparationMinutes?: number;

  @ApiPropertyOptional({
    example: 52.5186,
    description:
      'Latitude of the area the user wants to meet in (−90..90). ' +
      'Must be sent together with longitude. Used only for proximity matching; never shown to others.',
  })
  @IsOptional()
  @IsNumber()
  @Min(-90)
  @Max(90)
  latitude?: number;

  @ApiPropertyOptional({
    example: 13.3761,
    description:
      'Longitude of the area the user wants to meet in (−180..180). Must be sent together with latitude.',
  })
  @IsOptional()
  @IsNumber()
  @Min(-180)
  @Max(180)
  longitude?: number;
}
