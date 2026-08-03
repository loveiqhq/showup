import { ApiProperty } from '@nestjs/swagger';
import { IsEnum, IsNotEmpty, IsString } from 'class-validator';

import { PushPlatform, PushToken } from '../entities/push-token.entity';

/** A device registering (or re-registering) to receive push notifications. */
export class RegisterPushTokenDto {
  @ApiProperty({ description: 'The device push token issued by FCM' })
  @IsString()
  @IsNotEmpty()
  token: string;

  @ApiProperty({ enum: PushPlatform })
  @IsEnum(PushPlatform)
  platform: PushPlatform;
}

/** A device removing its registration (e.g. on logout). */
export class DeletePushTokenDto {
  @ApiProperty({ description: 'The device push token to remove' })
  @IsString()
  @IsNotEmpty()
  token: string;
}

/** Safe view of a registered push token (never echoes the token value back). */
export class PushTokenDto {
  @ApiProperty()
  id: string;

  @ApiProperty({ enum: PushPlatform })
  platform: PushPlatform;

  @ApiProperty()
  createdAt: Date;

  static from(t: PushToken): PushTokenDto {
    return { id: t.id, platform: t.platform, createdAt: t.createdAt };
  }
}
