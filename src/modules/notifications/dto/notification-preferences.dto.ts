import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsBoolean, IsOptional } from 'class-validator';

import { NotificationPreference } from '../entities/notification-preference.entity';

/** Partial update of a user's notification preferences — only provided fields change. */
export class UpdateNotificationPreferencesDto {
  @ApiPropertyOptional({ description: 'Account & date essentials' })
  @IsOptional()
  @IsBoolean()
  essential?: boolean;

  @ApiPropertyOptional({ description: 'Reminders and re-engagement nudges' })
  @IsOptional()
  @IsBoolean()
  engagement?: boolean;

  @ApiPropertyOptional({
    description: 'Marketing (off unless explicitly enabled)',
  })
  @IsOptional()
  @IsBoolean()
  marketing?: boolean;
}

/** A user's current notification preferences. */
export class NotificationPreferencesDto {
  @ApiProperty()
  essential: boolean;

  @ApiProperty()
  engagement: boolean;

  @ApiProperty()
  marketing: boolean;

  static from(p: NotificationPreference): NotificationPreferencesDto {
    return {
      essential: p.essential,
      engagement: p.engagement,
      marketing: p.marketing,
    };
  }
}
