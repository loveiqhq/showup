import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { SavePermissionStatusesDto } from '../dto/permission-status.dto';
import {
  DevicePermission,
  DevicePermissionStatus,
  PermissionStatusEntry,
} from '../entities/permission-status.entity';

/**
 * The phone's permission statuses as the apps last reported them (epic SHOWUP-151, SHOWUP-163).
 * Written by the apps' shared permission reconciler; read by the push path, which must not send to
 * a phone whose notifications are switched off.
 */
@Injectable()
export class PermissionStatusesService {
  constructor(
    @InjectRepository(PermissionStatusEntry)
    private readonly entries: Repository<PermissionStatusEntry>,
  ) {}

  /** Saves what the phone just reported. The newest report wins, from whichever phone sent it. */
  async save(userId: string, dto: SavePermissionStatusesDto): Promise<void> {
    const seen = new Set<DevicePermission>();
    for (const item of dto.statuses) {
      if (seen.has(item.permission)) {
        throw new BadRequestException(
          `Each permission may appear once; ${item.permission} appears twice`,
        );
      }
      seen.add(item.permission);
    }
    await this.entries.upsert(
      dto.statuses.map((item) => ({
        userId,
        permission: item.permission,
        status: item.status,
        platform: dto.platform,
      })),
      ['userId', 'permission'],
    );
  }

  /** Everything the server has heard for this user, in a stable order. */
  async list(userId: string): Promise<PermissionStatusEntry[]> {
    return this.entries.find({
      where: { userId },
      order: { permission: 'ASC' },
    });
  }

  /**
   * The phone said notifications are off (denied, or restricted, which §24 treats as denied). Not
   * knowing is NOT off: a user whose app has not reported yet keeps getting pushes, as before.
   */
  async notificationsBlocked(userId: string): Promise<boolean> {
    const entry = await this.entries.findOne({
      where: { userId, permission: DevicePermission.Notifications },
    });
    return (
      entry?.status === DevicePermissionStatus.Denied ||
      entry?.status === DevicePermissionStatus.Restricted
    );
  }
}
