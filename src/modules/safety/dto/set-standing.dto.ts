import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsEnum, IsOptional, IsString, MaxLength } from 'class-validator';

import { ModerationStanding } from '../util/safety';

/** What a staff member sends to change a user/profile/photo's moderation standing. */
export class SetStandingDto {
  @ApiProperty({ enum: ModerationStanding })
  @IsEnum(ModerationStanding)
  standing: ModerationStanding;

  @ApiPropertyOptional({
    description: 'Why the standing was changed — kept in the audit trail.',
  })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  reason?: string;
}
