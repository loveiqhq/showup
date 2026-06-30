import { ApiProperty } from '@nestjs/swagger';
import { IsDateString } from 'class-validator';

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
}
