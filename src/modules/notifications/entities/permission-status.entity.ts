import { Column, Entity, PrimaryColumn, UpdateDateColumn } from 'typeorm';

import { PushPlatform } from './push-token.entity';

/** The device permissions the apps report -- `enums.json` §8, the MVP's asks. */
export enum DevicePermission {
  Notifications = 'notifications',
  Location = 'location',
  Camera = 'camera',
  Microphone = 'microphone',
}

/** What the phone says about one of them -- `enums.json` §24 `permission_status`. */
export enum DevicePermissionStatus {
  NotDetermined = 'not_determined',
  Granted = 'granted',
  Denied = 'denied',
  /** Parental controls or MDM. Treated as denied everywhere in the product (§24). */
  Restricted = 'restricted',
}

/**
 * The last status the user's phone reported for one permission (epic SHOWUP-151: "read the status
 * -> if it differs from the last value synced, update the server").
 *
 * READ, NEVER REMEMBERED -- ON THE PHONE. The OS owns the permission and it can change at any time
 * outside the app; the apps re-read it on every launch and foreground and send it here when it
 * moves. This row is that report, so the server can answer "can we notify this user" with all three
 * facts the epic names: the permission, the user's consent, and a registered push token.
 *
 * One row per user and permission: the newest report wins, from whichever phone sent it.
 */
@Entity('permission_statuses')
export class PermissionStatusEntry {
  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  @PrimaryColumn({
    type: 'enum',
    enum: DevicePermission,
    enumName: 'permission_statuses_permission_enum',
  })
  permission: DevicePermission;

  @Column({
    type: 'enum',
    enum: DevicePermissionStatus,
    enumName: 'permission_statuses_status_enum',
  })
  status: DevicePermissionStatus;

  @Column({
    type: 'enum',
    enum: PushPlatform,
    enumName: 'push_tokens_platform_enum',
  })
  platform: PushPlatform;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
