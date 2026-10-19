import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsEnum,
  ValidateNested,
} from 'class-validator';

import {
  DevicePermission,
  DevicePermissionStatus,
  PermissionStatusEntry,
} from '../entities/permission-status.entity';
import { PushPlatform } from '../entities/push-token.entity';

/** One permission and what the phone says about it. */
export class PermissionStatusItemDto {
  @ApiProperty({ enum: DevicePermission, enumName: 'DevicePermission' })
  @IsEnum(DevicePermission)
  permission: DevicePermission;

  @ApiProperty({
    enum: DevicePermissionStatus,
    enumName: 'DevicePermissionStatus',
    description:
      'enums.json §24. restricted is parental controls or MDM, treated as denied.',
  })
  @IsEnum(DevicePermissionStatus)
  status: DevicePermissionStatus;
}

/**
 * The phone's permissions, as just read -- sent when one moves, on a new sign-in, and when the app
 * answers its own permission sheet. Each permission at most once.
 */
export class SavePermissionStatusesDto {
  @ApiProperty({ enum: PushPlatform })
  @IsEnum(PushPlatform)
  platform: PushPlatform;

  @ApiProperty({
    type: PermissionStatusItemDto,
    isArray: true,
    minItems: 1,
    maxItems: 4,
  })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(4)
  @ValidateNested({ each: true })
  @Type(() => PermissionStatusItemDto)
  statuses: PermissionStatusItemDto[];
}

/** What the server last heard about one of the user's permissions. */
export class PermissionStatusDto {
  @ApiProperty({ enum: DevicePermission, enumName: 'DevicePermission' })
  permission: DevicePermission;

  @ApiProperty({
    enum: DevicePermissionStatus,
    enumName: 'DevicePermissionStatus',
  })
  status: DevicePermissionStatus;

  @ApiProperty({ enum: PushPlatform })
  platform: PushPlatform;

  @ApiProperty()
  updatedAt: Date;

  static from(e: PermissionStatusEntry): PermissionStatusDto {
    return {
      permission: e.permission,
      status: e.status,
      platform: e.platform,
      updatedAt: e.updatedAt,
    };
  }
}
