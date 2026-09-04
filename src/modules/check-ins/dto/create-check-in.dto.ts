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

  // `type: 'integer'` only. The description says 15-60, but the validators are @IsOptional and
  // @IsInt with no @Min or @Max -- so nothing enforces that range. Declaring minimum/maximum here
  // would put a constraint in the contract that the server does not apply, and a generated client
  // would then reject a value the backend accepts happily.
  @ApiPropertyOptional({
    type: 'integer',
    example: 30,
    description:
      'Minutes the user needs to get ready before heading out (15–60). ' +
      'Defaults to 30 if omitted. Used with travel time to check a date is reachable.',
  })
  @IsOptional()
  @IsInt()
  preparationMinutes?: number;

  @ApiProperty({
    example: 52.5186,
    description:
      "Latitude of the person's location at check-in, read from the device (−90..90). Required — a " +
      'location is mandatory to check in. Used only to find nearby people and to pick a nearby venue; ' +
      'never shown to others, who receive a rounded distance instead. This is not a meeting place the ' +
      'person chooses: the venue for a date is selected by the backend and recommended to both people.',
  })
  @IsNumber()
  @Min(-90)
  @Max(90)
  latitude: number;

  @ApiProperty({
    example: 13.3761,
    description:
      "Longitude of the person's location at check-in, read from the device (−180..180). Required, " +
      'together with latitude.',
  })
  @IsNumber()
  @Min(-180)
  @Max(180)
  longitude: number;
}
