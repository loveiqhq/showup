import { ApiProperty } from '@nestjs/swagger';

import { CheckIn } from '../entities/check-in.entity';
import { CheckInStatus, isActiveCheckIn } from '../util/check-in';

/** Safe view of a check-in returned to the owning user. */
export class CheckInDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ enum: CheckInStatus })
  status: CheckInStatus;

  @ApiProperty()
  availabilityStart: Date;

  @ApiProperty()
  availabilityEnd: Date;

  @ApiProperty({
    type: 'integer',
    description: 'Minutes the user needs to get ready before heading out',
  })
  preparationMinutes: number;

  @ApiProperty({ description: 'Whether this check-in is active right now' })
  active: boolean;

  @ApiProperty()
  createdAt: Date;

  static from(c: CheckIn, now: Date = new Date()): CheckInDto {
    return {
      id: c.id,
      status: c.status,
      availabilityStart: c.availabilityStart,
      availabilityEnd: c.availabilityEnd,
      preparationMinutes: c.preparationMinutes,
      active: isActiveCheckIn(c, now),
      createdAt: c.createdAt,
    };
  }
}
